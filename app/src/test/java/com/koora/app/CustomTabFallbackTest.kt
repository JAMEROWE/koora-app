package com.koora.app

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koora.app.ui.components.openHighlightInAppCustomTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * v1.9.1 (master spec v5 §6.1) — the Chrome Custom Tab fallback must
 * open YouTube's OFFICIAL watch URL inside Koora's task, with a
 * sanitized video id. Robolectric records the started activity, so the
 * exact intent is verified on the JVM — no device needed.
 *
 * The fallback fires only when YouTube refuses the inline embed
 * (embeddable=false / geo-block / stall guard) — the official web
 * player keeps all branding and ads intact, the external YouTube app
 * is never involved.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CustomTabFallbackTest {

    @Test
    fun opensOfficialWatchUrl() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        openHighlightInAppCustomTab(app, "dQw4w9WgXcQ")
        val started: Intent = Shadows.shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", started.data.toString())
    }

    @Test
    fun sanitizesDangerousVideoId() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        openHighlightInAppCustomTab(app, "abc<script>alert(1)</script>")
        val started = Shadows.shadowOf(app).nextStartedActivity
        // Every char outside [A-Za-z0-9_-] is stripped — the URL stays one
        // official watch link, never an injection vector.
        assertEquals(
            "https://www.youtube.com/watch?v=abcscriptalert1script",
            started.data.toString()
        )
    }

    @Test
    fun blankIdOpensNothing() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).clearNextStartedActivities()
        openHighlightInAppCustomTab(app, "***")
        assertNull(Shadows.shadowOf(app).nextStartedActivity)
    }
}
