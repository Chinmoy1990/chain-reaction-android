package com.chainreaction.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chainreaction.app.bt.BtLink
import com.chainreaction.app.game.Engine
import com.chainreaction.app.game.GameState
import com.chainreaction.app.game.MoveResult
import com.chainreaction.app.game.Pos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { MENU, LOCAL_SETUP, LOBBY, GAME }
enum class Mode { LOCAL, BLUETOOTH }

/** A move waiting to be animated on screen. */
class Playback(val id: Int, val result: MoveResult)

/**
 * Bluetooth protocol (one text line per message):
 *   HELLO|rows|cols|round   host -> joiner, starts the game (host = player 0)
 *   MOVE|r|c                a tap by the sender
 *   RESTART|round           start a new round (round decides who goes first)
 *   BYE                     sender left the game
 * Both phones run the same deterministic engine on the same moves, so boards never drift.
 */
class GameViewModel : ViewModel() {
    var screen by mutableStateOf(Screen.MENU); private set
    var mode by mutableStateOf(Mode.LOCAL); private set
    var rows by mutableIntStateOf(13)
    var cols by mutableIntStateOf(6)
    var playerCount by mutableIntStateOf(2)
    var localPlayer by mutableIntStateOf(0); private set
    var opponentName by mutableStateOf(""); private set
    var state by mutableStateOf(GameState.new(13, 6, 2)); private set
    var playback by mutableStateOf<Playback?>(null); private set
    var notice by mutableStateOf<String?>(null)

    private var round = 0
    private var playbackCounter = 0
    private val queuedRemoteMoves = ArrayDeque<Pos>()

    init {
        viewModelScope.launch { for (line in BtLink.incoming) onMessage(line) }
        viewModelScope.launch { BtLink.status.collect(::onStatus) }
    }

    // ---- navigation -------------------------------------------------------

    fun goTo(s: Screen) {
        if (s == Screen.MENU || s == Screen.LOCAL_SETUP) BtLink.stop()
        screen = s
    }

    fun startLocal() {
        mode = Mode.LOCAL
        round = 0
        newRound()
        screen = Screen.GAME
    }

    fun leaveGame() {
        if (mode == Mode.BLUETOOTH) {
            BtLink.send("BYE")
            viewModelScope.launch { delay(200); BtLink.stop() } // let BYE flush first
        }
        notice = null
        screen = Screen.MENU
    }

    fun playAgain() {
        round++
        if (mode == Mode.BLUETOOTH) BtLink.send("RESTART|$round")
        newRound()
    }

    private fun newRound() {
        val players = if (mode == Mode.BLUETOOTH) 2 else playerCount
        state = GameState.new(rows, cols, players, first = round % players)
        playback = null
        queuedRemoteMoves.clear()
    }

    // ---- moves --------------------------------------------------------------

    /** Player whose turn is shown (stays on the mover while their explosion animates). */
    val shownTurn: Int get() = playback?.result?.player ?: state.current

    val isMyTurn: Boolean
        get() = mode == Mode.LOCAL || state.current == localPlayer

    fun tap(r: Int, c: Int) {
        if (playback != null || state.winner >= 0) return
        if (!isMyTurn || !state.canPlace(r, c)) return
        if (mode == Mode.BLUETOOTH) BtLink.send("MOVE|$r|$c")
        apply(Pos(r, c))
    }

    /** Called by the board once it finished animating the current move. */
    fun playbackDone() {
        playback = null
        queuedRemoteMoves.removeFirstOrNull()?.let(::applyRemote)
    }

    private fun apply(pos: Pos) {
        val result = Engine.play(state, pos)
        state = result.next
        playback = Playback(++playbackCounter, result)
    }

    private fun applyRemote(pos: Pos) {
        if (state.current != 1 - localPlayer || !state.canPlace(pos.r, pos.c)) return
        apply(pos)
    }

    // ---- bluetooth ------------------------------------------------------------

    private fun onStatus(s: BtLink.Status) {
        when (s) {
            is BtLink.Status.Connected -> {
                opponentName = s.name
                if (s.isHost && screen == Screen.LOBBY) {
                    round = 0
                    BtLink.send("HELLO|$rows|$cols|$round")
                    startBluetoothGame(localPlayer = 0)
                }
            }
            is BtLink.Status.Lost ->
                if (screen == Screen.GAME && mode == Mode.BLUETOOTH && state.winner < 0) notice = s.message
            else -> Unit
        }
    }

    private fun onMessage(line: String) {
        val parts = line.split('|')
        when (parts[0]) {
            "HELLO" -> if (screen == Screen.LOBBY && parts.size >= 4) {
                rows = parts[1].toIntOrNull() ?: return
                cols = parts[2].toIntOrNull() ?: return
                round = parts[3].toIntOrNull() ?: 0
                startBluetoothGame(localPlayer = 1)
            }
            "MOVE" -> if (screen == Screen.GAME && parts.size >= 3) {
                val pos = Pos(parts[1].toIntOrNull() ?: return, parts[2].toIntOrNull() ?: return)
                if (playback != null) queuedRemoteMoves.addLast(pos) else applyRemote(pos)
            }
            "RESTART" -> {
                val n = parts.getOrNull(1)?.toIntOrNull() ?: return
                if (n > round) { round = n; newRound() }
            }
            "BYE" -> if (screen == Screen.GAME) {
                notice = "$opponentName left the game."
                BtLink.stop()
            }
        }
    }

    private fun startBluetoothGame(localPlayer: Int) {
        mode = Mode.BLUETOOTH
        this.localPlayer = localPlayer
        notice = null
        newRound()
        screen = Screen.GAME
    }
}
