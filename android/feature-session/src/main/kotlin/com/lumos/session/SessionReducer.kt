package com.lumos.session
sealed class SessionState { object Idle:SessionState(); object Advertising:SessionState(); object Discovering:SessionState(); data class Connecting(val peer:PeerRef):SessionState(); data class Active(val peer:PeerRef,val sessionId:String,val transport:AdapterKind):SessionState(); data class Migrating(val peer:PeerRef,val sessionId:String,val from:AdapterKind,val to:AdapterKind):SessionState(); data class Failed(val code:String):SessionState() }
sealed class SessionAction { object StartAdvertise:SessionAction(); object StartDiscover:SessionAction(); data class Connect(val peer:PeerRef):SessionAction(); data class Negotiated(val peer:PeerRef,val sessionId:String,val transport:AdapterKind):SessionAction(); data class Migrate(val to:AdapterKind):SessionAction(); data class Fatal(val code:String):SessionAction(); object Reset:SessionAction() }
class SessionReducer {
 fun reduce(state:SessionState, action:SessionAction): SessionState = when (state) {
  SessionState.Idle -> when (action) { SessionAction.StartAdvertise -> SessionState.Advertising; SessionAction.StartDiscover -> SessionState.Discovering; is SessionAction.Connect -> SessionState.Connecting(action.peer); is SessionAction.Fatal -> SessionState.Failed(action.code); else -> state }
  SessionState.Advertising, SessionState.Discovering -> when (action) { is SessionAction.Connect -> SessionState.Connecting(action.peer); is SessionAction.Fatal -> SessionState.Failed(action.code); SessionAction.Reset -> SessionState.Idle; else -> state }
  is SessionState.Connecting -> when (action) { is SessionAction.Negotiated -> SessionState.Active(action.peer, action.sessionId, action.transport); is SessionAction.Fatal -> SessionState.Failed(action.code); SessionAction.Reset -> SessionState.Idle; else -> state }
  is SessionState.Active -> when (action) { is SessionAction.Migrate -> SessionState.Migrating(state.peer, state.sessionId, state.transport, action.to); is SessionAction.Fatal -> SessionState.Failed(action.code); SessionAction.Reset -> SessionState.Idle; else -> state }
  is SessionState.Migrating -> when (action) { is SessionAction.Negotiated -> SessionState.Active(action.peer, action.sessionId, action.transport); is SessionAction.Fatal -> SessionState.Failed(action.code); SessionAction.Reset -> SessionState.Idle; else -> state }
  is SessionState.Failed -> when (action) { SessionAction.Reset -> SessionState.Idle; else -> state }
 }
}
