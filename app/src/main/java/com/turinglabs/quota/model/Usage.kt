package com.turinglabs.quota.model

import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

enum class Provider(val key: String, val displayName: String, val shortName: String) {
    CLAUDE("claude", "Claude", "Claude"),
    CODEX("codex", "Codex", "Codex"),
    GROK("grok", "Grok", "Grok"),
    OLLAMA("ollama", "Ollama Cloud", "Ollama");

    companion object {
        fun from(key: String?): Provider? = entries.firstOrNull { it.key == key }
    }
}

sealed interface WindowKind {
    data object Session : WindowKind
    data object Weekly : WindowKind
    data class WeeklyModel(val model: String) : WindowKind
    data object Monthly : WindowKind
    data class Custom(val minutes: Int) : WindowKind
}

data class UsageWindow(val kind: WindowKind, val usedPercent: Double, val resetsAt: Instant?) {
    fun hasReset(now: Instant): Boolean = resetsAt != null && !resetsAt.isAfter(now)

    fun usedPercent(now: Instant): Int = if (hasReset(now)) 0 else usedPercent.coerceIn(0.0, 100.0).roundToInt()

    fun remainingPercent(now: Instant): Int = 100 - usedPercent(now)
}

enum class UsageLevel {
    NORMAL, WARNING, CRITICAL;

    companion object {
        fun of(remainingPercent: Int): UsageLevel = when {
            remainingPercent <= 5 -> CRITICAL
            remainingPercent <= 20 -> WARNING
            else -> NORMAL
        }
    }
}

data class Issue(val kind: String, val message: String)

data class AccountUsage(
    val id: String,
    val provider: Provider,
    val email: String?,
    val plan: String?,
    val fetchedAt: Instant?,
    val windows: List<UsageWindow>,
    val issue: Issue?,
) {
    /** The account-wide window closest to its limit. */
    fun headline(now: Instant): UsageWindow? {
        val accountWide = windows.filter { it.kind !is WindowKind.WeeklyModel }
        return accountWide.ifEmpty { windows }.minByOrNull { it.remainingPercent(now) }
    }

    /**
     * The bars the widget draws, like the Mac menu bar: the 5-hour session above the weekly (or monthly)
     * window when the account reports both, otherwise the window closest to its limit.
     */
    fun barWindows(now: Instant): List<UsageWindow> {
        val session = windows.firstOrNull { it.kind == WindowKind.Session }
        val longer = windows.firstOrNull { it.kind == WindowKind.Weekly } ?: windows.firstOrNull { it.kind == WindowKind.Monthly }
        if (session != null && longer != null) return listOf(session, longer)
        return listOfNotNull(headline(now))
    }
}

/** `GET /v1/usage` from quota-server (version 1). Unknown providers and window kinds are skipped. */
data class UsagePayload(
    val generatedAt: Instant,
    val refreshedAt: Instant?,
    val accounts: List<AccountUsage>,
) {
    companion object {
        fun parse(json: String): UsagePayload {
            val root = JSONObject(json)
            val accounts = root.getJSONArray("accounts")
            return UsagePayload(
                generatedAt = Instant.parse(root.getString("generatedAt")),
                refreshedAt = root.instantOrNull("refreshedAt"),
                accounts = (0 until accounts.length()).mapNotNull { parseAccount(accounts.getJSONObject(it)) },
            )
        }

        private fun parseAccount(account: JSONObject): AccountUsage? {
            val provider = Provider.from(account.stringOrNull("provider")) ?: return null
            val windows = account.optJSONArray("windows")
            val issue = account.optJSONObject("issue")
            return AccountUsage(
                id = account.getString("id"),
                provider = provider,
                email = account.stringOrNull("email"),
                plan = account.stringOrNull("plan"),
                fetchedAt = account.instantOrNull("fetchedAt"),
                windows = if (windows == null) emptyList() else (0 until windows.length()).mapNotNull { parseWindow(windows.getJSONObject(it)) },
                issue = issue?.let { Issue(it.optString("kind"), it.optString("message")) },
            )
        }

        private fun parseWindow(window: JSONObject): UsageWindow? {
            val kind = when (window.stringOrNull("kind")) {
                "session" -> WindowKind.Session
                "weekly" -> WindowKind.Weekly
                "weeklyModel" -> WindowKind.WeeklyModel(window.stringOrNull("model") ?: return null)
                "monthly" -> WindowKind.Monthly
                "custom" -> WindowKind.Custom(if (window.has("minutes")) window.getInt("minutes") else return null)
                else -> return null
            }
            return UsageWindow(kind, window.getDouble("usedPercent").coerceIn(0.0, 100.0), window.instantOrNull("resetsAt"))
        }

        fun sample(now: Instant = Instant.now()): UsagePayload {
            fun account(provider: Provider, plan: String?, vararg windows: UsageWindow) =
                AccountUsage(provider.key, provider, "name@example.com", plan, now, windows.toList(), null)
            fun later(days: Long = 0, hours: Long = 0, minutes: Long = 0) =
                now.plus(Duration.ofDays(days).plusHours(hours).plusMinutes(minutes))
            return UsagePayload(now, now, listOf(
                account(Provider.CLAUDE, "Team",
                    UsageWindow(WindowKind.Session, 28.0, later(hours = 2, minutes = 10)),
                    UsageWindow(WindowKind.Weekly, 64.0, later(days = 3))),
                account(Provider.CODEX, "Plus", UsageWindow(WindowKind.Weekly, 37.0, later(days = 5))),
                account(Provider.GROK, null, UsageWindow(WindowKind.Weekly, 96.0, later(hours = 9))),
                account(Provider.OLLAMA, "Max", UsageWindow(WindowKind.Monthly, 53.0, later(days = 13))),
            ))
        }
    }
}

private fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONObject.instantOrNull(key: String): Instant? =
    stringOrNull(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }
