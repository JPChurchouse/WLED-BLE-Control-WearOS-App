package com.example.wledble

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.wledble.model.ConnectionState
import com.example.wledble.presentation.screens.ControlScreen
import com.example.wledble.presentation.screens.ScanScreen
import com.example.wledble.presentation.theme.WledBleTheme
import com.example.wledble.viewmodel.WledViewModel

/**
 * Single-activity WearOS app.
 *
 * Navigation graph:
 *   scan ──connect──▶ control
 *   control ──swipe-right──▶ scan   (also triggered by disconnect)
 *
 * SwipeDismissableNavHost provides the native swipe-to-go-back gesture.
 * All BLE permission logic lives here to keep Composables and ViewModel
 * framework-agnostic.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: WledViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            viewModel.startScan()
        }
        // If denied, user sees idle scan screen with "Scan Again" button
        // which re-triggers checkPermissionsAndScan() on tap.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            WledBleTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val navController = rememberSwipeDismissableNavController()

                // Automatically navigate when connection state changes.
                // LaunchedEffect key = connectionState class (not instance) so we
                // don't re-navigate on every state data field update.
                LaunchedEffect(uiState.connectionState::class) {
                    when (uiState.connectionState) {
                        is ConnectionState.Connected -> {
                            if (navController.currentDestination?.route != "control") {
                                navController.navigate("control") {
                                    // Keep scan on back-stack so swipe-right returns to it
                                    launchSingleTop = true
                                }
                            }
                        }
                        is ConnectionState.Disconnected -> {
                            // Pop back to scan when explicitly disconnected
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
                            onDisconnect     = {
                                viewModel.disconnect()
                                // NavController pop is driven by state above
                            }
                        )
                    }
                }
            }
        }

        // Kick off scan immediately on first launch
        checkPermissionsAndScan()
    }

    private fun checkPermissionsAndScan() {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            // API 26–30: location required for BLE scan results
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            viewModel.startScan()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}