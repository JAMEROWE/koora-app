package com.koora.app

import com.koora.app.data.StatsEvent
import com.koora.app.ui.formationRows
import com.koora.app.ui.minuteValue
import com.koora.app.ui.pitchBadges
import com.koora.app.ui.playerMatches
import com.koora.app.ui.positionLabel
import com.koora.app.ui.statNumberColor
import com.koora.app.ui.screens.regionAllows
import com.koora.app.ui.screens.verifyDataApiVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.0 roadmap release — pure logic locks (plain JUnit: no Android APIs
 * under test, so no Robolectric needed):
 *
 *  - minuteValue: the 90' timeline positions events by real minute;
 *    anything unparseable must return null (never a guessed position).
 *  - formationRows: the 2D lineup pitch is rendered ONLY from a valid
 *    formation (GK + 10 outfielders); anything else falls back to the
 *    text list so positions are never invented.
 *  - verifyDataApiVideo / regionAllows: the roadmap §3.2 allowlist
 *    checklist — channel allowlist, processed, public, embeddable,
 *    region restriction.
 */
class RoadmapLogicTest {

    // ---------- minuteValue ----------

    @Test
    fun minutesParse_plainAndInjury() {
        assertEquals(46.0, minuteValue("45+1")!!, 0.001)
        assertEquals(93.0, minuteValue("90+3")!!, 0.001)
        assertEquals(90.0, minuteValue("90")!!, 0.001)
        assertEquals(7.0, minuteValue("7")!!, 0.001)
    }

    @Test
    fun minutesParse_toleratesQuoteAndMinuteSuffix() {
        assertEquals(12.0, minuteValue("12'")!!, 0.001)
        assertEquals(45.0, minuteValue("45m")!!, 0.001)
        assertEquals(45.0, minuteValue(" 45 ")!!, 0.001)
    }

    @Test
    fun minutesParse_garbageIsNull_neverGuessed() {
        assertNull(minuteValue(""))
        assertNull(minuteValue("abc"))
        assertNull(minuteValue("+2"))
        assertNull(minuteValue("-5"))
        assertNull(minuteValue("45+-1"))
    }

    // ---------- formationRows ----------

    @Test
    fun formationsParse_commonShapes() {
        assertEquals(listOf(1, 4, 3, 3), formationRows("4-3-3"))
        assertEquals(listOf(1, 4, 2, 3, 1), formationRows("4-2-3-1"))
        assertEquals(listOf(1, 3, 5, 2), formationRows("3-5-2"))
        assertEquals(listOf(1, 5, 4, 1), formationRows(" 5-4-1 "))
    }

    @Test
    fun formationsReject_everythingThePitchCannotTrust() {
        assertNull(formationRows(null))
        assertNull(formationRows(""))
        assertNull(formationRows("442"))            // no separators
        assertNull(formationRows("4-3-4"))          // sums to 11 outfielders
        assertNull(formationRows("4-4"))            // sums to 8
        assertNull(formationRows("4-3-x-1"))        // non-numeric
        assertNull(formationRows("4-3-3-1-1-1"))    // too many rows
        assertNull(formationRows("0-4-4-2"))        // zero row
    }

    // ---------- verifyDataApiVideo (roadmap §3.2) ----------

    private val ok = Verify(
        channelId = "UCG5qGWdu8nIRZqJ_GgDwQ-w",   // Premier League (allowlist)
        uploadStatus = "processed",
        privacyStatus = "public",
        embeddable = true,
        blocked = emptyList(),
        allowed = emptyList(),
        deviceCountry = "MA"
    )

    private data class Verify(
        val channelId: String,
        val uploadStatus: String?,
        val privacyStatus: String?,
        val embeddable: Boolean?,
        val blocked: List<String>,
        val allowed: List<String>,
        val deviceCountry: String
    )

    private fun call(v: Verify) = verifyDataApiVideo(
        v.channelId, v.uploadStatus, v.privacyStatus, v.embeddable,
        v.blocked, v.allowed, v.deviceCountry
    )

    @Test
    fun verifiedVideo_passesAllFiveChecks() {
        assertTrue(call(ok))
    }

    @Test
    fun nonAllowlistedChannel_isRejected_evenIfEverythingElseIsPerfect() {
        // A re-upload channel — exactly what the roadmap bans.
        assertFalse(call(ok.copy(channelId = "UCsomeRipperChannel")))
    }

    @Test
    fun unprocessed_private_or_nonEmbeddable_areRejected() {
        assertFalse(call(ok.copy(uploadStatus = "processing")))
        assertFalse(call(ok.copy(privacyStatus = "unlisted")))
        assertFalse(call(ok.copy(embeddable = false)))
        assertFalse(call(ok.copy(embeddable = null)))
    }

    @Test
    fun regionBlock_rejectsDeviceCountry_only() {
        assertTrue(call(ok.copy(blocked = listOf("US", "CA"))))
        assertFalse(call(ok.copy(blocked = listOf("MA"))))
        // allow-list style: device country must be present
        assertFalse(call(ok.copy(allowed = listOf("US"))))
        assertTrue(call(ok.copy(allowed = listOf("MA", "FR"))))
        // block wins over allow
        assertFalse(call(ok.copy(blocked = listOf("MA"), allowed = listOf("MA"))))
    }

    @Test
    fun unknownDeviceCountry_cannotBeRegionChecked_passes() {
        // YouTube's own player still enforces playback blocks; the check
        // needs a country to have meaning.
        assertTrue(call(ok.copy(deviceCountry = "")))
    }

    @Test
    fun regionAllows_isCaseInsensitive() {
        // "ma" in the block list must block "MA" devices
        assertFalse(regionAllows(listOf("ma"), emptyList(), "MA"))
        assertFalse(regionAllows(listOf("US"), emptyList(), "us"))
        // "FR" in the allow list must admit "fr" devices
        assertTrue(regionAllows(emptyList(), listOf("FR"), "fr"))
    }

    // ---------- v1.9.0: playerMatches (pitch event badges) ----------

    @Test
    fun playerMatches_fullAndSurnameForms() {
        assertTrue(playerMatches("Bukayo Saka", "Bukayo Saka"))
        assertTrue(playerMatches("B. Saka", "Bukayo Saka"))        // abbreviated event text
        assertTrue(playerMatches("Bukayo Saka", "B Saka"))          // "B" dropped (short token)
        assertTrue(playerMatches("Saka", "Bukayo Saka"))            // surname-only event text still matches
        // ...but a DIFFERENT first-name-only form does not:
        assertFalse(playerMatches("Gabriel", "Gabriel Barbosa"))    // same first name, different player
        assertFalse(playerMatches("Cole Palmer", "Bukayo Saka"))
        assertFalse(playerMatches("", "Bukayo Saka"))
    }

    @Test
    fun playerMatches_sameSurnameDistinctPlayers_cannotBothMatch() {
        // Two Haleys in one match are rare; the surname rule is the best
        // honest signal available — but a DIFFERENT surname never matches.
        assertFalse(playerMatches("Jamie Vardy", "Jordan Ayew"))
    }

    // ---------- v1.10.1: pitchBadges (non-colliding on-pitch badges) ----------

    private fun ev(minute: String, kind: String, side: Int = 0) =
        StatsEvent(minute = minute, side = side, kind = kind, detail = "", player = "P", other = null)

    @Test
    fun pitchBadges_underLimit_everythingFitsOnPitch() {
        val two = listOf(ev("23", "goal"), ev("67", "yellow"))
        assertEquals(two, pitchBadges(two))
        assertEquals(2, pitchBadges(two).size)
    }

    @Test
    fun pitchBadges_overLimit_priorityGoalThenCardsThenSub() {
        // A busy starter: 3 goals + 2 yellows + a sub in the record. The
        // pitch slot shows the match story in priority order (goal, red,
        // yellow, sub) and caps at two pills; the events section keeps all.
        val busy = listOf(
            ev("12", "yellow"), ev("40", "sub"), ev("23", "goal"),
            ev("55", "goal"), ev("78", "goal")
        )
        val picked = pitchBadges(busy)
        assertEquals(listOf("23", "55"), picked.map { it.minute })
        assertTrue(picked.all { it.kind == "goal" })
    }

    @Test
    fun pitchBadges_prefersGoalOverYellow_overRedOverSub() {
        val mixed = listOf(ev("30", "sub"), ev("10", "yellow"), ev("60", "red"), ev("5", "goal"))
        val picked = pitchBadges(mixed)
        assertEquals(setOf("goal", "red"), picked.map { it.kind }.toSet())
    }

    @Test
    fun pitchBadges_stableMinutesWithinSameKind() {
        val goals = listOf(ev("7", "goal"), ev("34", "goal"), ev("90", "goal"))
        val picked = pitchBadges(goals)
        // Stable sort: equal priority keeps the PROVIDER's own event order
        // (events arrive chronological) — we never re-order the story.
        assertEquals(listOf("7", "34"), picked.map { it.minute })
    }

    // ---------- v1.9.0: statNumberColor (WCAG-safe numbers) ----------

    private fun lum(c: Int): Double {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return 0.2126 * r / 255.0 + 0.7152 * g / 255.0 + 0.0722 * b / 255.0
    }

    @Test
    fun statNumberColor_darkKitLiftedInDarkTheme() {
        val darkNavy = 0xFF1A2A5E.toInt()
        val lifted = statNumberColor(darkNavy, darkTheme = true)
        assertNotEquals(darkNavy, lifted)
        assertTrue("lifted color must be brighter", lum(lifted) > lum(darkNavy))
    }

    @Test
    fun statNumberColor_whiteKitDarkenedInLightTheme() {
        val white = 0xFFFFFFFF.toInt()
        val darkened = statNumberColor(white, darkTheme = false)
        assertTrue("darkened color must be darker", lum(darkened) < lum(white))
    }

    @Test
    fun statNumberColor_alreadyReadable_isUntouched() {
        val midGreen = 0xFF34D399.toInt()   // bright enough for dark, dark enough for light
        assertEquals(midGreen, statNumberColor(midGreen, darkTheme = true))
        assertEquals(midGreen, statNumberColor(midGreen, darkTheme = false))
    }

    // ---------- v1.9.0: positionLabel (structural, never invented) ----------

    @Test
    fun positionLabel_433() {
        val rows = formationRows("4-3-3")!!      // [1, 4, 3, 3]
        assertEquals("GK", positionLabel(rows, 0))
        assertEquals("DF", positionLabel(rows, 1))
        assertEquals("DF", positionLabel(rows, 4))
        assertEquals("MF", positionLabel(rows, 5))
        assertEquals("FW", positionLabel(rows, 8))
        assertEquals("FW", positionLabel(rows, 10))
    }

    @Test
    fun positionLabel_outOfRange_isSafe() {
        val rows = formationRows("4-3-3")!!
        assertEquals("MF", positionLabel(rows, -1))
        assertEquals("MF", positionLabel(rows, 11))
    }
}
