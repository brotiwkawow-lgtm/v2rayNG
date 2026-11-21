package com.v2ray.ang.nox

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class NoxActivity : ComponentActivity() {
    private val vm: NoxVm by viewModels()

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) NoxBridge.start(this) else vm.connecting = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NoxApp(vm) { toggle() } }
    }

    override fun onResume() { super.onResume(); vm.refresh() }

    private fun toggle() {
        if (vm.connected) { NoxBridge.stop(this); return }
        if (vm.connecting) return
        lifecycleScope.launch {
            if (!vm.resolveSelection()) return@launch
            val intent = VpnService.prepare(this@NoxActivity)
            if (intent != null) vpnPermission.launch(intent) else NoxBridge.start(this@NoxActivity)
        }
    }
}
