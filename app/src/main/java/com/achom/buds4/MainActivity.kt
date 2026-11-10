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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NoiseAware
import androidx.compose.material.icons.rounded.NoiseControlOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes.blurBehindRadius = 28
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
    val eqMode by BudsProtocol.eqMode.collectAsState()
    val findMyBuds by BudsProtocol.findMyBuds.collectAsState()
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
    val cardBorder = Color(0x1FFFFFFF)
    val activeRowColor = Color(0xE63C404B)
    val barFillColor = Color(0xFFC6C5D6)

    val eqTitle = when (eqMode) {
        1 -> "EQ: Серенада"
        2 -> "EQ: Басы"
        else -> "EQ: Сбалансир."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0x33000000),
                        Color(0x990A0A0C),
                        Color(0xE60A0A0C)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // 1. Заголовок и стеклянные пилюли (Статус + Эквалайзер + Поиск наушников)
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "OnePlus Audio",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = name,
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Пилюля подключения
                    Surface(
                        shape = CircleShape,
                        color = Color(0xCC1E1F24),
                        modifier = Modifier.clickable { scope.launch { BudsProtocol.connect(null) } }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (connected) Color.White else Color(0xFF8E9199))
                            )
                            Text(
                                text = if (connected) "В сети" else "Коннект",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Пилюля смены Эквалайзера (Сбалансированный -> Серенада -> Басы)
                    Surface(
                        shape = CircleShape,
                        color = Color(0xCC141416),
                        modifier = Modifier.clickable {
                            val nextEq = (eqMode + 1) % 3
                            scope.launch { BudsProtocol.setEqMode(nextEq) }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = eqTitle,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Пилюля Поиска наушников (Вкл/Выкл пищалку)
                    Surface(
                        shape = CircleShape,
                        color = if (findMyBuds) Color(0xFFE5534B) else Color(0xCC141416),
                        modifier = Modifier.clickable {
                            scope.launch { BudsProtocol.setFindMyBuds(!findMyBuds) }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (findMyBuds) "Стоп" else "Поиск",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. Карточка батареи со столбиками
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(cardGlass)
                    .border(1.dp, cardBorder, RoundedCornerShape(28.dp))
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Equalizer,
                                contentDescription = null,
                                tint = Color(0xFFC6C5D6),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Заряд наушников",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Л ${battery.left}% · П ${battery.right}%",
                            color = Color(0xFFB5B7C0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(104.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        BatteryBarItem("Левый", "${battery.left}%", battery.left, barFillColor)
                        BatteryBarItem("Правый", "${battery.right}%", battery.right, barFillColor)
                        BatteryBarItem(
                            "Кейс",
                            battery.case?.let { "$it%" } ?: "Закрыт",
                            battery.case ?: 100,
                            barFillColor.copy(alpha = if (battery.case != null) 1f else 0.35f)
                        )
                    }
                }
            }

            // 3. Склеенный список режимов (4 = Шумодав, 2 = Прозрачность, 1 = Выкл)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val modes = listOf(
                    Triple(4, "Шумоподавление", "ANC · максимальная тишина"),
                    Triple(2, "Прозрачность", "Passthrough · слышно окружение"),
                    Triple(1, "Выключено", "Off · без обработки звука")
                )
                modes.forEachIndexed { index, (code, title, sub) ->
                    val shape = when (index) {
                        0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
                        modes.lastIndex -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
                        else -> RoundedCornerShape(6.dp)
                    }
                    val selected = ancMode == code
                    val rowBg by animateColorAsState(
                        targetValue = if (selected) activeRowColor else cardGlass,
                        animationSpec = tween(220)
                    )
                    val icon = when (code) {
                        4 -> Icons.Rounded.NoiseAware
                        2 -> Icons.Rounded.Hearing
                        else -> Icons.Rounded.NoiseControlOff
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(rowBg)
                            .border(1.dp, cardBorder, shape)
                            .clickable { scope.launch { BudsProtocol.setAncMode(code) } }
                            .padding(horizontal = 18.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ScallopedBadgeIcon(icon = icon, selected = selected)

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (selected) "· активен" else "",
                                    color = Color(0xFFD0D4E4),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = sub,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 4. Светлая капсула с живой синусоидой (клик переключает режим по кругу, долгий/если нет оверлея — открывает разрешение)
            WaveCapsule(
                ancMode = ancMode,
                onCycleMode = {
                    if (!Settings.canDrawOverlays(context)) {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        )
                    } else {
                        val next = when (ancMode) {
                            4 -> 2
                            2 -> 1
                            else -> 4
                        }
                        scope.launch { BudsProtocol.setAncMode(next) }
                    }
                }
            )
        }
    }
}

@Composable
fun BatteryBarItem(label: String, valueText: String, percent: Int, barColor: Color) {
    val clamped = percent.coerceIn(10, 100)
    val animatedFrac by animateFloatAsState(
        targetValue = clamped / 100f,
        animationSpec = tween(500)
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = Modifier.fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .weight(1f),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animatedFrac)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(barColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(Color(0xFFEDE8FF))
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$label · $valueText",
            color = Color(0xFFB5B7C0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ScallopedBadgeIcon(icon: ImageVector, selected: Boolean) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
        if (selected) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path()
                val cx = size.width / 2f
                val cy = size.height / 2f
                val outerR = size.minDimension / 2f
                val innerR = outerR * 0.84f
                val points = 24
                for (i in 0 until points) {
                    val angle = (i * 2.0 * PI / points)
                    val r = if (i % 2 == 0) outerR else innerR
                    val x = cx + (r * cos(angle)).toFloat()
                    val y = cy + (r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color = Color(0xFFDCE1EC))
            }
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFF1E2028),
                modifier = Modifier.size(22.dp)
            )
        } else {
            Surface(
                shape = CircleShape,
                color = Color(0xFF28292E),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color(0xFFA0A3AD),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WaveCapsule(ancMode: Int, onCycleMode: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val targetAmp = when (ancMode) {
        4 -> 0.18f // Шумодав гасит волну
        2 -> 1.0f  // Прозрачность раскачивает волну
        else -> 0.55f
    }
    val ampFactor by animateFloatAsState(targetValue = targetAmp, animationSpec = tween(400), label = "amp")

    Surface(
        shape = RoundedCornerShape(36.dp),
        color = Color(0xD9D5DAE6),
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(36.dp))
            .clickable { onCycleMode() }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp)
        ) {
            val path = Path()
            val midY = size.height / 2f
            val maxAmp = size.height * 0.22f * ampFactor
            val waveLength = size.width / 8.5f
            val steps = 160
            for (i in 0..steps) {
                val x = (i.toFloat() / steps) * size.width
                val y = midY + sin((x / waveLength) * (2f * PI.toFloat()) + phase) * maxAmp
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = Color(0xFF262932),
                style = Stroke(width = 4.8.dp.toPx(), cap = StrokeCap.Round)
            )
        }
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
