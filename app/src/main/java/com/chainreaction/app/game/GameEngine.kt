package com.chainreaction.app.game

data class Pos(val r: Int, val c: Int)

/** Grid of cells. Each cell has an owner (-1 = empty) and an orb count. */
class Board private constructor(
    val rows: Int,
    val cols: Int,
    private val owners: IntArray,
    private val counts: IntArray,
) {
    constructor(rows: Int, cols: Int) : this(rows, cols, IntArray(rows * cols) { -1 }, IntArray(rows * cols))

    fun owner(r: Int, c: Int) = owners[r * cols + c]
    fun count(r: Int, c: Int) = counts[r * cols + c]

    /** Classic rule: a cell explodes when it holds as many orbs as it has neighbours. */
    fun capacity(r: Int, c: Int): Int {
        var n = 4
        if (r == 0 || r == rows - 1) n--
        if (c == 0 || c == cols - 1) n--
        return n
    }

    fun neighbors(r: Int, c: Int): List<Pos> = buildList {
        if (r > 0) add(Pos(r - 1, c))
        if (r < rows - 1) add(Pos(r + 1, c))
        if (c > 0) add(Pos(r, c - 1))
        if (c < cols - 1) add(Pos(r, c + 1))
    }

    fun orbsOf(player: Int): Int {
        var total = 0
        for (i in owners.indices) if (owners[i] == player) total += counts[i]
        return total
    }

    fun criticalCells(): List<Pos> = buildList {
        for (r in 0 until rows) for (c in 0 until cols) {
            if (count(r, c) >= capacity(r, c)) add(Pos(r, c))
        }
    }

    fun copy() = Board(rows, cols, owners.copyOf(), counts.copyOf())

    internal fun add(r: Int, c: Int, player: Int) {
        val i = r * cols + c
        counts[i]++
        owners[i] = player
    }

    internal fun remove(r: Int, c: Int, n: Int) {
        val i = r * cols + c
        counts[i] -= n
        if (counts[i] <= 0) {
            counts[i] = 0
            owners[i] = -1
        }
    }
}

class GameState(
    val board: Board,
    val players: Int,
    val current: Int,
    /** Moves made by each player; a player can't be eliminated before their first move. */
    val moves: IntArray,
    val eliminated: BooleanArray,
    val winner: Int,
) {
    fun canPlace(r: Int, c: Int): Boolean {
        if (winner >= 0) return false
        if (r !in 0 until board.rows || c !in 0 until board.cols) return false
        val o = board.owner(r, c)
        return o == -1 || o == current
    }

    companion object {
        fun new(rows: Int, cols: Int, players: Int, first: Int = 0) = GameState(
            Board(rows, cols), players, first % players, IntArray(players), BooleanArray(players), -1,
        )
    }
}

/**
 * One step of a chain reaction: every cell in [sources] bursts at the same time.
 * [mid] is the board right after the bursting cells lost their orbs (orbs "in flight"),
 * [after] is the board once those orbs landed on the neighbours.
 */
class Wave(val sources: List<Pos>, val player: Int, val mid: Board, val after: Board)

class MoveResult(
    val player: Int,
    val pos: Pos,
    /** Board right after the orb was placed, before any explosion. */
    val placed: Board,
    val waves: List<Wave>,
    val next: GameState,
)

object Engine {
    private const val MAX_WAVES = 5000

    /** Plays a move for [GameState.current]. Caller must check [GameState.canPlace] first. */
    fun play(s: GameState, pos: Pos): MoveResult {
        require(s.canPlace(pos.r, pos.c)) { "Illegal move $pos" }
        val p = s.current
        var b = s.board.copy()
        b.add(pos.r, pos.c, p)
        val placed = b.copy()
        val moves = s.moves.copyOf().also { it[p]++ }

        val waves = ArrayList<Wave>()
        while (waves.size < MAX_WAVES) {
            // Stop as soon as the mover owns everything, otherwise a full board loops forever.
            if (dominates(b, p, s.players, moves, s.eliminated)) break
            val critical = b.criticalCells()
            if (critical.isEmpty()) break
            val mid = b.copy()
            for (src in critical) mid.remove(src.r, src.c, mid.capacity(src.r, src.c))
            val after = mid.copy()
            for (src in critical) for (n in after.neighbors(src.r, src.c)) after.add(n.r, n.c, p)
            waves += Wave(critical, p, mid, after)
            b = after
        }

        val eliminated = s.eliminated.copyOf()
        for (q in 0 until s.players) {
            if (q != p && !eliminated[q] && moves[q] > 0 && b.orbsOf(q) == 0) eliminated[q] = true
        }
        val alive = (0 until s.players).filter { !eliminated[it] }
        val winner = if (alive.size == 1) alive[0] else -1
        var next = p
        if (winner < 0) {
            do { next = (next + 1) % s.players } while (eliminated[next])
        }
        return MoveResult(p, pos, placed, waves, GameState(b, s.players, next, moves, eliminated, winner))
    }

    private fun dominates(b: Board, p: Int, players: Int, moves: IntArray, eliminated: BooleanArray) =
        (0 until players).all { q -> q == p || eliminated[q] || (moves[q] > 0 && b.orbsOf(q) == 0) }
}
