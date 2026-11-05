package com.chainreaction.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    @Test
    fun capacities() {
        val b = Board(9, 6)
        assertEquals(2, b.capacity(0, 0))
        assertEquals(3, b.capacity(0, 3))
        assertEquals(4, b.capacity(4, 3))
    }

    @Test
    fun cornerExplodesOnSecondOrb() {
        var s = GameState.new(9, 6, 2)
        s = Engine.play(s, Pos(0, 0)).next      // P0
        s = Engine.play(s, Pos(8, 5)).next      // P1
        val res = Engine.play(s, Pos(0, 0))     // P0 again -> burst
        assertEquals(1, res.waves.size)
        val b = res.next.board
        assertEquals(0, b.count(0, 0))
        assertEquals(1, b.count(0, 1)); assertEquals(0, b.owner(0, 1))
        assertEquals(1, b.count(1, 0)); assertEquals(0, b.owner(1, 0))
    }

    @Test
    fun cannotPlaceOnOpponentCell() {
        var s = GameState.new(9, 6, 2)
        s = Engine.play(s, Pos(4, 4)).next
        assertFalse(s.canPlace(4, 4))
        assertTrue(s.canPlace(4, 3))
    }

    @Test
    fun captureEliminatesAndWins() {
        var s = GameState.new(3, 3, 2)
        s = Engine.play(s, Pos(0, 0)).next   // P0 corner
        s = Engine.play(s, Pos(0, 1)).next   // P1 edge next to it
        s = Engine.play(s, Pos(0, 0)).next   // P0 corner bursts, captures P1's cell
        assertEquals(0, s.winner)
        assertTrue(s.eliminated[1])
    }

    @Test
    fun turnsAlternateAndChainsTerminate() {
        var s = GameState.new(9, 6, 2)
        var guard = 0
        val rnd = java.util.Random(42)
        while (s.winner < 0 && guard++ < 5000) {
            val legal = (0 until 9).flatMap { r -> (0 until 6).map { c -> Pos(r, c) } }.filter { s.canPlace(it.r, it.c) }
            s = Engine.play(s, legal[rnd.nextInt(legal.size)]).next
        }
        assertTrue("game should end", s.winner >= 0)
    }
}
