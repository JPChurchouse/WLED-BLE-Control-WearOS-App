package com.jpchurchouse.wledblewear

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.jpchurchouse.wledblewear.presentation.screens.ControlScreen
import com.jpchurchouse.wledblewear.presentation.screens.ScanScreen
import com.jpchurchouse.wledblewear.presentation.theme.WledTheme
import com.jpchurchouse.wledblewear.viewmodel.WledViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object NavRoutes {
    const val SCAN    = "scan"
    const val CONTROL = "control"
}

/** Intent extra key written by WledTileService LaunchActions. */
const val EXTRA_TILE_COMMAND = "tile_cmd"

class MainActivity : ComponentActivity() {

    private val viewModel: WledViewModel by viewModels()
    private var navController: NavHostController? = null

    // ── Permission launcher ───────────────────────────────────────────────────

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.startScan()
        }
        // If denied the scan screen shows an empty list; no crash path.
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Observe connection state so we auto-navigate to ControlScreen on connect
        // and back to ScanScreen on user-initiated disconnect.
        // We do this in onCreate so it survives the first onNewIntent call.
        lifecycleScope.launch {
            viewModel.uiState
                .map { it.connectionState }
                .distinctUntilChanged()
                .collect { state ->
                    val nav = navController ?: return@collect
                    when (state) {
                        is com.jpchurchouse.wledblewear.model.ConnectionState.Connected -> {
                            if (nav.currentDestination?.route != NavRoutes.CONTROL) {
                                nav.navigate(NavRoutes.CONTROL) {
                                    popUpTo(NavRoutes.SCAN) { inclusive = false }
                                }
                            }
                        }
                        is com.jpchurchouse.wledblewear.model.ConnectionState.Idle -> {
                            if (nav.currentDestination?.route != NavRoutes.SCAN) {
                                nav.popBackStack(NavRoutes.SCAN, inclusive = false)
                            }
                        }
                        else -> Unit
                    }
                }
        }

        setContent {
            WledTheme {
                val nav = rememberSwipeDismissableNavController()
                    .also { navController = it }

                SwipeDismissableNavHost(
                    navController = nav,
                    startDestination = NavRoutes.SCAN,
                ) {
                    composable(NavRoutes.SCAN) {
                        ScanScreen(
                            viewModel  = viewModel,
                            onScanStart = { requestBlePermissions() },
                            onDeviceSelected = { device -> viewModel.connect(device) },
                        )
                    }
                    composable(NavRoutes.CONTROL) {
                        ControlScreen(
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }

        // Handle tile intent that launched this activity cold
        handleTileIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTileIntent(intent)
    }

    // ── Tile intent dispatch ──────────────────────────────────────────────────

    private fun handleTileIntent(intent: Intent?) {
        val cmd = intent?.getStringExtra(EXTRA_TILE_COMMAND) ?: return
        when {
            cmd == "pwr" -> viewModel.togglePower()
            cmd.startsWith("pre:") -> {
                val id = cmd.removePrefix("pre:").toIntOrNull() ?: return
                viewModel.activatePreset(id)
            }
        }
        // Clear extra so rotation / re-delivery doesn't re-fire the command
        intent.removeExtra(EXTRA_TILE_COMMAND)
    }

    // ── BLE permission helper ─────────────────────────────────────────────────

    private fun requestBlePermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
        permissionLauncher.launch(perms)
    }
}
