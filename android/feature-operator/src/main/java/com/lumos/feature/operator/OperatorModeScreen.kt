package com.lumos.feature.operator

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumos.core.remoteconfig.*

@Composable
fun OperatorModeScreen(
    vm: OperatorModeViewModel,
) {
    val ui by vm.ui.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Operator Mode", style = MaterialTheme.typography.headlineSmall)
        ElevatedCard {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Active Config", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Version: ${ui.activeVersion ?: "unknown"}")
                Text("Last Apply: ${ui.lastApplyStatus}")
            }
        }

        ElevatedCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fetch & Apply", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = ui.primaryUrl, onValueChange = vm::setPrimaryUrl, label = { Text("Primary URL") })
                OutlinedTextField(value = ui.stagePercent.toString(), onValueChange = vm::setStagePercent, label = { Text("Rollout %") })
                Button(onClick = { vm.fetchAndApply() }, enabled = !ui.busy) { Text("Fetch & Apply") }
                OutlinedButton(onClick = { vm.rollback() }, enabled = !ui.busy) { Text("Rollback to Previous") }
            }
        }
    }
}

data class OperatorUi(
    val busy: Boolean = false,
    val primaryUrl: String = "",
    val stagePercent: Int = 100,
    val activeVersion: String? = null,
    val lastApplyStatus: String = "—",
)

class OperatorModeViewModel(
    private val svc: OperatorModeService
) {
    private val _ui = kotlinx.coroutines.flow.MutableStateFlow(OperatorUi())
    val ui: kotlinx.coroutines.flow.StateFlow<OperatorUi> = _ui

    fun setPrimaryUrl(v: String) { _ui.value = _ui.value.copy(primaryUrl = v) }
    fun setStagePercent(v: String) { _ui.value = _ui.value.copy(stagePercent = v.toIntOrNull() ?: 100) }

    fun fetchAndApply() {
        val cur = _ui.value
        _ui.value = cur.copy(busy = true)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val res = svc.fetchAndApply(cur.primaryUrl, cur.stagePercent)
            _ui.value = _ui.value.copy(
                busy = false,
                lastApplyStatus = res.toString(),
                activeVersion = svc.activeVersion(),
            )
        }
    }

    fun rollback() {
        val cur = _ui.value
        _ui.value = cur.copy(busy = true)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val ok = svc.rollback()
            _ui.value = _ui.value.copy(
                busy = false,
                lastApplyStatus = if (ok) "Rolled back" else "Rollback failed",
                activeVersion = svc.activeVersion(),
            )
        }
    }
}
