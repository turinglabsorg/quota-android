package com.turinglabs.quota

import com.turinglabs.quota.model.Formatting
import com.turinglabs.quota.model.Labels
import com.turinglabs.quota.model.Provider
import com.turinglabs.quota.model.UsageLevel
import com.turinglabs.quota.model.UsagePayload
import com.turinglabs.quota.model.UsageWindow
import com.turinglabs.quota.model.WindowKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class UsageTest {
    private val labels = object : Labels {
        override val now = "now"
        override fun daysHours(days: Long, hours: Long) = "${days}d ${hours}h"
        override fun days(days: Long) = "${days}d"
        override fun minutesAgo(minutes: Long) = "$minutes min ago"
        override fun hoursAgo(hours: Long) = "$hours h ago"
    }

    @Test
    fun parsesTheServerPayloadAndSkipsWhatItDoesNotKnow() {
        val payload = UsagePayload.parse(
            """
            {"version":1,"generatedAt":"2026-10-07T08:00:00Z","refreshedAt":"2026-10-07T07:58:00Z","accounts":[
              {"id":"A","provider":"claude","source":"cli","email":"me@example.com","plan":"Team","fetchedAt":"2026-10-07T07:58:00Z",
               "windows":[{"kind":"session","usedPercent":28.4,"resetsAt":"2026-10-07T10:00:00Z"},
                          {"kind":"weeklyModel","model":"Fable","usedPercent":90},
                          {"kind":"custom","minutes":60,"usedPercent":5},
                          {"kind":"daily","usedPercent":50}]},
              {"id":"B","provider":"grok","source":"cli","windows":[],"issue":{"kind":"network","message":"Network unreachable."}},
              {"id":"C","provider":"somethingNew","source":"cli","windows":[]}
            ]}
            """.trimIndent()
        )
        assertEquals(Instant.parse("2026-10-07T07:58:00Z"), payload.refreshedAt)
        assertEquals(listOf(Provider.CLAUDE, Provider.GROK), payload.accounts.map { it.provider })
        val claude = payload.accounts[0]
        assertEquals(listOf(WindowKind.Session, WindowKind.WeeklyModel("Fable"), WindowKind.Custom(60)), claude.windows.map { it.kind })
        assertEquals(Instant.parse("2026-10-07T10:00:00Z"), claude.windows[0].resetsAt)
        assertEquals("Team", claude.plan)
        assertEquals("Network unreachable.", payload.accounts[1].issue?.message)
        assertNull(payload.accounts[1].email)
    }

    @Test
    fun headlineIgnoresModelScopedLimits() {
        val now = Instant.now()
        val account = UsagePayload.sample(now).accounts.first().copy(windows = listOf(
            UsageWindow(WindowKind.Session, 30.0, null),
            UsageWindow(WindowKind.Weekly, 60.0, null),
            UsageWindow(WindowKind.WeeklyModel("Fable"), 95.0, null),
        ))
        assertEquals(WindowKind.Weekly, account.headline(now)?.kind)
        val modelOnly = account.copy(windows = listOf(UsageWindow(WindowKind.WeeklyModel("Fable"), 95.0, null)))
        assertEquals(WindowKind.WeeklyModel("Fable"), modelOnly.headline(now)?.kind)
    }

    @Test
    fun barsStackTheSessionAboveTheLongerWindow() {
        val now = Instant.now()
        val base = UsagePayload.sample(now).accounts.first()
        val session = UsageWindow(WindowKind.Session, 30.0, null)
        val weekly = UsageWindow(WindowKind.Weekly, 60.0, null)
        val monthly = UsageWindow(WindowKind.Monthly, 10.0, null)
        val fable = UsageWindow(WindowKind.WeeklyModel("Fable"), 95.0, null)
        assertEquals(listOf(session, weekly), base.copy(windows = listOf(weekly, fable, session)).barWindows(now))
        assertEquals(listOf(session, monthly), base.copy(windows = listOf(session, monthly)).barWindows(now))
        assertEquals(listOf(weekly), base.copy(windows = listOf(weekly, fable)).barWindows(now))
        assertEquals(emptyList<UsageWindow>(), base.copy(windows = emptyList()).barWindows(now))
    }

    @Test
    fun passedResetsCountAsFullyAvailable() {
        val now = Instant.now()
        assertEquals(100, UsageWindow(WindowKind.Session, 91.6, now.minusSeconds(10)).remainingPercent(now))
        assertEquals(8, UsageWindow(WindowKind.Session, 91.6, null).remainingPercent(now))
    }

    @Test
    fun levelsFollowTheRemainingPercent() {
        assertEquals(UsageLevel.NORMAL, UsageLevel.of(50))
        assertEquals(UsageLevel.WARNING, UsageLevel.of(20))
        assertEquals(UsageLevel.CRITICAL, UsageLevel.of(5))
    }

    @Test
    fun formatsCountdownsAndRelativeTimes() {
        val now = Instant.now()
        assertEquals("2h 10m", Formatting.countdown(now.plus(Duration.ofMinutes(130)).plusSeconds(5), now, labels))
        assertEquals("3d 4h", Formatting.countdown(now.plus(Duration.ofHours(76)).plusSeconds(30), now, labels))
        assertEquals("1m", Formatting.countdown(now.plusSeconds(20), now, labels))
        assertEquals("now", Formatting.countdown(now.minusSeconds(5), now, labels))
        assertEquals("3 min ago", Formatting.relative(now.minusSeconds(200), now, labels))
        assertEquals("2 h ago", Formatting.relative(now.minusSeconds(7_300), now, labels))
    }
}
