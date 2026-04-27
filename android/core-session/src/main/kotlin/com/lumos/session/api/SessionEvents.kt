
package com.lumos.session.api

/** Events emitted by SessionOrchestrator to feature/UI layer. */
sealed class SessionEvent {
    data class StateChanged(val newState: SessionState) : SessionEvent()
    data class NearbyUpdated(val peers: List<PeerRef>) : SessionEvent()
    data class InterestReceived(val from: PeerRef, val requestId: String) : SessionEvent()
    data class MatchEstablished(val peer: PeerRef, val sessionId: String) : SessionEvent()
    data class ChatMessageUpdated(val msg: ChatMessage) : SessionEvent()
    data class Toast(val text: String) : SessionEvent()
}

/** Commands invoked by feature layer into SessionOrchestrator. */
sealed class SessionCommand {
    data class StartDiscovering(val hint: TransportHint? = null) : SessionCommand()
    data object StopDiscovering : SessionCommand()

    data class SendInterest(val to: PeerRef) : SessionCommand()
    data class RespondInterest(val requestId: String, val accept: Boolean) : SessionCommand()

    data class SendChatText(val sessionId: String, val to: PeerRef, val text: String) : SessionCommand()
    data class EndSession(val sessionId: String) : SessionCommand()

    /** Used when QR bootstrap is scanned. */
    data class BootstrapFromQr(val token: String) : SessionCommand()
}
