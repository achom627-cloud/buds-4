package com.achom.buds4

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.NoiseControlOff
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mac = intent?.getStringExtra("DEVICE_MAC")

        setContent {
            val context = LocalContext.current
            val darkTheme = true
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                darkColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                BudsSheetScreen(
                    mac = mac,
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudsSheetScreen(mac: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val connected by BudsProtocol.connected.collectAsState()
    val name by BudsProtocol.deviceName.collectAsState()
    val ancMode by BudsProtocol.ancMode.collectAsState()
    val ancLevel by BudsProtocol.ancLevel.collectAsState()
    val gameMode by BudsProtocol.gameMode.collectAsState()
    val dualConn by BudsProtocol.dualConn.collectAsState()
    val battery by BudsProtocol.battery.collectAsState()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        scope.launch { BudsProtocol.connect(mac) }
    }

    LaunchedEffect(Unit) {
        permLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Шапка попапа при открытии кейса
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Headphones,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Column {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (connected) "RFCOMM ch15 подключен" else "Нажми для подключения",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }

                FilledTonalIconButton(onClick = { scope.launch { BudsProtocol.connect(mac) } }) {
                    Icon(Icons.Default.BluetoothConnected, contentDescription = "Connect")
                }
            }

            // Карточки заряда L / R / Кейс
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BatteryCard("Левый", battery.left, Modifier.weight(1f))
                BatteryCard("Правый", battery.right, Modifier.weight(1f))
                BatteryCard("Кейс", battery.case, Modifier.weight(1f))
            }

            // Переключатель ANC (cat=04 op=04: 04 / 01 / 02)
            Text(
                "Шумоподавление",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = listOf(
                    Triple(4, "Шумодав", Icons.Default.Headphones),
                    Triple(1, "Выкл", Icons.Default.NoiseControlOff),
                    Triple(2, "Прозрачн.", Icons.Default.Hearing)
                )
                modes.forEachIndexed { index, (code, label, icon) ->
                    SegmentedButton(
                        selected = ancMode == code,
                        onClick = { scope.launch { BudsProtocol.setAncMode(code) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }

            // Глубина шумодава / пресет (cat=06 op=04: 02 / 01 / 00)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val levels = listOf(2 to "Макс (02)", 1 to "Средний (01)", 0 to "Мин (00)")
                levels.forEachIndexed { index, (lvl, title) ->
                    SegmentedButton(
                        selected = ancLevel == lvl,
                        onClick = { scope.launch { BudsProtocol.setAncLevel(lvl) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = levels.size)
                    ) {
                        Text(title)
                    }
                }
            }

            // Быстрые переключатели из твоего лога (cat=00 и cat=01)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SportsEsports, contentDescription = null)
                            Column {
                                Text("Игровой режим (cat=00)", fontWeight = FontWeight.Medium)
                                Text("Минимальная задержка", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Switch(
                            checked = gameMode,
                            onCheckedChange = { scope.launch { BudsProtocol.setGameMode(it) } }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Мультипоинт / Фича 2 (cat=01)", fontWeight = FontWeight.Medium)
                            Text("Два устройства (01 01 01 01 XX)", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = dualConn,
                            onCheckedChange = { scope.launch { BudsProtocol.setDualConnection(it) } }
                        )
                    }
                }
            }

            // Разрешение на показ поверх всех окон при открытии кейса
            if (!Settings.canDrawOverlays(context)) {
                FilledTonalButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Разрешить авто-попап при открытии кейса")
                }
            }
        }
    }
}

@Composable
fun BatteryCard(title: String, percent: Int, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
