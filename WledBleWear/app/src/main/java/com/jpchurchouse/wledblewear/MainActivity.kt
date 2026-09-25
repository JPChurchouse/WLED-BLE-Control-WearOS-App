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
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.jpchurchouse.wledblewear.model.ConnectionState
import com.jpchurchouse.wledblewear.presentation.screens.ControlScreen
import com.jpchurchouse.wledblewear.presentation.screens.DevicesScreen
import com.jpchurchouse.wledblewear.presentation.screens.ScanScreen
import com.jpchurchouse.wledblewear.presentation.theme.WledTheme
import com.jpchurchouse.wledblewear.viewmodel.WledViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object NavRoutes {
    const val DEVICES = "devices"
    const val SCAN    = "scan"
    const val CONTROL = "control"
}

/** Intent extra: open the control screen for the tile's current device. */
const val EXTRA_OPEN_CURRENT = "open_current"

class MainActivity : ComponentActivity() {

    private val viewModel: WledViewModel by viewModels()
    private var navController: NavHostController? = null
    private var afterPermissions: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) afterPermissions?.invoke()
        afterPermissions = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // After a user disconnect (Idle, no problem to show), return to the device list.
        lifecycleScope.launch {
            viewModel.uiState
                .map { it.connectionState to it.problem }
                .distinctUntilChanged()
                .collect { (state, problem) ->
                    val nav = navController ?: return@collect
                    if (state == ConnectionState.Idle && problem == null &&
                        nav.currentDestination?.route == NavRoutes.CONTROL
                    ) {
                        nav.popBackStack(NavRoutes.DEVICES, inclusive = false)
                    }
                }
        }

        setContent {
            WledTheme {
                val nav = rememberSwipeDismissableNavController().also { navController = it }
                SwipeDismissableNavHost(navController = nav, startDestination = NavRoutes.DEVICES) {
                    composable(NavRoutes.DEVICES) {
                        DevicesScreen(
                            viewModel = viewModel,
                            onDeviceSelected = { device ->
                                withPermissions {
                                    viewModel.connect(device)
                                    nav.navigate(NavRoutes.CONTROL)
                                }
                            },
                            onScan = {
                                withPermissions {
                                    viewModel.startScan()
                                    nav.navigate(NavRoutes.SCAN)
                                }
                            },
                        )
                    }
                    composable(NavRoutes.SCAN) {
                        ScanScreen(
                            viewModel = viewModel,
                            onRescan = { withPermissions { viewModel.startScan() } },
                            onDeviceSelected = { device ->
                                viewModel.connect(device)
                                nav.navigate(NavRoutes.CONTROL) {
                                    popUpTo(NavRoutes.DEVICES) { inclusive = false }
                                }
                            },
                        )
                    }
                    composable(NavRoutes.CONTROL) {
                        ControlScreen(viewModel = viewModel)
                    }
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_CURRENT, false) != true) return
        intent.removeExtra(EXTRA_OPEN_CURRENT)
        withPermissions {
            viewModel.connectToCurrentDevice()
            window.decorView.post {
                navController?.let { nav ->
                    if (nav.currentDestination?.route != NavRoutes.CONTROL) nav.navigate(NavRoutes.CONTROL)
                }
            }
        }
    }

    // ── BLE permissions ───────────────────────────────────────────────────────

    private fun withPermissions(block: () -> Unit) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            block()
        } else {
            afterPermissions = block
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
