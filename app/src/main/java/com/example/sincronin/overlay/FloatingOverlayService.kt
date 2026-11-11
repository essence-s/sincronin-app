package com.example.sincronin.overlay

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntRect
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.sincronin.MainActivity
import com.example.sincronin.R
import com.example.sincronin.presentation.fabmenu.FabMenuViewModel
import com.example.sincronin.presentation.overlay.FloatingFabContent
import com.example.sincronin.presentation.overlay.FloatingOverlayContent
import com.example.sincronin.ui.theme.SincroninTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Servicio en primer plano que dibuja el botón flotante en una ventana del
 * sistema ([WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]), de modo que
 * se ve por encima de cualquier otra app (WhatsApp, cámara, un video, etc.).
 *
 * El botón siempre mide lo mismo (56 dp) y vive en la misma ventana, así que
 * no se recrea ni parpadea. Lo que cambia es su opacidad y cuánto se asoma:
 *
 * 1. **En reposo**: atenuado, discreto sobre un video.
 * 2. **Despierto**: opaco. Tocar el botón abre el menú, esté atenuado o no:
 *    la atenuación es solo visual y nunca cuesta un toque de más.
 * 3. **Lengüeta**: si lo sueltas cerca de un lateral, la ventana se corre hasta
 *    el borde y el contenido dibuja el círculo medio fuera, recortado por la
 *    ventana: solo se ve la mitad. Un toque la abre igual que el botón entero.
 *    La ventana se excluye del gesto de retroceso del sistema, así que arrastrar
 *    o tocar el botón pegado al borde no te manda atrás.
 *
 * Al pulsarlo con el botón despierto se abre el menú en **otra ventana** que
 * ocupa el área segura completa, para oscurecer el fondo y capturar el toque.
 * Esa ventana se quita cuando la animación de cierre ya devolvió el panel a la
 * posición del botón, así que el botón de abajo nunca desaparece.
 *
 * El panel recibe el rectángulo exacto del FAB ([fabRect]) en píxeles del área
 * segura: crece desde ahí en lugar de aparecer siempre en una esquina.
 */
class FloatingOverlayService :
    Service(),
    LifecycleOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val windowManager: WindowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private lateinit var viewModel: FabMenuViewModel

    /** Ventana del botón: existe desde que arranca el servicio y no se redimensiona. */
    private lateinit var fabView: ComposeView
    private var fabAttached = false

    /** Ventana del panel: solo existe mientras el menú está abierto cerrándose. */
    private var panelView: ComposeView? = null

    /** Rectángulo del FAB en px del área segura; el panel se despliega desde ahí. */
    private val fabRect = mutableStateOf(IntRect.Zero)

    /** El botón está atenuado hasta que se toca. */
    private val fabIdle = mutableStateOf(true)

    /**
     * Lateral al que está pegado asomando solo la mitad: -1 izquierda,
     * 1 derecha, 0 suelto (botón entero dentro de la pantalla).
     */
    private val peekSide = mutableStateOf(0)

    /**
     * Cuánto se ha sacado la lengüeta con el dedo, en px (0..[halfWindowPx]).
     *
     * Mientras la lengüeta está puesta, el arrastre no mueve la ventana: saca el
     * círculo del borde. Así el recorte de la ventana cae siempre justo en el
     * borde de la pantalla y el botón no se ve cortado, y el círculo queda
     * pegado al dedo hasta que está entero; a partir de ahí sí se mueve la
     * ventana.
     */
    private val peekPull = mutableStateOf(0f)

    /** Media ventana en px: lo que hay que sacar la lengüeta para verla entera. */
    private val halfWindowPx: Float get() = dp(FAB_WINDOW_SIZE_DP) / 2f

    /**
     * Lateral que tenía la lengüeta cuando se abrió el menú. Al cerrarlo el
     * botón vuelve a esconderse ahí en vez de quedarse entero junto al borde.
     */
    private var restorePeekSide = 0

    /** Esquina superior izquierda de la ventana del botón, en px del área segura. */
    private var fabX = 0f
    private var fabY = 0f

    private var idleJob: Job? = null
    private var snapAnimator: ValueAnimator? = null

    /** Apertura del menú que espera a que la lengüeta vuelva dentro. */
    private val pendingOpen = Runnable {
        if (!viewModel.uiState.value.isExpanded) viewModel.onFabClick()
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val fabLayoutParams: WindowManager.LayoutParams by lazy {
        overlayParams(dp(FAB_WINDOW_SIZE_DP), dp(FAB_WINDOW_SIZE_DP)).apply {
            x = fabX.roundToInt()
            y = fabY.roundToInt()
        }
    }

    override fun onCreate() {
        super.onCreate()

        isRunning = true
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        startAsForeground()
        createFabWindow()
        observeExpansion()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Al girar la pantalla el área útil cambia: devolvemos el botón dentro o
        // recalculamos la lengüeta con el ancho nuevo.
        if (!fabAttached) return
        if (peekSide.value != 0) {
            fabX = edgeX(peekSide.value)
        } else {
            clampFabPosition()
        }
        applyFabPosition()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        snapAnimator?.cancel()
        if (::fabView.isInitialized) fabView.removeCallbacks(pendingOpen)
        detachPanel()
        if (fabAttached) {
            runCatching { windowManager.removeView(fabView) }
            fabAttached = false
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        isRunning = false
        super.onDestroy()
    }

    private fun createFabWindow() {
        viewModel = FabMenuViewModel()

        initFabPosition()
        fabRect.value = fabRectInSafeArea()

        fabView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setContent {
                SincroninTheme {
                    FloatingFabContent(
                        idle = fabIdle.value,
                        peekSide = peekSide.value,
                        peekPull = (peekPull.value / halfWindowPx).coerceIn(0f, 1f),
                        // Se actúa en el siguiente ciclo: añadir una ventana
                        // dentro del mismo evento de toque puede cortarlo.
                        onFabClick = { post { onFabTapped() } },
                        onFabDragStart = { wakeFab() },
                        onFabDrag = ::onFabDrag,
                        onFabDragEnd = ::onFabDragEnd
                    )
                }
            }
        }

        val added = runCatching {
            windowManager.addView(fabView, fabLayoutParams)
        }.isSuccess

        if (added) {
            fabAttached = true
            excludeFabFromBackGesture()
        } else {
            stopSelf()
        }
    }

    /**
     * Saca la huella del botón del gesto de retroceso del sistema.
     *
     * Con navegación por gestos, deslizar desde el borde izquierdo o derecho
     * navega atrás. Como el botón (y sobre todo la lengüeta) vive justo en ese
     * borde, el sistema se quedaba con el gesto en vez de dejarlo al botón.
     * [android.view.View.setSystemGestureExclusionRects] marca la ventana como
     * zona que no debe ser interceptada: Android respeta hasta 200 dp por
     * lateral, así que una ventana de [FAB_WINDOW_SIZE_DP] entra de sobra.
     *
     * El rectángulo va en coordenadas de la vista, no de la pantalla, y la
     * vista mide siempre lo mismo: al mover el botón la ventana se mueve con
     * él, así que basta con declararlo una vez. Por debajo de Android 10 no
     * hay gesto de retroceso desde los bordes y no hace falta nada.
     */
    private fun excludeFabFromBackGesture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val size = dp(FAB_WINDOW_SIZE_DP)
        runCatching {
            fabView.systemGestureExclusionRects = listOf(Rect(0, 0, size, size))
        }
    }

    /**
     * Un toque en el botón abre el menú, también estando atenuado.
     *
     * Si estaba como lengüeta, primero se devuelve el círculo al centro de su
     * ventana y el menú se abre cuando ya llegó. Abrirlo en el mismo instante
     * hacía que el panel naciera de la mitad que asomaba (pegada al borde) en
     * vez de nacer del botón que ya se ve dentro.
     */
    private fun onFabTapped() {
        if (peekSide.value != 0) {
            // Se recuerda el lado para devolver la lengüeta al cerrar el menú.
            restorePeekSide = peekSide.value
            peekPull.value = 0f
            peekSide.value = 0
            wakeFab()
            // Refresca fabRect: el panel se ancla a esta posición, no a la de
            // la lengüeta, que estaba desplazada media ventana.
            applyFabPosition()
            fabView.removeCallbacks(pendingOpen)
            fabView.postDelayed(pendingOpen, SNAP_ANIMATION_MILLIS + 20L)
            return
        }
        wakeFab()
        viewModel.onFabClick()
    }

    private fun observeExpansion() {
        serviceScope.launch {
            viewModel.uiState
                .map { it.isExpanded }
                .distinctUntilChanged()
                .collect { expanded ->
                    // Al cerrar no se quita aquí: el panel avisa cuando la
                    // animación ya terminó (detachPanel).
                    if (expanded) attachPanel()
                }
        }
    }

    private fun attachPanel() {
        if (panelView != null || !fabAttached) return

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setContent {
                SincroninTheme {
                    val state by viewModel.uiState.collectAsState()
                    FloatingOverlayContent(
                        state = state,
                        fabRectPx = fabRect.value,
                        onClose = viewModel::onDismiss,
                        onCollapseFinished = { fabView.post { detachPanel() } }
                    )
                }
            }
        }

        val added = runCatching {
            windowManager.addView(
                view,
                overlayParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
            )
        }.isSuccess

        if (added) {
            panelView = view
        }
    }

    private fun detachPanel() {
        val view = panelView ?: return
        panelView = null
        runCatching { windowManager.removeView(view) }
        // El menú acaba de cerrarse: el botón sigue despierto unos segundos.
        wakeFab()
        // Y si el menú se abrió desde la lengüeta, vuelve a media pantalla
        // fuera: se cierra sin dejar el botón entero pegado al lateral.
        restorePeek()
    }

    /** Devuelve el botón a la lengüeta desde la que se abrió el menú. */
    private fun restorePeek() {
        val side = restorePeekSide
        restorePeekSide = 0
        if (side == 0) return
        peekPull.value = 0f
        peekSide.value = side
        // La ventana ya está en el borde; lo que se mueve es el círculo dentro
        // de ella, así que basta con resincronizar el rectángulo del panel.
        applyFabPosition()
    }

    /** Saca el botón a pantalla completa y reinicia el temporizador de reposo. */
    private fun wakeFab() {
        fabIdle.value = false
        idleJob?.cancel()
        idleJob = serviceScope.launch {
            delay(IDLE_TIMEOUT_MILLIS)
            if (!viewModel.uiState.value.isExpanded) fabIdle.value = true
        }
    }

    /** Mueve la ventana del botón con el dedo, sin salirse del área segura. */
    private fun onFabDrag(offset: Offset) {
        if (!fabAttached) return

        snapAnimator?.cancel()
        // Con la lengüeta puesta el primer movimiento no mueve la ventana: saca
        // el círculo del borde. Solo el sobrante (y cualquier movimiento una
        // vez que la lengüeta está fuera) desplaza la ventana.
        val side = peekSide.value
        var movementX = offset.x
        if (side != 0) {
            val inward = offset.x * -side
            val pending = halfWindowPx - peekPull.value
            val consumed = inward.coerceIn(0f, pending)
            peekPull.value = (peekPull.value + consumed).coerceIn(0f, halfWindowPx)
            movementX = offset.x + side * consumed
            if (peekPull.value >= halfWindowPx) peekSide.value = 0
        }
        // Movido a mano: ya no hay lengüeta que recuperar al cerrar.
        restorePeekSide = 0

        val size = dp(FAB_WINDOW_SIZE_DP)
        val area = safeArea()
        val maxX = (area.width().toFloat() - size).coerceAtLeast(0f)
        val maxY = (area.height().toFloat() - size).coerceAtLeast(0f)
        fabX = (fabX + movementX).coerceIn(0f, maxX)
        fabY = (fabY + offset.y).coerceIn(0f, maxY)
        applyFabPosition()
    }

    /** Al soltar: si quedó muy cerca de un lateral, se pega y deja una lengüeta. */
    private fun onFabDragEnd() {
        if (!fabAttached) return

        // Si se soltó a medio sacar, la lengüeta vuelve a su sitio de golpe.
        peekPull.value = 0f
        val maxX = maxFabX()
        val threshold = dp(SNAP_THRESHOLD_DP).toFloat()
        when {
            fabX <= threshold -> snapToEdge(side = -1)
            fabX >= maxX - threshold -> snapToEdge(side = 1)
            else -> {
                peekSide.value = 0
                applyFabPosition()
            }
        }
        wakeFab()
    }

    /**
     * Pega la lengüeta a un lateral. La ventana se queda **dentro** de la
     * pantalla (el sistema la recorta si se manda a coordenadas negativas sin
     * FLAG_LAYOUT_NO_LIMITS); es el contenido el que dibuja el círculo medio
     * fuera y la propia ventana lo recorta.
     */
    private fun snapToEdge(side: Int) {
        peekPull.value = 0f
        peekSide.value = side
        animateFabX(edgeX(side))
    }

    /** Posición de la ventana pegada al borde: totalmente dentro de la pantalla. */
    private fun edgeX(side: Int): Float = if (side < 0) 0f else maxFabX()

    private fun animateFabX(target: Float) {
        snapAnimator?.cancel()
        if (fabX == target) {
            applyFabPosition()
            return
        }
        snapAnimator = ValueAnimator.ofFloat(fabX, target).apply {
            duration = SNAP_ANIMATION_MILLIS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                fabX = it.animatedValue as Float
                applyFabPosition()
            }
            start()
        }
    }

    private fun maxFabX(): Float =
        (safeArea().width().toFloat() - dp(FAB_WINDOW_SIZE_DP)).coerceAtLeast(0f)

    private fun initFabPosition() {
        val area = safeArea()
        val size = dp(FAB_WINDOW_SIZE_DP)
        fabX = (area.width().toFloat() - size).coerceAtLeast(0f)
        fabY = (area.height().toFloat() - size).coerceAtLeast(0f)
    }

    private fun clampFabPosition() {
        val area = safeArea()
        val size = dp(FAB_WINDOW_SIZE_DP)
        fabX = fabX.coerceIn(0f, (area.width().toFloat() - size).coerceAtLeast(0f))
        fabY = fabY.coerceIn(0f, (area.height().toFloat() - size).coerceAtLeast(0f))
    }

    private fun applyFabPosition() {
        fabLayoutParams.x = fabX.roundToInt()
        fabLayoutParams.y = fabY.roundToInt()
        if (fabAttached) {
            runCatching { windowManager.updateViewLayout(fabView, fabLayoutParams) }
        }
        fabRect.value = fabRectInSafeArea()
    }

    /**
     * Dónde se ve el círculo dentro de la ventana pequeña, en px del área
     * segura. Cuando es lengüeta el contenido lo dibuja desplazado media
     * ventana, así que el rectángulo se desplaza igual: el panel debe nacer del
     * círculo que se ve, no de la posición que tendría estando entero.
     */
    private fun fabRectInSafeArea(): IntRect {
        val size = dp(FAB_SIZE_DP)
        val halfWindow = dp(FAB_WINDOW_SIZE_DP) / 2
        val shift = when (peekSide.value) {
            -1 -> -halfWindow
            1 -> halfWindow
            else -> 0
        }
        val left = fabX.roundToInt() + dp(FAB_INSET_DP) + shift
        val top = fabY.roundToInt() + dp(FAB_INSET_DP)
        return IntRect(left, top, left + size, top + size)
    }

    private fun overlayParams(width: Int, height: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width,
            height,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Toda ventana vive dentro del área segura: sus coordenadas y
                // las del FAB comparten origen y el panel encaja con el botón.
                setFitInsetsTypes(WindowInsets.Type.systemBars())
            }
        }

    /** Área donde la ventana puede moverse: la pantalla menos las barras. */
    private fun safeArea(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val area = runCatching {
                val metrics = windowManager.currentWindowMetrics
                val insets = metrics.windowInsets
                    .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                val bounds = metrics.bounds
                Rect(
                    bounds.left,
                    bounds.top,
                    bounds.right - bounds.left - insets.left - insets.right,
                    bounds.bottom - bounds.top - insets.top - insets.bottom
                )
            }.getOrNull()
            if (area != null) return area
        }
        val displayMetrics = resources.displayMetrics
        return Rect(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    private fun startAsForeground() {
        createNotificationChannel()

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val CHANNEL_ID = "floating_overlay"
        private const val NOTIFICATION_ID = 42

        /** Huella de la ventana del botón: 56 dp del FAB + 8 dp de sombra. */
        private const val FAB_WINDOW_SIZE_DP = 72
        private const val FAB_SIZE_DP = 56
        private const val FAB_INSET_DP = 8

        /** Distancia al borde a partir de la cual se pega al lateral. */
        private const val SNAP_THRESHOLD_DP = 28

        /** Tiempo sin usarse antes de volver a atenuarse. */
        private const val IDLE_TIMEOUT_MILLIS = 1_500L

        private const val SNAP_ANIMATION_MILLIS = 180L

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
