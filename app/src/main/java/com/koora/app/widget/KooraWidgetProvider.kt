package com.koora.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.koora.app.MainActivity
import com.koora.app.R
import com.koora.app.data.Match
import com.koora.app.ui.isLive
import com.koora.app.ui.scoreLabel

/*
 * Koora home-screen widget (v1.5.5) — live & upcoming scores at a glance.
 *
 * Data: WidgetFeed (the app's own last-good picks, persisted to disk).
 * Refresh: Android's 30-minute floor via updatePeriodMillis PLUS an instant
 * refresh whenever the app itself loads fresh data (WidgetFeed -> pushUpdate).
 * Tapping the widget opens Koora. Never shows invented data — with no data
 * it shows a hint instead.
 */
class KooraWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        renderAll(context, manager, ids)
    }

    companion object {
        /** Refresh every placed widget (called after app data refreshes). */
        fun pushUpdate(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, KooraWidgetProvider::class.java)
                )
                if (ids.isNotEmpty()) renderAll(context, manager, ids)
            }
        }

        internal fun renderAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val picks = WidgetFeed.loadPicks(context)
            val views = RemoteViews(context.packageName, R.layout.koora_widget)

            // Header: red LIVE badge when any picked match is being played
            if (picks.any { isLive(it) }) {
                views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_live))
                views.setViewVisibility(R.id.widget_status, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_status, View.GONE)
            }

            // Empty state: honest hint, never fake scores
            views.setViewVisibility(
                R.id.widget_hint,
                if (picks.isEmpty()) View.VISIBLE else View.GONE
            )

            bindRow(
                views, R.id.widget_row1,
                R.id.widget_home_1, R.id.widget_score_1, R.id.widget_away_1,
                picks.getOrNull(0)
            )
            bindRow(
                views, R.id.widget_row2,
                R.id.widget_home_2, R.id.widget_score_2, R.id.widget_away_2,
                picks.getOrNull(1)
            )
            bindRow(
                views, R.id.widget_row3,
                R.id.widget_home_3, R.id.widget_score_3, R.id.widget_away_3,
                picks.getOrNull(2)
            )
            bindRow(
                views, R.id.widget_row4,
                R.id.widget_home_4, R.id.widget_score_4, R.id.widget_away_4,
                picks.getOrNull(3)
            )

            // Whole widget opens Koora
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)

            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun bindRow(
            views: RemoteViews,
            rowId: Int,
            homeId: Int,
            scoreId: Int,
            awayId: Int,
            match: Match?
        ) {
            if (match == null) {
                views.setViewVisibility(rowId, View.GONE)
                return
            }
            views.setViewVisibility(rowId, View.VISIBLE)
            views.setTextViewText(homeId, match.homeTeam.name)
            views.setTextViewText(awayId, match.awayTeam.name)
            views.setTextViewText(scoreId, scoreLabel(match))
            views.setTextColor(
                scoreId,
                if (isLive(match)) Color.parseColor("#EF4444") else Color.parseColor("#F2F5F9")
            )
        }
    }
}
