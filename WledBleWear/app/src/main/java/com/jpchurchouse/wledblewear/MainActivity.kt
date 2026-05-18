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
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.tiles.TileService
import com.jpchurchouse.wledblewear.data.WledPreferences
import com.jpchurchouse.wledblewear.data.wledDataStore
import com.jpchurchouse.wledblewear.model.ConnectionState
import com.jpchurchouse.wledblewear.presentation.screens.ControlScreen
import com.jpchurchouse.wledblewear.presentation.screens.ScanScreen
import com.jpchurchouse.wledblewear.presentation.theme.WledTheme
import com.jpchurchouse.wledblewear.tile.WledTileService
import com.jpchurchouse.wledblewear.viewmodel.WledViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object NavRoutes {
    const val SCAN    = "scan"
    const val CONTROL = "control"
}

/** Intent extra key written by WledTileService / complication LaunchActions. */
const val EXTRA_TILE_COMMAND = "tile_cmd"

class MainActivity : ComponentActivity() {

    private val viewModel: WledViewModel by viewModels()
    private var navController: NavHostController? = null

    // ── Permission launcher ───────────────────────────────────────────────────

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) viewModel.startScan()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Auto-navigate to ControlScreen on connection; back to ScanScreen on Idle.
        lifecycleScope.launch {
            viewModel.uiState
                .map { it.connectionState }
                .distinctUntilChanged()
                .collect { state ->
                    val nav = navController ?: return@collect
                    when (state) {
                        is ConnectionState.Connected -> {
                            if (nav.currentDestination?.route != NavRoutes.CONTROL) {
                                nav.navigate(NavRoutes.CONTROL) {
                                    popUpTo(NavRoutes.SCAN) { inclusive = false }
                                }
                            }
                        }
                        is ConnectionState.Idle -> {
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
                    navController    = nav,
                    startDestination = NavRoutes.SCAN,
                ) {
                    composable(NavRoutes.SCAN) {
                        ScanScreen(
                            viewModel        = viewModel,
                            onScanStart      = { requestBlePermissions() },
                            onDeviceSelected = { device -> viewModel.connect(device) },
                        )
                    }
                    composable(NavRoutes.CONTROL) {
                        ControlScreen(viewModel = viewModel)
                    }
                }
            }
        }

        handleTileIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTileIntent(intent)
    }

    // ── Tile intent dispatch ──────────────────────────────────────────────────

    private fun handleTileIntent(intent: Intent?) {
        val cmd = intent?.getStringExtra(EXTRA_TILE_COMMAND) ?: return
        // Clear immediately so rotation / re-delivery doesn't re-fire
        intent.removeExtra(EXTRA_TILE_COMMAND)

        when {
            cmd == WledTileService.CMD_CONNECT_LCD -> handleConnectLcd()
            cmd == "pwr"                           -> viewModel.togglePower()
            cmd.startsWith("pre:")                 -> {
                val id = cmd.removePrefix("pre:").toIntOrNull() ?: return
                viewModel.activatePreset(id)
            }
        }
    }

    /**
     * Tile "connect_lcd" handler.
     *
     * 1. Clear any previous CONNECT_FAILED flag so the tile shows a clean attempt.
     * 2. Ask ViewModel to connect to the last device (reads DataStore internally).
     * 3. Wait up to CONNECT_TIMEOUT_MS for ConnectionState.Connected.
     *    - On success: the state observer above navigates to ControlScreen automatically.
     *    - On timeout: disconnect (cancels backoff), write CONNECT_FAILED, request tile update.
     *
     * Timeout budget: 10 s covers 3 backoff attempts (t=0 s, t+2 s, t+6 s).
     */
    private fun handleConnectLcd() {
        lifecycleScope.launch {
            // Reset failure flag before the new attempt
            applicationContext.wledDataStore.edit { prefs ->
                prefs.remove(WledPreferences.CONNECT_FAILED)
            }

            // If already connected, just surface the control screen
            if (viewModel.uiState.value.connectionState == ConnectionState.Connected) {
                navController?.navigate(NavRoutes.CONTROL) {
                    popUpTo(NavRoutes.SCAN) { inclusive = false }
                }
                return@launch
            }

            viewModel.connectToLastDevice()

            val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                viewModel.uiState.first {
                    it.connectionState == ConnectionState.Connected
                }
            }

            if (connected == null) {
                // Timed out — cancel the BLE backoff loop and flag the failure in DataStore.
                // WledApplication.startStateSync() will pick up the resulting Idle emission
                // and clear stale state; we additionally set CONNECT_FAILED so the tile
                // shows the explicit failure layout.
                viewModel.disconnect()
                applicationContext.wledDataStore.edit { prefs ->
                    prefs[WledPreferences.CONNECT_FAILED] = true
                }
                // Request a tile refresh so the failure layout appears immediately
                TileService.getUpdater(applicationContext)
                    .requestUpdate(WledTileService::class.java)
            }
            // Success path: the state observer's Connected branch navigates to ControlScreen.
        }
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

    companion object {
        /** 10 s: covers connect attempt at t=0 + retries at t+2 s and t+6 s. */
        private const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
