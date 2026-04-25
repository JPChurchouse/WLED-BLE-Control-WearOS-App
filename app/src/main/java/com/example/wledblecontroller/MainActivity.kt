package com.jpchurchouse.wledblewear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.jpchurchouse.wledblewear.model.ConnectionState
import com.jpchurchouse.wledblewear.presentation.screens.ControlScreen
import com.jpchurchouse.wledblewear.presentation.screens.ScanScreen
import com.jpchurchouse.wledblewear.presentation.theme.WledBleTheme
import com.jpchurchouse.wledblewear.viewmodel.WledViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: WledViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results: Map<String, Boolean> ->
        if (results.values.all { granted -> granted }) {
            viewModel.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleTileCommand(intent?.getStringExtra(EXTRA_TILE_COMMAND))

        setContent {
            WledBleTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val navController = rememberSwipeDismissableNavController()

                LaunchedEffect(uiState.connectionState::class) {
                    when (uiState.connectionState) {
                        is ConnectionState.Connected -> {
                            if (navController.currentDestination?.route != "control") {
                                navController.navigate("control") { launchSingleTop = true }
                            }
                        }
                        is ConnectionState.Disconnected -> {
                            if (navController.currentDestination?.route == "control") {
                                navController.popBackStack()
                            }
                        }
                        else -> Unit
                    }
                }

                SwipeDismissableNavHost(
                    navController    = navController,
                    startDestination = "scan"
                ) {
                    composable("scan") {
                        ScanScreen(
                            uiState     = uiState,
                            onStartScan = { checkPermissionsAndScan() },
                            onConnect   = { viewModel.connect(it.device) }
                        )
                    }
                    composable("control") {
                        ControlScreen(
                            uiState          = uiState,
                            onTogglePower    = { viewModel.togglePower() },
                            onActivatePreset = { viewModel.activatePreset(it) },
                            onDisconnect     = { viewModel.disconnect() }
                        )
                    }
                }
            }
        }

        checkPermissionsAndScan()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTileCommand(intent.getStringExtra(EXTRA_TILE_COMMAND))
    }

    private fun handleTileCommand(cmd: String?) {
        when {
            cmd == null                          -> return
            cmd == CMD_TOGGLE_POWER              -> viewModel.togglePower()
            cmd.startsWith(CMD_PRESET_PREFIX)    -> {
                val id = cmd.removePrefix(CMD_PRESET_PREFIX).toIntOrNull() ?: return
                viewModel.activatePreset(id)
            }
        }
    }

    private fun checkPermissionsAndScan() {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) viewModel.startScan()
        else permissionLauncher.launch(missing.toTypedArray())
    }

    companion object {
        const val EXTRA_TILE_COMMAND = "tile_cmd"
        const val CMD_TOGGLE_POWER   = "pwr"
        const val CMD_PRESET_PREFIX  = "pre:"
    }
}