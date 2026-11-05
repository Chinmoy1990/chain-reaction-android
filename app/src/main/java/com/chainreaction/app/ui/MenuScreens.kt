package com.chainreaction.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chainreaction.app.GameViewModel
import com.chainreaction.app.Screen
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Title() {
    Text(
        "CHAIN\nREACTION",
        style = TextStyle(
            brush = Brush.linearGradient(listOf(PlayerColors[3], PlayerColors[0], PlayerColors[4])),
            fontSize = 44.sp, fontWeight = FontWeight.Black, lineHeight = 46.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        ),
    )
}

/** Three orbiting orbs as a logo. */
@Composable
fun SpinningOrbs(size: Int = 110) {
    val t by rememberInfiniteTransition(label = "logo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "spin",
    )
    Canvas(Modifier.size(size.dp)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val rad = this.size.width * 0.16f
        for (i in 0 until 3) {
            val a = (t + i / 3f) * 2 * PI.toFloat()
            drawOrb(c + Offset(cos(a), sin(a)) * (this.size.width * 0.2f), rad, PlayerColors[i])
        }
    }
}

@Composable
fun MenuScreen(vm: GameViewModel) {
    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpinningOrbs()
        Spacer(Modifier.height(12.dp))
        Title()
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = { vm.goTo(Screen.LOBBY) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Play over Bluetooth", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { vm.goTo(Screen.LOCAL_SETUP) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Play on this phone", fontSize = 18.sp) }
        Spacer(Modifier.height(32.dp))
        Text(
            "Tap an empty cell or one of yours to add an orb. When a cell fills up " +
                "(2 in corners, 3 on edges, 4 inside) it explodes into its neighbours and " +
                "captures them. Wipe out every other colour to win.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun LocalSetupScreen(vm: GameViewModel) {
    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Pass & play", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        Text("Players")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { if (vm.playerCount > 2) vm.playerCount-- }) { Text("−", fontSize = 20.sp) }
            Text("${vm.playerCount}", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
            OutlinedButton(onClick = { if (vm.playerCount < 8) vm.playerCount++ }) { Text("+", fontSize = 20.sp) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(vm.playerCount) { p ->
                Canvas(Modifier.size(22.dp)) { drawOrb(center, size.width * 0.4f, playerColor(p)) }
            }
        }
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = { vm.startLocal() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Start", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        TextButton(onClick = { vm.goTo(Screen.MENU) }) { Text("Back") }
    }
}
