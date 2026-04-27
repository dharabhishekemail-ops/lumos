package com.lumos.session
import org.junit.Test
import org.junit.Assert.*
class FaultSimulatorTests{@Test fun deterministic(){val p=(1..10).map{SimPacket(it, byteArrayOf(it.toByte()))}; val fp=FaultProfile(dropPct=10,duplicatePct=30,maxDuplicates=2,reorderWindow=3,minLatencyMs=1,maxLatencyMs=5); val a=DeterministicFaultSimulator(42).process(p,fp); val b=DeterministicFaultSimulator(42).process(p,fp); assertEquals(a.map{it.deliverAtMs to it.packet.seq}, b.map{it.deliverAtMs to it.packet.seq})}}