package com.example

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.db.EmergencyEvent
import com.example.db.SyncQueueItem
import com.example.db.VaultFile
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.VaultViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F1116) // Immerse dark background
                ) {
                    VaultApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VaultApp(viewModel: VaultViewModel = viewModel()) {
    val context = LocalContext.current
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    // Handle toast events
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearToast()
        }
    }

    // Handle Location permissions at the top level
    val locationPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Automatically request location permissions on application start
    LaunchedEffect(Unit) {
        if (!locationPermissionState.allPermissionsGranted) {
            locationPermissionState.launchMultiplePermissionRequest()
        }
    }

    // Process local sync queue whenever the application returns to the foreground
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.forceNetworkSyncNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AnimatedContent(
        targetState = isLocked,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "lock_transition"
    ) { locked ->
        if (locked) {
            VaultKeypadScreen(viewModel)
        } else {
            VaultDashboardScreen(viewModel, locationPermissionState.allPermissionsGranted)
        }
    }
}

// ==========================================
//              KEYPAD SECURE VIEW
// ==========================================
@Composable
fun VaultKeypadScreen(viewModel: VaultViewModel) {
    var pinText by remember { mutableStateOf("") }
    val wrongAttempts by viewModel.wrongPinAttempts.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Security indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Vault Safe Locked",
                tint = if (wrongAttempts > 0) Color(0xFFFF2E5B) else Color(0xFF00E5FF),
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = 8.dp)
            )
            Text(
                text = "SECURE VAULT",
                fontSize = 24.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Text(
                text = "SYSTEM STANDBY: ENCRYPTED BACKGROUND SYNC ACTIVE",
                fontSize = 10.sp,
                color = Color.LightGray.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        // Center PIN display card
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Asterisk markers representing the PIN code
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color(0xFF1E222D), RoundedCornerShape(16.dp))
                    .padding(horizontal = 32.dp, vertical = 16.dp)
                    .widthIn(min = 160.dp)
            ) {
                // Display passcode characters formatted with dots
                val display = if (pinText.isEmpty()) "ENTER PIN" else pinText.map { "●" }.joinToString(" ")
                Text(
                    text = display,
                    color = if (pinText.isEmpty()) Color.LightGray.copy(alpha = 0.5f) else Color.White,
                    fontSize = if (pinText.isEmpty()) 16.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = if (pinText.isEmpty()) 0.sp else 6.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (wrongAttempts > 0) {
                Text(
                    text = "Failed security verification attempts: $wrongAttempts/3\n" +
                            "Notice: 3 or more errors forces an emergency camera capture.",
                    color = Color(0xFFFF2E5B),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                Text(
                    text = "Default vault security PIN is: 9999",
                    color = Color.LightGray.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        // Secure Entry Grid (Keypad 0-9)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "OK")
            )

            for (row in keys) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    for (key in row) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    when (key) {
                                        "C" -> Color(0xFF373D4B)
                                        "OK" -> Color(0xFF00E5FF)
                                        else -> Color(0xFF1E222D)
                                    }
                                )
                                .clickable {
                                    when (key) {
                                        "C" -> {
                                            if (pinText.isNotEmpty()) pinText = pinText.dropLast(1)
                                        }
                                        "OK" -> {
                                            if (pinText.isNotEmpty()) {
                                                viewModel.verifyPin(pinText)
                                                pinText = ""
                                            }
                                        }
                                        else -> {
                                            if (pinText.length < 4) {
                                                pinText += key
                                            }
                                        }
                                    }
                                }
                                .testTag("keypad_$key"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                color = if (key == "OK") Color.Black else Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Threat simulation button at absolute bottom
        Button(
            onClick = { viewModel.simulateEmergencyAttack() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("simulate_threat_btn")
        ) {
            Icon(Icons.Filled.Warning, contentDescription = "Simulate Intrusion Bypass", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "SIMULATE INTRUSION ATTACK (FORCE SYNC LOG)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ==========================================
//             DASHBOARD ROOT VIEW
// ==========================================
@Composable
fun VaultDashboardScreen(
    viewModel: VaultViewModel,
    hasLocationPermission: Boolean
) {
    val context = LocalContext.current
    var configMenuOpen by remember { mutableStateOf(false) }
    var currentBaseUrl by remember { mutableStateOf("") }
    val apiBaseUrl by viewModel.apiBaseUrl.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0 = Dashboard, 1 = File Hub, 2 = Sync Log

    LaunchedEffect(apiBaseUrl) {
        currentBaseUrl = apiBaseUrl
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.LockOpen,
                            contentDescription = "Vault Server Unlocked",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "VAULT MANAGER",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { configMenuOpen = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Web Sync Server Core Settings", tint = Color.LightGray)
                    }
                    IconButton(
                        onClick = { viewModel.lockVault() },
                        modifier = Modifier.testTag("lock_button")
                    ) {
                        Icon(Icons.Filled.ExitToApp, contentDescription = "Lock Application Safe", tint = Color(0xFFFF2E5B))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F1116),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF11141B),
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Filled.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Diagnostics", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00E5FF),
                        selectedTextColor = Color(0xFF00E5FF),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF00E5FF).copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Filled.CloudUpload, contentDescription = "Crypt Capture Hub") },
                    label = { Text("Secure Hub", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00E5FF),
                        selectedTextColor = Color(0xFF00E5FF),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF00E5FF).copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Filled.History, contentDescription = "Intruder Alert History") },
                    label = { Text("Breach Audit", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00E5FF),
                        selectedTextColor = Color(0xFF00E5FF),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF00E5FF).copy(alpha = 0.15f)
                    )
                )
            }
        },
        containerColor = Color(0xFF0F1116)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                0 -> DiagnosticsTabScreen(viewModel, hasLocationPermission)
                1 -> CryptCaptureHubScreen(viewModel)
                2 -> ThreatAuditScreen(viewModel)
            }

            // Edit API Server Base URL dialog box
            if (configMenuOpen) {
                AlertDialog(
                    onDismissRequest = { configMenuOpen = false },
                    title = {
                        Text(
                            "SYNC ENDPOINT CONFIGURATION",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Define the HTTPS base address hosting the vault cloud backup. Connection requires strict TLS (HTTPS only) safety checks.",
                                fontSize = 12.sp,
                                color = Color.LightGray
                            )
                            OutlinedTextField(
                                value = currentBaseUrl,
                                onValueChange = { currentBaseUrl = it },
                                label = { Text("API Sync Base URL") },
                                textStyle = LocalTextStyle.current.copy(color = Color.White),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_url_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00E5FF),
                                    unfocusedBorderColor = Color.Gray
                                )
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (currentBaseUrl.startsWith("https://")) {
                                    viewModel.updateApiUrl(currentBaseUrl)
                                    configMenuOpen = false
                                } else {
                                    Toast.makeText(context, "URL must use HTTPS protocol!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("apply_url_btn")
                        ) {
                            Text("SAVE", color = Color(0xFF00E5FF))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { configMenuOpen = false }) {
                            Text("DISMISS", color = Color.Gray)
                        }
                    },
                    containerColor = Color(0xFF1E222D)
                )
            }
        }
    }
}

// ==========================================
//            TAB 0: DIAGNOSTICS & QUEUE
// ==========================================
@Composable
fun DiagnosticsTabScreen(
    viewModel: VaultViewModel,
    hasLocationPermission: Boolean
) {
    val activeQueue by viewModel.activeQueue.collectAsStateWithLifecycle()
    val deviceReg by viewModel.deviceRegistration.collectAsStateWithLifecycle()
    val totalFiles by viewModel.vaultFiles.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // System status header metrics
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222D)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SYSTEM ENGINE DIAGNOSTIC",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E5FF)
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (activeQueue.isNotEmpty()) Color(0xFFFFA000) else Color(0xFF00E5FF),
                                    CircleShape
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Active Sync Queue", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                "${activeQueue.size} items",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeQueue.isNotEmpty()) Color(0xFFFFA000) else Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Encrypted Library", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                "${totalFiles.size} vault files",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (hasLocationPermission) "GPS Telemetry Attached ✓" else "GPS Telemetry Missing ×",
                            fontSize = 10.sp,
                            color = if (hasLocationPermission) Color(0xFF00E5FF) else Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "WorkManager Scheduled (15m)",
                            fontSize = 10.sp,
                            color = Color.LightGray.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Hardware device fingerprint registration details
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222D)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "REGISTERED DEVICE FINGERPRINT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val currentReg = deviceReg
                    if (currentReg != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Device ID:", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                Text(
                                    currentReg.deviceId.take(18) + "...",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Device Model:", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                Text(currentReg.deviceModel, fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("OS Details:", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                Text(currentReg.osVersion, fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Cloud Synced:", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                Text(
                                    if (currentReg.isRegisteredOnBackend) "REGISTERED" else "OUTBOX QUEUED (OFFLINE)",
                                    fontSize = 11.sp,
                                    color = if (currentReg.isRegisteredOnBackend) Color(0xFF00E5FF) else Color(0xFFFFCC00),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        Text("Configuring Secure Fingerprint Metadata...", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }

        // Action manual Sync Trigger
        item {
            Button(
                onClick = { viewModel.forceNetworkSyncNow() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("force_sync_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = "Sync Local Queue Immediately",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RUN FORCE QUEUE SYNC NOW",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Live list details heading
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PENDING CLOUD SYNC QUEUE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E222D), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${activeQueue.size} in line",
                        fontSize = 10.sp,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (activeQueue.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .background(Color(0xFF11141B), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Clear Queue",
                            tint = Color(0xFF00E5FF).copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Cloud backup queue completely synchronized.\n" +
                                    "Your encrypted items are matching.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        } else {
            items(activeQueue) { queueItem ->
                SyncQueueRowItem(queueItem)
            }
        }
    }
}

@Composable
fun SyncQueueRowItem(item: SyncQueueItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222D)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (item.isEmergency) Icons.Filled.Warning else Icons.Filled.InsertDriveFile,
                        contentDescription = "File sync Type",
                        tint = if (item.isEmergency) Color(0xFFFF2E5B) else Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        val simpleId = "File UID: #${item.fileId}"
                        Text(
                            text = if (item.isEmergency) "SECURITY BREACH LOG" else simpleId,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Registered queue item #${item.id}",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Styled Status Badge Pill
                Box(
                    modifier = Modifier
                        .background(
                            when (item.status) {
                                "SYNCING" -> Color(0xFFFFA000).copy(alpha = 0.15f)
                                "FAILED" -> Color(0xFFFF2E5B).copy(alpha = 0.15f)
                                else -> Color.LightGray.copy(alpha = 0.1f)
                            },
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (item.status) {
                            "SYNCING" -> Color(0xFFFFA000)
                            "FAILED" -> Color(0xFFFF2E5B)
                            else -> Color.LightGray
                        },
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Attached coordinate metadata metrics
            val locationText = if (item.latitude != null && item.longitude != null) {
                "GPS Attached: ${"%.4f".format(item.latitude)}, ${"%.4f".format(item.longitude)}"
            } else {
                "GPS Metadata: Retrying/Permission Missing"
            }
            Text(
                text = locationText,
                fontSize = 10.sp,
                color = Color.LightGray.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace
            )

            if (item.retryCount > 0) {
                Text(
                    text = "Sync failure retry attempts: ${item.retryCount} times",
                    fontSize = 10.sp,
                    color = Color(0xFFFFE57F),
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item.lastError?.let {
                Text(
                    text = "Last block message: $it",
                    fontSize = 9.sp,
                    color = Color(0xFFFF2E5B),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ==========================================
//          TAB 1: CRYPT CAPTURE HUB
// ==========================================
@Composable
fun CryptCaptureHubScreen(viewModel: VaultViewModel) {
    var documentTitle by remember { mutableStateOf("") }
    var documentBody by remember { mutableStateOf("") }
    val importedFiles by viewModel.vaultFiles.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222D)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "NEW SECURE ENVELOPE ENCRYPTION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E5FF),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = documentTitle,
                        onValueChange = { documentTitle = it },
                        label = { Text("File Name (e.g., credentials.cfg)") },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .testTag("doc_title_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color.Gray
                        )
                    )

                    OutlinedTextField(
                        value = documentBody,
                        onValueChange = { documentBody = it },
                        label = { Text("Encrypted Vault Content Payload") },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .padding(bottom = 16.dp)
                            .testTag("doc_body_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color.Gray
                        )
                    )

                    Button(
                        onClick = {
                            if (documentTitle.isNotBlank() && documentBody.isNotBlank()) {
                                viewModel.importSecureItem(documentTitle, documentBody)
                                documentTitle = ""
                                documentBody = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("encrypt_save_btn"),
                        enabled = documentTitle.isNotBlank() && documentBody.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = "Encrypt and lock", tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "ENCRYPT & ADD TO SYNC QUEUE",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "ENCRYPTED CLIENT STORAGE ARCHIVE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (importedFiles.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .background(Color(0xFF11141B), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Vault filesystem empty. Encrypt and import files to review client archive.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            items(importedFiles) { file ->
                LockedArchiveRow(file)
            }
        }
    }
}

@Composable
fun LockedArchiveRow(file: VaultFile) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    val displayDate = formatter.format(Date(file.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222D)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = "AES-256 Secure",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        file.filename,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "AES SHARED INDICES • ${file.sizeBytes} B",
                        fontSize = 9.sp,
                        color = Color(0xFFFFA000),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "Created: $displayDate",
                        fontSize = 9.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Secure status icon representing encryption keys matched
            Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = "Matched key",
                tint = Color.LightGray.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ==========================================
//          TAB 2: THREAT AUDIT LOGS
// ==========================================
@Composable
fun ThreatAuditScreen(viewModel: VaultViewModel) {
    val emergencyLogs by viewModel.emergencyEvents.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF2E5B).copy(alpha = 0.08f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = "Threat analysis active",
                            tint = Color(0xFFFF2E5B),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "INTRUDER BREACH TELEMETRY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFF2E5B)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Failed entries, mock bypass attempts, and biometric bypass signals generate encrypted capture logs containing available GPS positioning coordinates and device hardware models, immediately queued as urgent payloads.",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        item {
            Text(
                text = "INCIDENT AUDIT BLOCKCHAIN HISTORY",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (emergencyLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .background(Color(0xFF11141B), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No perimeter intrusion detections recorded.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            items(emergencyLogs) { log ->
                SecureIncidentRow(log)
            }
        }
    }
}

@Composable
fun SecureIncidentRow(log: EmergencyEvent) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    val displayDate = formatter.format(Date(log.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222D)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Failed Authentication",
                        tint = Color(0xFFFF2E5B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "AUTHENTICATION FAILURE Captured",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            if (log.syncedWithBackend) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFFFF9100).copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (log.syncedWithBackend) "BACKED UP" else "SEND RETRYING",
                        color = if (log.syncedWithBackend) Color(0xFF00E5FF) else Color(0xFFFF9100),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                log.details,
                color = Color.LightGray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Timestamp: $displayDate",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "Log ID: #${log.id}",
                    fontSize = 9.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
