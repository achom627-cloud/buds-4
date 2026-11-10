package com.achom.buds4

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NoiseAware
import androidx.compose.material.icons.rounded.NoiseControlOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes.blurBehindRadius = 30
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                WebShiftStyleBudsScreen(isPopup = false, onDismiss = {})
            }
        }
    }
}

@Composable
fun WebShiftStyleBudsScreen(isPopup: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val connected by BudsProtocol.connected.collectAsState()
    val name by BudsProtocol.deviceName.collectAsState()
    val ancMode by BudsProtocol.ancMode.collectAsState()
    val battery by BudsProtocol.battery.collectAsState()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        scope.launch { BudsProtocol.connect(null) }
        context.startForegroundService(Intent(context, BudsMonitorService::class.java))
    }

    LaunchedEffect(Unit) {
        val perms = mutableListOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permLauncher.launch(perms.toTypedArray())
    }

    val cardGlass = Color(0xD9141416)
    val cardBorder = Color(0x1AFFFFFF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0x22000000),
                        Color(0x66000000),
                        Color(0xCC050507)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "OnePlus Audio",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = name,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                }
                IconButton(onClick = { scope.launch { BudsProtocol.connect(null) } }) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, tint = Color.White)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardGlass)
                    .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(modifier = Modifier.size(100.dp, 80.dp)) {
                            drawCircle(Color(0xFF28292E), radius = 22.dp.toPx(), center = Offset(25.dp.toPx(), 40.dp.toPx()))
                            drawCircle(Color(0xFF42454F), radius = 18.dp.toPx(), center = Offset(25.dp.toPx(), 40.dp.toPx()))
                            drawCircle(Color(0xFFE2E4EB), radius = 8.dp.toPx(), center = Offset(23.dp.toPx(), 36.dp.toPx()))

                            drawCircle(Color(0xFF28292E), radius = 22.dp.toPx(), center = Offset(75.dp.toPx(), 40.dp.toPx()))
                            drawCircle(Color(0xFF42454F), radius = 18.dp.toPx(), center = Offset(75.dp.toPx(), 40.dp.toPx()))
                            drawCircle(Color(0xFFE2E4EB), radius = 8.dp.toPx(), center = Offset(73.dp.toPx(), 36.dp.toPx()))
                        }

                        Canvas(modifier = Modifier.size(90.dp)) {
                            drawRoundRect(
                                color = Color(0xFF1E2024),
                                size = size,
                                cornerRadius = CornerRadius(32.dp.toPx())
                            )
                            drawOval(
                                color = Color(0xFF121316),
                                topLeft = Offset(8.dp.toPx(), 8.dp.toPx()),
                                size = Size(size.width - 16.dp.toPx(), size.height - 16.dp.toPx())
                            )
                            drawCircle(Color(0xFF56E39F), radius = 3.dp.toPx(), center = Offset(size.width / 2f, size.height - 18.dp.toPx()))
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0x1AFFFFFF),
                                strokeWidth = 5.dp
                            )
                            CircularProgressIndicator(
                                progress = { (battery.left / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFFC6C5D6),
                                strokeWidth = 5.dp,
                                strokeCap = StrokeCap.Round
                            )
                            Text(
                                text = "${battery.left}%",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Заряд комплекса", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardGlass)
                    .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Название", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardGlass)
                    .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column {
                    Text("Режимы звука", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Active Noise Control", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SoundModeButton(
                        title = "Шумоподавление",
                        sub = "ANC",
                        icon = Icons.Rounded.NoiseAware,
                        selected = ancMode == 4,
                        modifier = Modifier.weight(1f),
                        onClick = { scope.launch { BudsProtocol.setAncMode(4) } }
                    )
                    SoundModeButton(
                        title = "Выкл",
                        sub = "Off",
                        icon = Icons.Rounded.NoiseControlOff,
                        selected = ancMode == 1,
                        modifier = Modifier.weight(1f),
                        onClick = { scope.launch { BudsProtocol.setAncMode(1) } }
                    )
                    SoundModeButton(
                        title = "Прозрачность",
                        sub = "Transparency",
                        icon = Icons.Rounded.Hearing,
                        selected = ancMode == 2,
                        modifier = Modifier.weight(1f),
                        onClick = { scope.launch { BudsProtocol.setAncMode(2) } }
                    )
                }
            }
        }
    }
}

@Composable
fun SoundModeButton(
    title: String,
    sub: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(targetValue = if (selected) Color(0xE63C404B) else Color(0xFF1B1C21))
    val tint by animateColorAsState(targetValue = if (selected) Color.White else Color(0xFF8E9199))

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(sub, color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudsPopupSheet(mac: String?, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) {
        BudsProtocol.connect(mac)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = Color(0xF2121215),
        scrimColor = Color.Black.copy(alpha = 0.45f)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(540.dp)) {
            WebShiftStyleBudsScreen(isPopup = true, onDismiss = onDismiss)
        }
    }
}
