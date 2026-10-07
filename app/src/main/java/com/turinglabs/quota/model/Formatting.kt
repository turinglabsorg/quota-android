package com.turinglabs.quota.model

import java.time.Duration
import java.time.Instant

/** Localized words the formatters need; the app reads them from string resources. */
interface Labels {
    val now: String
    fun daysHours(days: Long, hours: Long): String
    fun days(days: Long): String
    fun minutesAgo(minutes: Long): String
    fun hoursAgo(hours: Long): String
}

object Formatting {
    /** `2h 10m`, `3d 4h`, `12m`: the same countdown as the Mac and iOS apps. */
    fun countdown(to: Instant, now: Instant, labels: Labels): String {
        val seconds = Duration.between(now, to).seconds
        if (seconds <= 0) return labels.now
        val days = seconds / 86_400
        val hours = (seconds % 86_400) / 3_600
        val minutes = (seconds % 3_600) / 60
        return when {
            days > 0 -> if (hours > 0) labels.daysHours(days, hours) else labels.days(days)
            hours > 0 -> if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
            else -> "${maxOf(minutes, 1)}m"
        }
    }

    fun relative(date: Instant, now: Instant, labels: Labels): String {
        val seconds = Duration.between(date, now).seconds
        return when {
            seconds < 60 -> labels.now
            seconds < 3_600 -> labels.minutesAgo(seconds / 60)
            else -> labels.hoursAgo(seconds / 3_600)
        }
    }
}
