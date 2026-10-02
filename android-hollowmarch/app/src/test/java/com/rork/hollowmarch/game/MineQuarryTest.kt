package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.Biome
import com.rork.hollowmarch.world.Site
import com.rork.hollowmarch.world.SiteKind
import com.rork.hollowmarch.world.WorldGenerator
import com.rork.hollowmarch.world.isSettlement
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The province's diggings: a mine is two faces of one place — a working yard
 * above and a timbered underground beneath, joined by the adit — and a quarry
 * is open ground all the way down. Both are workplaces, not warrens: they are
 * worked by the province's own hands, and nothing hostile is born in them.
 */
class MineQuarryTest {

    private val world = WorldGenerator.generate(777L)

    private fun siteOf(kind: SiteKind): Site =
        world.sites.firstOrNull { it.kind == kind && !it.ruined }
            ?: error("the forge coined no $kind")

    private val mine = siteOf(SiteKind.MINE)
    private val quarry = siteOf(SiteKind.QUARRY)

    private val worksTrades = setOf("Miner", "Quarryman", "Prospector", "Smelter", "Stonemason")

    @Test
    fun theDiggingsStandOnHighGround() {
        // mines and quarries stand where stone and ore belong: hills and the
        // skirts of peaks, never the tilled flats or the marsh
        listOf(mine, quarry).forEach { site ->
            val biome = world.terrain.biomeAt(site.x, site.y)
            assertTrue(
                "${site.name} stands in $biome, not the high ground",
                biome == Biome.HILLS || biome == Biome.PEAK
            )
        }
    }

    @Test
    fun theMineIsTwoFacesOfOnePlace() {
        assertTrue("a mine keeps a lower working", SiteGen.floorCount(world, mine) in 1..2)
        assertEquals("one adit, framed in timber", 1, SiteGen.entranceCount(world, mine))
        val surface = SiteGen.map(world, mine, 0, null, null, 1)
        // the adit is the way down, and the yard has its way out to the province
        assertTrue(
            "the mine surface keeps a way down",
            surface.portals.any { it.down && it.targetFloor == 1 }
        )
        assertTrue(
            "the mine yard keeps its way out",
            surface.portals.any { !it.down && it.targetFloor == OverlandGen.OVERLAND_FLOOR }
        )
        // the underground is a worksite with its way back up
        val deep = SiteGen.map(world, mine, 1, null, null, 1)
        assertFalse("the adit leads under", deep.outdoor)
        assertTrue(
            "the deep level climbs back out",
            deep.portals.any { !it.down && it.targetFloor == 0 }
        )
    }

    @Test
    fun theMineIsAWorkplaceNotAWarren() {
        val deep = SiteGen.map(world, mine, 1, null, null, 1)
        // no wardens, no beasts: hostile hands are never born underground here
        val raiders = deep.entities.filter { it.kind == EntityKind.ENEMY && !it.resident }
        assertTrue("a mine keeps no hostile hands: ${raiders.map { it.name }}", raiders.isEmpty())
        // the working face is dressed: light, spill, ore, gear
        val props = deep.entities.filter { it.kind == EntityKind.PROP }
        assertTrue("braziers light the deep", props.any { it.spriteId == Sprites.BRAZIER })
        assertTrue(
            "ore and spill dress the face",
            props.any { it.name == "ore-bearing rock" } || props.any { it.name == "spill" }
        )
        // a hand or two stays below
        assertTrue(
            "the deep keeps its hands",
            deep.entities.any { it.kind == EntityKind.ENEMY && it.resident }
        )
    }

    @Test
    fun theQuarryIsOpenToTheSky() {
        assertEquals("a quarry digs no lower world", 0, SiteGen.floorCount(world, quarry))
        val surface = SiteGen.map(world, quarry, 0, null, null, 1)
        assertTrue("the quarry stands in the open", surface.outdoor)
        // no way down and no interior: portals only leave for the province
        assertTrue(
            "a quarry keeps no way under",
            surface.portals.none { it.down }
        )
        // the cut itself is dressed: benches, cut blocks, stone piles
        val props = surface.entities.filter { it.kind == EntityKind.PROP }
        assertTrue("cut blocks lie about the floor", props.any { it.name == "cut block" })
        assertTrue("stone piles stand at the rim", props.any { it.name == "stone pile" })
        assertTrue("the works keep their gear", props.any { it.spriteId == Sprites.CART })
    }

    @Test
    fun theWorksKeepTheirHands() {
        listOf(mine, quarry).forEach { site ->
            val surface = SiteGen.map(world, site, 0, null, null, 1)
            val hands = surface.entities.filter { it.kind == EntityKind.ENEMY && it.resident }
            assertTrue(
                "${site.name} is worked: ${hands.size} hands",
                hands.size >= 3
            )
            hands.forEach { hand ->
                assertTrue(
                    "'${hand.role}' is no works trade",
                    hand.role in worksTrades
                )
                assertTrue("the trade is the registry's own", ROLES.isValid(hand.role))
            }
            // nothing hostile walks the works
            assertTrue(
                "${site.name} keeps no raiders",
                surface.entities.none { it.kind == EntityKind.ENEMY && !it.resident }
            )
        }
    }

    @Test
    fun theMineIsTheMineTwiceOver() {
        val a = SiteGen.map(world, mine, 1, null, null, 1)
        val b = SiteGen.map(world, mine, 1, null, null, 1)
        assertTrue("same seed, same workings", a.walls.contentEquals(b.walls))
        assertEquals(
            "same seed, same gear",
            a.entities.map { Triple(it.x, it.y, it.name) },
            b.entities.map { Triple(it.x, it.y, it.name) }
        )
    }

    @Test
    fun theLandRollsUnderTheWorks() {
        // the quarry's cut: a true pit stepped below its rim, the ramp graded down
        val surface = SiteGen.map(world, quarry, 0, null, null, 1)
        assertTrue("the quarry's ground keeps no heights", surface.heights != null)
        val cx = surface.width / 2
        val cy = surface.height / 2
        val floor = (cx - 1..cx + 1).map { surface.heightAt(it + 0.5f, cy + 0.5f) }.average().toFloat()
        val rim = (cx - 2..cx + 2 step 2).map { surface.heightAt(it + 0.5f, cy - 13.5f) }.average().toFloat()
        assertTrue("the cut floor stands at its rim's height", floor < rim - 0.3f)
        val rampTop = surface.heightAt(cx + 0.5f, cy + 8.5f)
        val rampFoot = surface.heightAt(cx + 0.5f, cy + 4.5f)
        assertTrue("the ramp climbs the wrong way", rampFoot < rampTop)
        // the mine's rock stands over its yard, the adit cut back to grade
        val yard = SiteGen.map(world, mine, 0, null, null, 1)
        val lattice = yard.heights
        assertTrue("the mine's ground keeps no heights", lattice != null)
        val vw = yard.width + 1
        fun rowMean(row: Int): Float =
            (0..yard.width).map { vx -> lattice!![row * vw + vx] }.average().toFloat()
        assertTrue("the rock does not stand over the yard", rowMean(6) > rowMean(18) + 0.3f)
        val mx = yard.width / 2
        val mouth = yard.heightAt(mx + 0.5f, 9.5f)
        val road = yard.heightAt(mx + 0.5f, 11.5f)
        assertTrue("the adit is not cut to grade", abs(mouth - road) < 0.25f)
    }

    @Test
    fun everySiteSurfaceStandsOnRollingLand() {
        listOf(
            SiteKind.VAULT, SiteKind.BARROW, SiteKind.RUIN, SiteKind.SHRINE, SiteKind.CAMP
        ).forEach { kind ->
            val site = siteOf(kind)
            val map = SiteGen.map(world, site, 0, null, null, 1)
            val lattice = map.heights
            assertTrue("$kind keeps no heights", lattice != null)
            // the ways through stand level: pads keep every doorway walkable
            val vw = map.width + 1
            map.portals.filter { it.targetFloor != OverlandGen.OVERLAND_FLOOR }.forEach { p ->
                val vx = p.x.toInt()
                val vy = p.y.toInt()
                val hts = lattice!!
                val h00 = hts[vy * vw + vx]
                val level = maxOf(
                    abs(hts[vy * vw + vx + 1] - h00),
                    abs(hts[(vy + 1) * vw + vx] - h00),
                    abs(hts[(vy + 1) * vw + vx + 1] - h00)
                )
                assertTrue(
                    "$kind's door at (${p.x}, ${p.y}) stands on broken ground",
                    level < 0.05f
                )
            }
        }
    }

    @Test
    fun theWorksAreWorkedByTheSettlementsOwnFolk() {
        listOf(mine, quarry).forEach { site ->
            val host = SiteGen.worksHost(world, site)
            assertTrue(
                "the works' host is no living settlement",
                host.isSettlement && !host.ruined && host.population > 0
            )
            assertEquals("a settlement works its own ground", host, SiteGen.worksHost(world, host))
            // deterministic: same province, same host, asked twice over
            assertEquals(host, SiteGen.worksHost(world, site))
        }
        // every hand's heart is dealt from the host settlement's book, not the works'
        val surface = SiteGen.map(world, mine, 0, null, null, 1)
        val host = SiteGen.worksHost(world, mine)
        val keys = (0..4).map { "${host.id}:w$it" } +
            (0..1).map { "${host.id}:u$it" } +
            listOf("${mine.id}:k0")
        surface.entities
            .filter { it.kind == EntityKind.ENEMY && it.resident }
            .forEach { hand ->
                assertTrue(
                    "'${hand.name}' carries a heart the works coined itself",
                    keys.any { tag ->
                        hand.personality == PersonalityBook.deal(
                            PersonalityBook.key(world.seed, tag), hand.role
                        )
                    }
                )
            }
    }
}
