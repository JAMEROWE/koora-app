package com.koora.app

import com.koora.app.ui.formationRows
import com.koora.app.ui.minuteValue
import com.koora.app.ui.screens.regionAllows
import com.koora.app.ui.screens.verifyDataApiVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(45.2, minuteValue(" 45 ")!!, 0.001)
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
        assertTrue(regionAllows(listOf("ma"), emptyList(), "MA"))
        assertFalse(regionAllows(listOf("US"), emptyList(), "us"))
    }
}
