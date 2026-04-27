package com.lumos.app

import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.lumos.core.session.SessionOrchestrator

/**
 * Phase 9: lifecycle hardening hook.
 * - When app goes background: degrade scanning/advertising per policy/config.
 * - When app returns foreground: resume.
 * - On power saver: prefer lower duty-cycle.
 *
 * NOTE: Permission revoke listeners should be wired via ActivityResult APIs in UI module;
 * here we expose orchestrator APIs to respond deterministically.
 */
class LifecycleHardening(
    private val context: Context,
    private val orchestrator: SessionOrchestrator
) : DefaultLifecycleObserver {

    private val pm: PowerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    override fun onStart(owner: LifecycleOwner) {
        orchestrator.onAppForegrounded(isPowerSave = pm.isPowerSaveMode)
    }

    override fun onStop(owner: LifecycleOwner) {
        orchestrator.onAppBackgrounded()
    }
}
