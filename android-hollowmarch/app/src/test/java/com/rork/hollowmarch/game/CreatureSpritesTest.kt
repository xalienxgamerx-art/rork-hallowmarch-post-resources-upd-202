package com.rork.hollowmarch.game

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The four facings: which board a creature shows the eye, its facing read against the camera. */
class CreatureSpritesTest {

    private fun dot(color: Int) = Sprite(1, 1, intArrayOf(color or (0xFF shl 24)))

    @Test
    fun theFacingAnswersTheEye() {
        Sprites.ensureBuilt()
        val front = dot(0xFF0000.toInt())
        val right = dot(0x00FF00)
        val left = dot(0x0000FF)
        val back = dot(0xFFFFFF)
        Sprites.installCreatureViews(Sprites.PILGRIM, front, right, left, back)
        // it walks as you look: the back
        assertEquals(back, Sprites.viewFor(Sprites.PILGRIM, 0f, 0f))
        // it comes at you: the face
        assertEquals(front, Sprites.viewFor(Sprites.PILGRIM, PI.toFloat(), 0f))
        // its nose to the screen's right, then to the screen's left
        assertEquals(right, Sprites.viewFor(Sprites.PILGRIM, PI.toFloat() / 2f, 0f))
        assertEquals(left, Sprites.viewFor(Sprites.PILGRIM, -PI.toFloat() / 2f, 0f))
    }

    @Test
    fun aCreatureWithoutArtKeepsItsDrawnShape() {
        Sprites.ensureBuilt()
        assertTrue(Sprites.viewFor(Sprites.REEDS, 1f, 2f) === Sprites[Sprites.REEDS])
    }
}
