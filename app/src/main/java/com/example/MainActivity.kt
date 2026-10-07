package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.ConnectedDevice
import com.example.ui.components.BandwidthLimitDialog
import com.example.ui.components.DeviceDetailDialog
import com.example.ui.components.DeviceQuotaDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.FirewallScreen
import com.example.ui.screens.InfinixToolsScreen
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.HotspotViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val viewModel: HotspotViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

data class NavItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun MainAppScreen(viewModel: HotspotViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog States
    var selectedDeviceForLimit by remember { mutableStateOf<ConnectedDevice?>(null) }
    var selectedDeviceForQuota by remember { mutableStateOf<ConnectedDevice?>(null) }
    var selectedDeviceForDetails by remember { mutableStateOf<ConnectedDevice?>(null) }

    // Check location permission for Android 10 Wi-Fi hotspot probing
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val fineGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        hasLocationPermission = fineGranted
        if (fineGranted) {
            viewModel.scanNetwork()
        }
    }

    // Auto-request permissions on first launch
    LaunchedEffect(Unit) {
        val permissionsToAsk = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToAsk.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (!hasLocationPermission) {
            permissionLauncher.launch(permissionsToAsk.toTypedArray())
        }
    }

    // Listen to Toast / Alert events from ViewModel
    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Handle system back navigation
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    val navItems = listOf(
        NavItem(
            title = "Surveillance",
            selectedIcon = Icons.Filled.Speed,
            unselectedIcon = Icons.Outlined.Speed,
            testTag = "nav_tab_dashboard"
        ),
        NavItem(
            title = "Appareils",
            selectedIcon = Icons.Filled.Devices,
            unselectedIcon = Icons.Outlined.Devices,
            testTag = "nav_tab_devices"
        ),
        NavItem(
            title = "Contrôle",
            selectedIcon = Icons.Filled.Security,
            unselectedIcon = Icons.Outlined.Security,
            testTag = "nav_tab_firewall"
        ),
        NavItem(
            title = "Infinix Tools",
            selectedIcon = Icons.Filled.Tune,
            unselectedIcon = Icons.Outlined.Tune,
            testTag = "nav_tab_tools"
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CyberBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = CyberSurfaceElevated,
                contentColor = TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("main_bottom_nav_bar")
            ) {
                navItems.forEachIndexed { index, item ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.title
                            )
                        },
                        label = {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberBackground,
                            selectedTextColor = CyberCyan,
                            indicatorColor = CyberCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag(item.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Permission request banner if location permission is not yet granted
            if (!hasLocationPermission) {
                Surface(
                    color = CyberAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = CyberAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Autorisez la localisation requise par Android 10 pour détecter le Wi-Fi.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextPrimary
                            )
                        }

                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberAmber,
                                contentColor = CyberBackground
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Autoriser", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToDevices = { selectedTab = 1 },
                        onSelectDeviceForLimit = { selectedDeviceForLimit = it },
                        onSelectDeviceForQuota = { selectedDeviceForQuota = it },
                        onSelectDeviceForDetails = { selectedDeviceForDetails = it }
                    )
                    1 -> DevicesScreen(
                        viewModel = viewModel,
                        onSelectDeviceForLimit = { selectedDeviceForLimit = it },
                        onSelectDeviceForQuota = { selectedDeviceForQuota = it },
                        onSelectDeviceForDetails = { selectedDeviceForDetails = it }
                    )
                    2 -> FirewallScreen(
                        viewModel = viewModel,
                        onSelectDeviceForLimit = { selectedDeviceForLimit = it }
                    )
                    3 -> InfinixToolsScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    // Bandwidth Limiter Dialog
    selectedDeviceForLimit?.let { device ->
        BandwidthLimitDialog(
            device = device,
            onDismiss = { selectedDeviceForLimit = null },
            onApplyLimit = { limit ->
                viewModel.setBandwidthLimit(device, limit)
            }
        )
    }

    // Quota Dialog
    selectedDeviceForQuota?.let { device ->
        DeviceQuotaDialog(
            device = device,
            onDismiss = { selectedDeviceForQuota = null },
            onApplyQuota = { quota ->
                viewModel.setQuota(device, quota)
            }
        )
    }

    // Detail & Root/ADB Command Dialog
    selectedDeviceForDetails?.let { device ->
        DeviceDetailDialog(
            device = device,
            onDismiss = { selectedDeviceForDetails = null },
            onSaveNickname = { nickname ->
                viewModel.setCustomNickname(device, nickname)
            },
            onExecuteRootCmd = { cmd ->
                viewModel.executeRootCommand(cmd)
            }
        )
    }
}
