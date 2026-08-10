package com.example

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.purelock.ui.PureLockScreen
import com.example.purelock.ui.PureLockViewModel
import com.example.ui.theme.PureLockTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PureLockViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "VPN permission granted. Tap Enable Protection again.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "VPN permission is required to enable adult content filtering.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PureLockTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PureLockScreen(
                        viewModel = viewModel,
                        onRequestVpnPermission = { requestVpnPermission() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        }
    }
}
