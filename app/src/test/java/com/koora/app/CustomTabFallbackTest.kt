package com.koora.app

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koora.app.ui.components.openHighlightInAppCustomTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * v1.9.1 (master spec v5 §6.1) — the Chrome Custom Tab fallback must
 * open YouTube's OFFICIAL watch URL inside Koora's task, with a
 * sanitized video id. Robolectric records the started activity, so the
 * exact intent is verified on the JVM — no device needed.
 *
 * The helper is ALWAYS called with an Activity context in production
 * (LocalContext.current inside a composable = MainActivity) — that is
 * what keeps the Custom Tab inside Koora's own task window, one X away
 * from Koora. The test mirrors exactly that: an Application context
 * would (rightly) throw AndroidRuntimeException, because the intent
 * deliberately carries no FLAG_ACTIVITY_NEW_TASK.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CustomTabFallbackTest {

    private fun launchFromActivity(videoId: String): Pair<ComponentActivity, Intent?> {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        openHighlightInAppCustomTab(activity, videoId)
        return activity to Shadows.shadowOf(activity).nextStartedActivity
    }

    @Test
    fun opensOfficialWatchUrl() {
        val (_, started) = launchFromActivity("dQw4w9WgXcQ")
        assertEquals(Intent.ACTION_VIEW, started!!.action)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", started.data.toString())
    }

    @Test
    fun sanitizesDangerousVideoId() {
        val (_, started) = launchFromActivity("abc<script>alert(1)</script>")
        // Every char outside [A-Za-z0-9_-] is stripped — the URL stays one
        // official watch link, never an injection vector.
        assertEquals(
            "https://www.youtube.com/watch?v=abcscriptalert1script",
            started!!.data.toString()
        )
    }

    @Test
    fun blankIdOpensNothing() {
        val (_, started) = launchFromActivity("***")
        // A blank id after sanitization means there is no official video
        // to hand over — nothing is started, the honest state stays.
        assertNull(started)
    }
}
