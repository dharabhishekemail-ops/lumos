
package com.lumos.session
import com.lumos.protocol.*
import kotlin.math.min
import kotlin.random.Random

data class RetryPolicy(val initialDelayMs:Long=200,val maxDelayMs:Long=5000,val jitterPct:Int=20,val maxAttempts:Int=6)
data class RetryDecision(val shouldRetry:Boolean,val delayMs:Long,val nextAttempt:Int)
class RetryPolicyEngine(private val rnd:Random= Random(1)){
 fun next(p:RetryPolicy, attempt:Int):RetryDecision{
  if(attempt>=p.maxAttempts) return RetryDecision(false,0,attempt)
  var d=p.initialDelayMs; repeat(attempt){ d=min(p.maxDelayMs, d*2) }
  val j=(d*p.jitterPct)/100; val jitter= if(j>0) rnd.nextLong(-j, j+1) else 0
  return RetryDecision(true,(d+j).coerceAtLeast(0),attempt+1)
 }
}
class DuplicateSuppressionWindow(private val ttlMs:Long, private val nowMs:()->Long){
 private val seen=LinkedHashMap<String,Long>()
 fun shouldAccept(key:String):Boolean{ evict(); if(seen.containsKey(key)) return false; seen[key]=nowMs(); return true }
 private fun evict(){ val n=nowMs(); val it=seen.entries.iterator(); while(it.hasNext()){ val e=it.next(); if(n-e.value>ttlMs) it.remove() } }
}
interface Scheduler{ fun schedule(delayMs:Long, task:()->Unit):Cancellable }
interface Cancellable{ fun cancel() }
class FakeScheduler:Scheduler{
 private data class Job(val due:Long,val t:()->Unit,var c:Boolean=false):Cancellable{ override fun cancel(){c=true} }
 var nowMs:Long=0; private val jobs= mutableListOf<Job>()
 override fun schedule(delayMs:Long, task:()->Unit):Cancellable { val j=Job(nowMs+delayMs, task); jobs+=j; return j }
 fun advanceBy(ms:Long){ nowMs+=ms; val due=jobs.filter{!it.c && it.due<=nowMs}.toList(); jobs.removeAll(due); due.forEach{it.t()} }
}
interface ProtocolCoder{ fun encode(m:TypedMessage):ByteArray; fun decode(b:ByteArray):TypedMessage }
object JsonCoder:ProtocolCoder{ override fun encode(m:TypedMessage)=ProtocolCodec.encode(m); override fun decode(b:ByteArray)=ProtocolCodec.decode(b) }
sealed interface OrchestratorCommand{ data class Send(val adapterId:String,val bytes:ByteArray):OrchestratorCommand; data class StartAdapter(val kind:TransportKind):OrchestratorCommand; data class ScheduleRetry(val reason:String,val delayMs:Long):OrchestratorCommand }
data class OrchestratorSnapshot(val activeTransport:TransportKind?=null,val attemptsByReason:Map<String,Int> = emptyMap())
class SessionOrchestratorService(private val coder:ProtocolCoder, private val retry:RetryPolicyEngine, private val scheduler:Scheduler, private val dedupe:DuplicateSuppressionWindow){
 private var snap=OrchestratorSnapshot()
 fun snapshot()=snap
 fun onOutbound(adapterId:String,msg:TypedMessage)= listOf(OrchestratorCommand.Send(adapterId, coder.encode(msg)))
 fun onInbound(bytes:ByteArray):List<OrchestratorCommand>{ val m=coder.decode(bytes); if(!dedupe.shouldAccept("${m.envelope.sessionId}:${m.envelope.messageId}")) return emptyList(); return when(val p=m.payload){ is Payload.TransportMigrate -> p.proposed.firstOrNull()?.let{ snap=snap.copy(activeTransport=it); listOf(OrchestratorCommand.StartAdapter(it)) }?: emptyList(); else -> emptyList() } }
 fun onTransportFailure(reason:String):List<OrchestratorCommand>{ val a=snap.attemptsByReason[reason]?:0; val d=retry.next(RetryPolicy(),a); if(!d.shouldRetry) return emptyList(); snap=snap.copy(attemptsByReason=snap.attemptsByReason + (reason to d.nextAttempt)); scheduler.schedule(d.delayMs){ }; return listOf(OrchestratorCommand.ScheduleRetry(reason,d.delayMs)) }
}
data class SimPacket(val seq:Int,val payload:ByteArray)
data class FaultProfile(val dropPct:Int=0,val duplicatePct:Int=0,val maxDuplicates:Int=1,val reorderWindow:Int=0,val minLatencyMs:Long=0,val maxLatencyMs:Long=0)
data class ScheduledPacket(val deliverAtMs:Long,val packet:SimPacket)
class DeterministicFaultSimulator(seed:Int=7){
 private val rnd=Random(seed)
 fun process(input:List<SimPacket>, p:FaultProfile, startMs:Long=0):List<ScheduledPacket>{
  val out= mutableListOf<ScheduledPacket>(); var now=startMs
  for(pkt in input){
   if(rnd.nextInt(100)<p.dropPct) continue
   val dups= if(rnd.nextInt(100)<p.duplicatePct) 1+rnd.nextInt(p.maxDuplicates.coerceAtLeast(1)) else 0
   repeat(1+dups){ val lat= if(p.maxLatencyMs>p.minLatencyMs) rnd.nextLong(p.minLatencyMs,p.maxLatencyMs+1) else p.minLatencyMs; out+=ScheduledPacket(now+lat,pkt) }
   now++
  }
  val reordered = if(p.reorderWindow>1) out.chunked(p.reorderWindow).flatMap{ it.shuffled(rnd) } else out
  return reordered.sortedBy{it.deliverAtMs}
 }
}
