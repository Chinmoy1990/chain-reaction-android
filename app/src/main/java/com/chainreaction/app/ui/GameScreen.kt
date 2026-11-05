package com.chainreaction.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chainreaction.app.GameViewModel
import com.chainreaction.app.Mode
import com.chainreaction.app.Sounds

@Composable
fun GameScreen(vm: GameViewModel) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { vm.leaveGame() }

    val state = vm.state
    val turn = vm.shownTurn
    val turnColor by animateColorAsState(playerColor(turn), label = "turn")
    val showWinner = state.winner >= 0 && vm.playback == null

    Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.leaveGame() }) { Text("← Leave") }
                Spacer(Modifier.weight(1f))
                Text(turnLabel(vm, turn), color = turnColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { Sounds.toggleMute() }, modifier = Modifier.width(80.dp)) {
                    Text(if (Sounds.muted) "🔇" else "🔊", fontSize = 20.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                for (p in 0 until state.players) PlayerChip(vm, p, isTurn = p == turn)
            }
            BoardView(
                board = state.board,
                playback = vm.playback,
                turnColor = turnColor,
                onTap = vm::tap,
                onPlaybackDone = vm::playbackDone,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 12.dp),
            )
        }

        AnimatedVisibility(
            visible = showWinner,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            WinnerCard(vm)
        }
    }

    vm.notice?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.leaveGame() },
            confirmButton = { Button(onClick = { vm.leaveGame() }) { Text("Back to menu") } },
            title = { Text("Game over") },
            text = { Text(msg) },
        )
    }
}

private fun turnLabel(vm: GameViewModel, turn: Int): String = when {
    vm.state.winner >= 0 && vm.playback == null -> "Game over"
    vm.mode == Mode.BLUETOOTH -> if (turn == vm.localPlayer) "Your turn" else "${vm.opponentName}'s turn"
    else -> "${PlayerNames[turn]}'s turn"
}

private fun playerLabel(vm: GameViewModel, p: Int): String =
    if (vm.mode == Mode.BLUETOOTH) (if (p == vm.localPlayer) "You" else vm.opponentName.take(12)) else PlayerNames[p]

@Composable
private fun PlayerChip(vm: GameViewModel, p: Int, isTurn: Boolean) {
    val out = vm.state.eliminated[p]
    val color = playerColor(p)
    Row(
        Modifier
            .background(Panel, RoundedCornerShape(50))
            .border(2.dp, if (isTurn) color else Color.Transparent, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(14.dp)) { drawOrb(center, size.width * 0.45f, color, if (out) 0.3f else 1f) }
        Spacer(Modifier.width(6.dp))
        Text(
            "${playerLabel(vm, p)} · ${vm.state.board.orbsOf(p)}",
            fontSize = 13.sp,
            color = if (out) Color.Gray else MaterialTheme.colorScheme.onBackground,
            textDecoration = if (out) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun WinnerCard(vm: GameViewModel) {
    val w = vm.state.winner
    val title = when {
        vm.mode == Mode.BLUETOOTH && w == vm.localPlayer -> "You win! 🎉"
        vm.mode == Mode.BLUETOOTH -> "${vm.opponentName} wins"
        else -> "${PlayerNames[w]} wins! 🎉"
    }
    Column(
        Modifier
            .padding(32.dp)
            .background(Panel.copy(alpha = 0.97f), RoundedCornerShape(24.dp))
            .border(2.dp, playerColor(w), RoundedCornerShape(24.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(56.dp)) { drawOrb(center, size.width * 0.38f, playerColor(w)) }
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = playerColor(w))
        Spacer(Modifier.height(20.dp))
        Button(onClick = { vm.playAgain() }, modifier = Modifier.fillMaxWidth()) { Text("Play again") }
        OutlinedButton(onClick = { vm.leaveGame() }, modifier = Modifier.fillMaxWidth()) { Text("Menu") }
    }
}
