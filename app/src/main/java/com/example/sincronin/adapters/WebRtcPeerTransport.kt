package com.example.sincronin.adapters

import android.content.Context
import com.example.sincronin.ports.PeerEvent
import com.example.sincronin.ports.PeerTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer

/**
 * Canal de datos sobre WebRTC, sin audio ni video: aquí solo viaja texto.
 *
 * El servidor de señalización no entiende de WebRTC, así que todo lo que
 * produce este adaptador (SDP y candidatos ICE) sale como JSON por
 * [onSignal] y vuelve a entrar por [answer] / [accept] / [addCandidate].
 * Los formatos son los del protocolo: `sdp` y `candidate` van como objetos.
 */
class WebRtcPeerTransport(
    context: Context,
    private val onSignal: (json: String) -> Unit
) : PeerTransport {

    private val _events = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 8)
    override val events: Flow<PeerEvent> = _events

    private val factory: PeerConnectionFactory

    private var connection: PeerConnection? = null
    private var channel: DataChannel? = null

    init {
        // Se puede llamar más de una vez, pero la segunda lanza.
        runCatching {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .createInitializationOptions()
            )
        }
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    override fun propose() {
        val pc = connect()
        // El anfitrión abre el canal antes de ofrecer, así el invitado lo
        // recibe ya puesto cuando acepte.
        val data = pc.createDataChannel("sincronin", DataChannel.Init())
        channel = data
        watch(data)

        pc.createOffer(
            OnSdp { offer ->
                pc.setLocalDescription(OnSdp(), offer)
                onSignal(session(OFFER, offer.description))
            },
            MediaConstraints()
        )
    }

    override fun answer(json: String) {
        val pc = connect()
        pc.setRemoteDescription(
            OnSdp {
                pc.createAnswer(
                    OnSdp { answer ->
                        pc.setLocalDescription(OnSdp(), answer)
                        onSignal(session(ANSWER, answer.description))
                    },
                    MediaConstraints()
                )
            },
            SessionDescription(SessionDescription.Type.OFFER, sdpOf(json))
        )
    }

    override fun accept(json: String) {
        connect().setRemoteDescription(
            OnSdp(),
            SessionDescription(SessionDescription.Type.ANSWER, sdpOf(json))
        )
    }

    override fun addCandidate(json: String) {
        val pc = connection ?: return
        val candidate = runCatching { JSONObject(json).getJSONObject("candidate") }.getOrNull() ?: return
        pc.addIceCandidate(
            IceCandidate(
                candidate.optString("sdpMid"),
                candidate.optInt("sdpMLineIndex"),
                candidate.optString("candidate")
            )
        )
    }

    override fun restart() {
        val pc = connection ?: return
        // restartIce() marca la próxima oferta como reinicio de ICE; así se
        // conservan los candidatos y el canal no se recrea.
        pc.restartIce()
        pc.createOffer(
            OnSdp { offer ->
                pc.setLocalDescription(OnSdp(), offer)
                onSignal(session(OFFER, offer.description))
            },
            MediaConstraints()
        )
    }

    override fun send(text: String) {
        val buffer = DataChannel.Buffer(ByteBuffer.wrap(text.toByteArray()), false)
        channel?.send(buffer)
    }

    override fun close() {
        channel?.runCatching { unregisterObserver() }
        channel?.close()
        channel = null
        connection?.close()
        connection?.dispose()
        connection = null
        factory.dispose()
    }

    private fun connect(): PeerConnection = connection ?: factory.createPeerConnection(
        PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder(STUN).createIceServer())
        ),
        observer
    )!!.also { connection = it }

    private fun watch(data: DataChannel) {
        data.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            override fun onStateChange() {
                when (data.state()) {
                    DataChannel.State.OPEN -> _events.tryEmit(PeerEvent.Connected)
                    DataChannel.State.CLOSED -> _events.tryEmit(PeerEvent.Disconnected)
                    else -> Unit
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                _events.tryEmit(PeerEvent.Message(String(bytes)))
            }
        })
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            when (state) {
                PeerConnection.IceConnectionState.FAILED,
                PeerConnection.IceConnectionState.DISCONNECTED -> _events.tryEmit(PeerEvent.Disconnected)
                else -> Unit
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit

        override fun onIceCandidate(candidate: IceCandidate) {
            val payload = JSONObject()
                .put("candidate", candidate.sdp)
                .put("sdpMid", candidate.sdpMid)
                .put("sdpMLineIndex", candidate.sdpMLineIndex)
            onSignal(JSONObject().put("type", ICE).put("candidate", payload).toString())
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit

        override fun onAddStream(stream: MediaStream) = Unit

        override fun onRemoveStream(stream: MediaStream) = Unit

        override fun onDataChannel(dataChannel: DataChannel) {
            channel = dataChannel
            watch(dataChannel)
        }

        override fun onRenegotiationNeeded() = Unit
    }

    /** Lo mínimo del [SdpObserver]: lo que importa es el SDP creado. */
    private inner class OnSdp(
        private val onCreated: (SessionDescription) -> Unit = {}
    ) : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) = onCreated(sdp)

        override fun onSetSuccess() = Unit

        override fun onCreateFailure(error: String?) {
            _events.tryEmit(PeerEvent.Failed)
        }

        override fun onSetFailure(error: String?) {
            _events.tryEmit(PeerEvent.Failed)
        }
    }

    /**
     * El cuerpo va como en el protocolo: el SDP es un objeto
     * `{"type":"offer","sdp":"v=0..."}`, no el texto suelto. Mandarlo
     * plano hacía que el otro lado (y este mismo al leerlo) no supiera
     * sacar el SDP.
     */
    private fun session(type: String, description: String): String = JSONObject()
        .put("type", type)
        .put("sdp", JSONObject().put("type", type).put("sdp", description))
        .toString()

    private fun sdpOf(json: String): String =
        runCatching { JSONObject(json).getJSONObject("sdp").getString("sdp") }.getOrDefault("")

    private companion object {
        const val STUN = "stun:stun.l.google.com:19302"
        const val OFFER = "offer"
        const val ANSWER = "answer"
        const val ICE = "ice"
    }
}
