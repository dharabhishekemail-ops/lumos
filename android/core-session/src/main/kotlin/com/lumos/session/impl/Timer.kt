
package com.lumos.session.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface Timer {
    fun schedule(delayMs: Long, task: () -> Unit): Cancellable
}

interface Cancellable {
    fun cancel()
}

class CoroutineTimer(private val scope: CoroutineScope) : Timer {
    override fun schedule(delayMs: Long, task: () -> Unit): Cancellable {
        val job: Job = scope.launch {
            delay(delayMs)
            task()
        }
        return object : Cancellable {
            override fun cancel() { job.cancel() }
        }
    }
}
