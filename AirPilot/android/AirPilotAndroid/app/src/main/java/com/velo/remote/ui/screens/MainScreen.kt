package com.velo.remote.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velo.remote.data.model.DiscoveredServer
import com.velo.remote.network.ConnectionState
import com.velo.remote.ui.theme.*
import com.velo.remote.ui.viewmodel.VeloViewModel

@Composable
fun MainScreen(viewModel: VeloViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val discoveredServers by viewModel.discoveredServers.collectAsState()

    var manualIpDialog by remember { mutableStateOf(false) }
    var manualIpText by remember { mutableStateOf("") }
    var aboutDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        when (val state = connectionState) {
            is ConnectionState.Disconnected -> {
                DiscoveryView(
                    servers = discoveredServers,
                    isPaired = { viewModel.isServerPaired(it) },
                    onConnect = { viewModel.connectToServer(it) },
                    onManualConnect = { manualIpDialog = true },
                    onOpenAbout = { aboutDialog = true }
                )
            }
            is ConnectionState.Connecting -> {
                ConnectingView(server = state.server, onCancel = { viewModel.disconnect() })
            }
            is ConnectionState.PairingRequired -> {
                PairingDialog(
                    server = state.server,
                    error = state.error,
                    onSubmitPin = { viewModel.submitPin(it) },
                    onCancel = { viewModel.disconnect() }
                )
            }
            is ConnectionState.Connected -> {
                ConnectedControllerView(
                    server = state.server,
                    viewModel = viewModel,
                    onOpenAbout = { aboutDialog = true }
                )
            }
            is ConnectionState.Reconnecting -> {
                ReconnectingView(
                    server = state.server,
                    attempt = state.attempt,
                    onCancel = { viewModel.disconnect() }
                )
            }
        }

        if (manualIpDialog) {
            AlertDialog(
                onDismissRequest = { manualIpDialog = false },
                title = { Text("Manual IP Connect", color = TextPrimary) },
                text = {
                    Column {
                        Text("Enter the Velo Desktop receiver's LAN IP address:", color = TextSecondary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = manualIpText,
                            onValueChange = { manualIpText = it },
                            placeholder = { Text("e.g. 192.168.1.100") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            manualIpDialog = false
                            if (manualIpText.isNotBlank()) {
                                viewModel.connectToServer(
                                    DiscoveredServer(
                                        computerName = "Manual PC",
                                        hostAddress = manualIpText.trim(),
                                        port = 51821,
                                        os = "Windows",
                                        status = "Available"
                                    )
                                )
                            }
                        }
                    ) {
                        Text("Connect")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { manualIpDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }

        if (aboutDialog) {
            AboutDialog(onDismiss = { aboutDialog = false })
        }
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = SurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(CyanPrimary)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "About Velo",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Ultra-responsive PC control, trackpad, and presentation pointer from your phone.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = CyanPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = SurfaceLighter)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "ACKNOWLEDGMENTS & CREDITS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldAccent,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Explicitly crediting darusc and the original Droid Studio / Mousedroid project (https://github.com/darusc/Mousedroid) for the foundational architecture and concept that inspired Velo.",
                    fontSize = 12.sp,
                    color = TextPrimary,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Velo builds upon this open-source heritage with a modern .NET 10 WPF receiver, Jetpack Compose Material 3 UI, zero-config UDP discovery, and virtual laser overlay.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Version: Velo Remote v1.0 • Android (com.velo.remote)",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
            ) {
                Text("Close", color = BgDark, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun DiscoveryView(
    servers: List<DiscoveredServer>,
    isPaired: (DiscoveredServer) -> Boolean,
    onConnect: (DiscoveredServer) -> Unit,
    onManualConnect: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // App Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(CyanPrimary)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "VELO",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "REMOTE",
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                color = CyanPrimary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenAbout) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "About & Credits",
                    tint = TextSecondary
                )
            }
        }

        // Section Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(
                text = "NEARBY COMPUTERS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = CyanPrimary
            )
        }

        if (servers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Searching for Velo Receivers...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ensure Velo Desktop is running on your Windows PC and both devices are on the same Wi-Fi.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(onClick = onManualConnect) {
                        Text("Connect by IP Manually", color = CyanPrimary)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(servers) { server ->
                    ServerCard(
                        server = server,
                        isPaired = isPaired(server),
                        onConnect = { onConnect(server) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onManualConnect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enter IP Manually", color = TextSecondary)
            }
        }
    }
}

@Composable
fun ServerCard(
    server: DiscoveredServer,
    isPaired: Boolean,
    onConnect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceLighter),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null,
                    tint = if (isPaired) EmeraldAccent else CyanPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = server.computerName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (isPaired) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(EmeraldAccent.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PAIRED",
                                color = EmeraldAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(EmeraldAccent)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${server.os} • ${server.hostAddress}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPaired) EmeraldAccent else CyanPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (isPaired) "Connect" else "Pair",
                    color = BgDark,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PairingDialog(
    server: DiscoveredServer,
    error: String?,
    onSubmitPin: (String) -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        if (error != null) {
            isSubmitting = false
        }
    }

    LaunchedEffect(isSubmitting) {
        if (isSubmitting) {
            kotlinx.coroutines.delay(7000L)
            if (isSubmitting) {
                isSubmitting = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(18.dp),
        containerColor = SurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = CyanPrimary)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Pair with ${server.computerName}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Enter the 6-digit code shown in Velo Desktop on your Windows screen:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        if (digits.length <= 6) {
                            pin = digits
                        }
                    },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        letterSpacing = 6.sp,
                        color = CyanPrimary
                    ),
                    placeholder = {
                        Text(
                            "000000",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            letterSpacing = 6.sp,
                            color = TextMuted,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            if (pin.length == 6 && !isSubmitting) {
                                isSubmitting = true
                                onSubmitPin(pin)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = RoseError,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length == 6 && !isSubmitting) {
                        isSubmitting = true
                        onSubmitPin(pin)
                    }
                },
                enabled = pin.length == 6 && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = BgDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pairing...", color = BgDark, fontWeight = FontWeight.Bold)
                } else {
                    Text("Pair", color = BgDark, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun ConnectingView(server: DiscoveredServer, onCancel: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = CyanPrimary)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Connecting to ${server.computerName}...",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = onCancel) {
                Text("Cancel", color = TextSecondary)
            }
        }
    }
}

@Composable
fun ReconnectingView(server: DiscoveredServer, attempt: Int, onCancel: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = CyanPrimary)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Reconnecting to ${server.computerName}...",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Attempt #$attempt",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = onCancel) {
                Text("Disconnect", color = RoseError)
            }
        }
    }
}

@Composable
fun ConnectedControllerView(
    server: DiscoveredServer,
    viewModel: VeloViewModel,
    onOpenAbout: () -> Unit
) {
    var selectedMode by remember { mutableStateOf("TRACKPAD") }
    var sensitivity by remember { mutableFloatStateOf(1.5f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Connected Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "VELO",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = CyanPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(EmeraldAccent)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Connected to ${server.computerName}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            IconButton(onClick = onOpenAbout) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "About Velo",
                    tint = TextSecondary
                )
            }

            IconButton(onClick = { viewModel.disconnect() }) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Disconnect",
                    tint = RoseError
                )
            }
        }

        // Mode Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            val modes = listOf("TRACKPAD", "PRESENTATION", "POINTER", "KEYBOARD", "MEDIA")
            modes.forEach { mode ->
                val isActive = mode == selectedMode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) CyanPrimary else Color.Transparent)
                        .clickable { selectedMode = mode }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = mode,
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isActive) BgDark else TextSecondary
                    )
                }
            }
        }

        // Active Mode Canvas
        when (selectedMode) {
            "TRACKPAD" -> {
                TrackpadSurface(
                    sensitivity = sensitivity,
                    onMove = { dx, dy -> viewModel.sendMouseMove(dx.toDouble(), dy.toDouble()) },
                    onClick = { button -> viewModel.sendMouseClick(button) },
                    onScroll = { dx, dy -> viewModel.sendMouseScroll(dx.toDouble(), dy.toDouble()) }
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$selectedMode mode active in Velo Remote",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun TrackpadSurface(
    sensitivity: Float,
    onMove: (Float, Float) -> Unit,
    onClick: (String) -> Unit,
    onScroll: (Float, Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Touch Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceDark)
                .pointerInput(sensitivity) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onMove(dragAmount.x * sensitivity, dragAmount.y * sensitivity)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick("left") },
                        onDoubleTap = {
                            onClick("left")
                            onClick("left")
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "VELO TOUCHPAD SURFACE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Slide 1 finger to move cursor • Tap for left click",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tactile Left & Right Click Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onClick("left") },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Text("LEFT CLICK", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            Button(
                onClick = { onClick("right") },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Text("RIGHT CLICK", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }
    }
}
