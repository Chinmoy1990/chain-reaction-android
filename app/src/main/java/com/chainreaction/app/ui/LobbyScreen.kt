package com.chainreaction.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.chainreaction.app.GameViewModel
import com.chainreaction.app.Screen
import com.chainreaction.app.bt.BtLink

private val neededPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
    ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

@Composable
fun LobbyScreen(vm: GameViewModel) {
    val ctx = LocalContext.current
    val adapter = remember { ctx.getSystemService(BluetoothManager::class.java)?.adapter }

    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.goTo(Screen.MENU) }) { Text("← Back") }
            Spacer(Modifier.weight(1f))
            Text("Bluetooth game", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(72.dp))
        }
        Spacer(Modifier.height(16.dp))

        if (adapter == null) {
            Message("This phone doesn't have Bluetooth.")
            return@Column
        }

        var granted by remember {
            mutableStateOf(neededPermissions.all {
                ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
            })
        }
        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { res -> granted = res.values.all { it } }
        var enabled by remember { mutableStateOf(adapter.isEnabled) }
        val enableLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { enabled = adapter.isEnabled }

        LaunchedEffect(Unit) { if (!granted) permLauncher.launch(neededPermissions) }

        when {
            !granted -> {
                Message("Chain Reaction needs the \"Nearby devices\" permission to find and connect to your friend's phone.")
                Button(onClick = { permLauncher.launch(neededPermissions) }) { Text("Allow") }
            }
            !enabled -> {
                Message("Bluetooth is turned off.")
                Button(onClick = {
                    enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }) { Text("Turn on Bluetooth") }
            }
            else -> ReadyLobby(vm, adapter)
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text, textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

@SuppressLint("MissingPermission")
@Composable
private fun ReadyLobby(vm: GameViewModel, adapter: BluetoothAdapter) {
    val status by BtLink.status.collectAsState()
    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    when (val s = status) {
        BtLink.Status.Hosting -> Waiting(
            title = "Waiting for a friend to join…",
            detail = "On the other phone choose \"Join\" and pick\n\"${adapter.name}\".\n\n" +
                "If it doesn't show up there, make this phone visible:",
            onCancel = { BtLink.stop() },
        ) {
            OutlinedButton(onClick = {
                discoverableLauncher.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                        .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120),
                )
            }) { Text("Make this phone visible") }
        }
        is BtLink.Status.Connecting -> Waiting(
            title = "Connecting to ${s.name}…",
            detail = "If a pairing request pops up, accept it on both phones.",
            onCancel = { BtLink.stop() },
        )
        is BtLink.Status.Connected -> Waiting(title = "Connected to ${s.name}!", detail = "Starting game…", onCancel = { BtLink.stop() })
        else -> {
            if (s is BtLink.Status.Lost) {
                Text(s.message, color = Color(0xFFFF8080), textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
            }
            HostSection(vm, adapter)
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            JoinSection(adapter)
        }
    }
}

@Composable
private fun Waiting(title: String, detail: String, onCancel: () -> Unit, extra: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpinningOrbs(90)
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(detail, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Spacer(Modifier.height(16.dp))
        extra()
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun HostSection(vm: GameViewModel, adapter: BluetoothAdapter) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Host", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { BtLink.host(adapter) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Host a game", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun JoinSection(adapter: BluetoothAdapter) {
    val ctx = LocalContext.current
    val paired = remember { runCatching { adapter.bondedDevices.toList() }.getOrDefault(emptyList()) }
    val found = remember { mutableStateListOf<BluetoothDevice>() }
    var scanning by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val d = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            ?: return
                        val name = runCatching { d.name }.getOrNull()
                        if (name != null && found.none { it.address == d.address } && paired.none { it.address == d.address }) {
                            found += d
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> scanning = false
                }
            }
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose {
            ctx.unregisterReceiver(receiver)
            runCatching { adapter.cancelDiscovery() }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Join a friend's game", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Ask your friend to tap \"Host a game\", then tap their phone below:",
            fontSize = 13.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                found.clear()
                scanning = runCatching { adapter.startDiscovery() }.getOrDefault(false)
            },
            enabled = !scanning,
        ) {
            if (scanning) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Scanning…")
            } else Text("Scan for nearby phones")
        }
        Spacer(Modifier.height(8.dp))
        var showAll by remember { mutableStateOf(false) }
        val all = paired + found
        val phones = all.filter(::looksLikePhone)
        val list = if (showAll) all else phones
        val hidden = all.size - phones.size
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (list.isEmpty()) item {
                Text(
                    if (scanning) "Looking for phones…" else "No phones yet. Tap \"Scan for nearby phones\".",
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    textAlign = TextAlign.Center, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
            items(list, key = { it.address }) { d ->
                val isPaired = paired.any { it.address == d.address }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(12.dp))
                        .clickable { BtLink.connect(adapter, d) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(runCatching { d.name }.getOrNull() ?: d.address, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text(
                        if (isPaired) "Paired" else "Nearby", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                }
            }
            if (hidden > 0) item {
                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showAll) "Show phones only" else "Show $hidden other devices (laptops, headphones…)")
                }
            }
        }
    }
}

/** Hide headphones, speakers, laptops etc. Devices that don't report a class are kept. */
@SuppressLint("MissingPermission")
private fun looksLikePhone(d: BluetoothDevice): Boolean = runCatching {
    val major = d.bluetoothClass?.majorDeviceClass ?: return@runCatching true
    major == BluetoothClass.Device.Major.PHONE || major == BluetoothClass.Device.Major.UNCATEGORIZED
}.getOrDefault(true)
