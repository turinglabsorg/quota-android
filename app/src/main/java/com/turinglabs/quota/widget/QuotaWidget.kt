package com.turinglabs.quota.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.turinglabs.quota.R
import com.turinglabs.quota.data.DisplayMode
import com.turinglabs.quota.data.QuotaStore
import com.turinglabs.quota.model.AccountUsage
import com.turinglabs.quota.model.Formatting
import com.turinglabs.quota.model.Provider
import com.turinglabs.quota.model.UsageLevel
import com.turinglabs.quota.model.UsagePayload
import com.turinglabs.quota.model.UsageWindow
import com.turinglabs.quota.ui.MainActivity
import com.turinglabs.quota.ui.ResourceLabels
import com.turinglabs.quota.ui.color
import com.turinglabs.quota.ui.glyph
import com.turinglabs.quota.work.RefreshWorker
import java.time.Duration
import java.time.Instant

class QuotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = QuotaWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
        RefreshWorker.refreshNow(context)
    }
}

/** Home screen widget: small (up to four rows) and medium (2 x 2 grid with reset countdowns). */
class QuotaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    private sealed interface State {
        data class Ready(val payload: UsagePayload) : State
        data object NotPaired : State
        data object Unreachable : State
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = QuotaStore(context)
        val state = when {
            store.showsSample -> State.Ready(UsagePayload.sample())
            !store.isPaired -> State.NotPaired
            else -> store.cachedPayload()?.let { State.Ready(it) } ?: State.Unreachable
        }
        val mode = store.displayMode
        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(20.dp)
                        .padding(14.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                ) {
                    when (state) {
                        is State.Ready -> Usage(state.payload, mode)
                        State.NotPaired -> Message(LocalContext.current.getString(R.string.open_to_pair))
                        State.Unreachable -> Message(LocalContext.current.getString(R.string.error_network))
                    }
                }
            }
        }
    }

    private data class Bar(val window: UsageWindow, val remaining: Int) {
        val level get() = UsageLevel.of(remaining)
    }

    /** One account: session above weekly (or monthly) when it reports both, as in the Mac menu bar. */
    private data class Line(val account: AccountUsage, val bars: List<Bar>, val headline: UsageWindow)

    @Composable
    private fun ColumnScope.Usage(payload: UsagePayload, mode: DisplayMode) {
        val now = Instant.now()
        val lines = payload.accounts.mapNotNull { account ->
            val headline = account.headline(now) ?: return@mapNotNull null
            Line(account, account.barWindows(now).map { Bar(it, it.remainingPercent(now)) }, headline)
        }.take(4)
        // The padding keeps a minimum gap; equal-weight spacers spread the remaining height over the rows.
        if (LocalSize.current.width >= MEDIUM.width) {
            lines.chunked(2).forEach { pair ->
                Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    pair.forEachIndexed { index, line ->
                        if (index > 0) Spacer(GlanceModifier.width(14.dp))
                        Column(modifier = GlanceModifier.defaultWeight()) { Tile(line, mode, now) }
                    }
                    if (pair.size == 1) Spacer(GlanceModifier.defaultWeight())
                }
                Spacer(GlanceModifier.defaultWeight())
            }
        } else {
            lines.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Glyph(line.account.provider)
                    Spacer(GlanceModifier.width(5.dp))
                    Name(line.account.provider, GlanceModifier.width(44.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        line.bars.forEachIndexed { index, bar ->
                            if (index > 0) Spacer(GlanceModifier.height(2.dp))
                            BarLine(bar, mode, valueSize = 11, valueWidth = 32)
                        }
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
            }
        }
        Updated(payload, now)
    }

    @Composable
    private fun Tile(line: Line, mode: DisplayMode, now: Instant) {
        val context = LocalContext.current
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Glyph(line.account.provider)
            Spacer(GlanceModifier.width(5.dp))
            Name(line.account.provider, GlanceModifier.defaultWeight())
            val resetsAt = line.headline.resetsAt
            if (resetsAt != null && !line.headline.hasReset(now)) {
                Text(
                    text = "↻ ${Formatting.countdown(resetsAt, now, ResourceLabels(context))}",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                    maxLines = 1,
                )
            }
        }
        line.bars.forEach { bar ->
            Spacer(GlanceModifier.height(3.dp))
            BarLine(bar, mode, valueSize = 13, valueWidth = 40)
        }
    }

    @Composable
    private fun Glyph(provider: Provider) {
        Image(
            provider = ImageProvider(provider.glyph),
            contentDescription = provider.displayName,
            modifier = GlanceModifier.size(12.dp),
            colorFilter = ColorFilter.tint(if (provider == Provider.CLAUDE) ColorProvider(Color(0xFFD97757)) else GlanceTheme.colors.onSurface),
        )
    }

    @Composable
    private fun Name(provider: Provider, modifier: GlanceModifier) {
        Text(
            text = provider.shortName,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            modifier = modifier,
        )
    }

    /** One bar with its value at the end, colored by its own level. */
    @Composable
    private fun BarLine(bar: Bar, mode: DisplayMode, valueSize: Int, valueWidth: Int) {
        val value = mode.value(bar.remaining)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = value / 100f,
                modifier = GlanceModifier.defaultWeight().height(4.dp),
                color = ColorProvider(bar.level.color),
                backgroundColor = ColorProvider(Color(0x22888888)),
            )
            Text(
                text = "$value%",
                style = TextStyle(
                    color = if (bar.level == UsageLevel.NORMAL) GlanceTheme.colors.onSurface else ColorProvider(bar.level.color),
                    fontSize = valueSize.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                ),
                maxLines = 1,
                modifier = GlanceModifier.width(valueWidth.dp),
            )
        }
    }

    @Composable
    private fun Updated(payload: UsagePayload, now: Instant) {
        val context = LocalContext.current
        val refreshed = payload.refreshedAt ?: return
        val stale = Duration.between(refreshed, now) > Duration.ofMinutes(30)
        Text(
            text = context.getString(R.string.updated, Formatting.relative(refreshed, now, ResourceLabels(context))),
            style = TextStyle(
                color = if (stale) ColorProvider(UsageLevel.WARNING.color) else GlanceTheme.colors.onSurfaceVariant,
                fontSize = 10.sp,
            ),
            maxLines = 1,
        )
    }

    @Composable
    private fun Message(text: String) {
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val MEDIUM = DpSize(250.dp, 110.dp)
    }
}
