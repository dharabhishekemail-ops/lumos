package com.lumos.session
import com.lumos.protocol.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
sealed class OrchestratorCommand { data class StartAdapter(val kind:AdapterKind):OrchestratorCommand(); data class StopAdapter(val kind:AdapterKind):OrchestratorCommand(); data class SendFrame(val peer:PeerRef,val bytes:ByteArray,val correlationId:String):OrchestratorCommand(); data class EmitUiStatus(val status:String):OrchestratorCommand(); data class ScheduleRetry(val reason:String,val backoffMs:Long):OrchestratorCommand() }
data class OrchestratorConfig(val preferredTransports:List<AdapterKind> = listOf(AdapterKind.BLE, AdapterKind.WIFI_LOCAL, AdapterKind.QR), val textRetryBudget:Int = 5)
class SessionOrchestrator(private val scope:CoroutineScope, private val adapters:Map<AdapterKind, TransportAdapter>, private val reducer:SessionReducer = SessionReducer(), private val capabilityNegotiator:CapabilityNegotiator = CapabilityNegotiator(), private val config:OrchestratorConfig = OrchestratorConfig()) {
 private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
 val state: StateFlow<SessionState> = _state.asStateFlow()
 private val _commands = MutableSharedFlow<OrchestratorCommand>(extraBufferCapacity = 64)
 val commands: SharedFlow<OrchestratorCommand> = _commands.asSharedFlow()
 private var eventsJob: Job? = null
 fun start() { eventsJob?.cancel(); eventsJob = scope.launch { merge(*adapters.values.map{it.events()}.toTypedArray()).collect { onTransportEvent(it) } }; config.preferredTransports.forEach { _commands.tryEmit(OrchestratorCommand.StartAdapter(it)) }; dispatch(SessionAction.StartDiscover); _commands.tryEmit(OrchestratorCommand.EmitUiStatus("Scanning nearby users…")) }
 fun stop() { eventsJob?.cancel(); adapters.keys.forEach{ _commands.tryEmit(OrchestratorCommand.StopAdapter(it)) }; dispatch(SessionAction.Reset) }
 fun dispatch(action:SessionAction){ _state.value = reducer.reduce(_state.value, action) }
 fun negotiate(local:LocalCapabilities, remote:PeerCapabilities, remoteVersions:List<Int>) = capabilityNegotiator.negotiate(local, remote, remoteVersions)
 private fun onTransportEvent(event:TransportEvent){ when(event){ is TransportEvent.PeerDiscovered -> { if (_state.value is SessionState.Discovering || _state.value is SessionState.Advertising){ dispatch(SessionAction.Connect(event.peer)); _commands.tryEmit(OrchestratorCommand.EmitUiStatus("Connecting…")) } }
  is TransportEvent.FrameReceived -> { if (_state.value is SessionState.Connecting){ val peer = (_state.value as SessionState.Connecting).peer; dispatch(SessionAction.Negotiated(peer, UUID.randomUUID().toString(), event.frame.transport)); _commands.tryEmit(OrchestratorCommand.EmitUiStatus("Secure session active")) } }
  is TransportEvent.LinkStateChanged -> { val current = _state.value; if (current is SessionState.Active && current.transport == event.transport && !event.available){ val fallback = config.preferredTransports.firstOrNull{it != current.transport}; if (fallback != null){ dispatch(SessionAction.Migrate(fallback)); _commands.tryEmit(OrchestratorCommand.EmitUiStatus("Trying local fallback…")); _commands.tryEmit(OrchestratorCommand.ScheduleRetry("transport_migrate",250)) } else dispatch(SessionAction.Fatal("NO_FALLBACK_TRANSPORT")) } }
  is TransportEvent.SendResult -> if (!event.success && event.retryable) _commands.tryEmit(OrchestratorCommand.ScheduleRetry("send_retry:${'$'}{event.correlationId}",200))
  is TransportEvent.PeerLost -> {}
 } }
}
