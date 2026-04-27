package com.lumos.feature.session

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionReducerTest {
    private val reducer = SessionReducer()

    @Test
    fun idle_to_discovering_to_connecting() {
        val s1 = reducer.reduce(SessionNode(), SessionEvent.StartDiscovery)
        assertEquals(SessionState.DISCOVERING, s1.state)
        val s2 = reducer.reduce(s1, SessionEvent.PeerDiscovered("peer-1"))
        assertEquals(SessionState.CONNECTING, s2.state)
    }

    @Test
    fun degraded_retries_until_budget() {
        val degraded = SessionNode(state = SessionState.DEGRADED, ctx = SessionContext(retryCount = 2, retryBudget = 3))
        val retry = reducer.reduce(degraded, SessionEvent.RetryBudgetAvailable)
        assertEquals(SessionState.RETRYING, retry.state)
    }
}
