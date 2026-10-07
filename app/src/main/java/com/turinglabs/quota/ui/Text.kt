package com.turinglabs.quota.ui

import android.content.Context
import com.turinglabs.quota.R
import com.turinglabs.quota.data.DisplayMode
import com.turinglabs.quota.data.QuotaError
import com.turinglabs.quota.model.Labels
import com.turinglabs.quota.model.UsageLevel
import com.turinglabs.quota.model.WindowKind
import androidx.compose.ui.graphics.Color
import com.turinglabs.quota.model.Provider

class ResourceLabels(private val context: Context) : Labels {
    override val now: String get() = context.getString(R.string.now)
    override fun daysHours(days: Long, hours: Long) = context.getString(R.string.countdown_days_hours, days.toInt(), hours.toInt())
    override fun days(days: Long) = context.getString(R.string.countdown_days, days.toInt())
    override fun minutesAgo(minutes: Long) = context.getString(R.string.minutes_ago, minutes.toInt())
    override fun hoursAgo(hours: Long) = context.getString(R.string.hours_ago, hours.toInt())
}

fun Context.windowLabel(kind: WindowKind): String = when (kind) {
    WindowKind.Session -> getString(R.string.window_session)
    WindowKind.Weekly -> getString(R.string.window_weekly)
    is WindowKind.WeeklyModel -> getString(R.string.window_weekly_model, kind.model)
    WindowKind.Monthly -> getString(R.string.window_monthly)
    is WindowKind.Custom -> getString(R.string.window_custom, duration(kind.minutes))
}

private fun Context.duration(minutes: Int): String = when {
    minutes % 1_440 == 0 -> getString(R.string.duration_days, minutes / 1_440)
    minutes % 60 == 0 -> getString(R.string.duration_hours, minutes / 60)
    else -> getString(R.string.duration_minutes, minutes)
}

fun Context.suffix(mode: DisplayMode): String =
    getString(if (mode == DisplayMode.REMAINING) R.string.left else R.string.used)

fun Context.message(error: Throwable): String = when (error) {
    QuotaError.NotPaired -> getString(R.string.error_not_paired)
    QuotaError.InvalidCode -> getString(R.string.error_invalid_code)
    QuotaError.ExpiredCode -> getString(R.string.error_expired_code)
    QuotaError.Unauthorized -> getString(R.string.error_unauthorized)
    is QuotaError.Server -> getString(R.string.error_server, error.status)
    QuotaError.Keystore -> getString(R.string.error_keystore)
    QuotaError.InvalidResponse -> getString(R.string.error_invalid_response)
    else -> getString(R.string.error_network)
}

val UsageLevel.color: Color
    get() = when (this) {
        UsageLevel.NORMAL -> Color(0xFF34C759)
        UsageLevel.WARNING -> Color(0xFFFF9500)
        UsageLevel.CRITICAL -> Color(0xFFFF3B30)
    }

val Provider.glyph: Int
    get() = when (this) {
        Provider.CLAUDE -> R.drawable.glyph_claude
        Provider.CODEX -> R.drawable.glyph_codex
        Provider.GROK -> R.drawable.glyph_grok
        Provider.OLLAMA -> R.drawable.glyph_ollama
    }

/** Brand color only for the Claude glyph, as on the Mac and iOS. */
fun Provider.accent(onSurface: Color): Color =
    if (this == Provider.CLAUDE) Color(0xFFD97757) else onSurface
