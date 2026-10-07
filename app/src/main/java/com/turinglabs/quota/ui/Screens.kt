package com.turinglabs.quota.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.turinglabs.quota.R
import com.turinglabs.quota.data.DisplayMode
import com.turinglabs.quota.model.AccountUsage
import com.turinglabs.quota.model.Formatting
import com.turinglabs.quota.model.UsageLevel
import com.turinglabs.quota.model.UsagePayload
import com.turinglabs.quota.model.UsageWindow
import kotlinx.coroutines.delay
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotaApp(model: AppViewModel = viewModel()) {
    var showingSettings by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quota", fontWeight = FontWeight.Bold) },
                actions = {
                    if (model.isPaired) {
                        IconButton(onClick = model::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                        IconButton(onClick = { showingSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (model.isPaired) {
            UsageList(model, padding)
        } else {
            PairingScreen(model, padding)
        }
    }

    if (showingSettings) {
        SettingsSheet(model, onDismiss = { showingSettings = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsageList(model: AppViewModel, padding: PaddingValues) {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(30_000)
            value = Instant.now()
        }
    }
    PullToRefreshBox(
        isRefreshing = model.isLoading,
        onRefresh = model::refresh,
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            model.error?.let { message ->
                item { IssueLine(message) }
            }
            val payload = model.payload
            if (payload == null) {
                item { Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(payload.accounts, key = { it.id }) { account ->
                    AccountCard(account, now, model.displayMode)
                }
                if (payload.accounts.isEmpty()) {
                    item { Text(stringResource(R.string.no_accounts), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                item { UpdatedFooter(payload, now) }
            }
        }
    }
}

@Composable
private fun AccountCard(account: AccountUsage, now: Instant, mode: DisplayMode) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(account.provider.glyph),
                        contentDescription = null,
                        tint = account.provider.accent(onSurface),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(account.provider.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    account.plan?.let { plan ->
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(50), color = onSurface.copy(alpha = 0.08f)) {
                            Text(
                                plan,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                account.email?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            account.windows.forEach { WindowRow(it, now, mode) }
            account.issue?.let { IssueLine(it.message) }
            if (account.fetchedAt == null && account.issue == null) {
                Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WindowRow(window: UsageWindow, now: Instant, mode: DisplayMode) {
    val context = LocalContext.current
    val remaining = window.remainingPercent(now)
    val value = mode.value(remaining)
    val level = UsageLevel.of(remaining)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(context.windowLabel(window.kind), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                "$value%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (level == UsageLevel.NORMAL) MaterialTheme.colorScheme.onSurface else level.color,
            )
            Spacer(Modifier.width(4.dp))
            Text(context.suffix(mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = level.color,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        window.resetsAt?.let { resetsAt ->
            val text = if (window.hasReset(now)) {
                stringResource(R.string.reset_updating)
            } else {
                stringResource(R.string.resets_in, Formatting.countdown(resetsAt, now, ResourceLabels(context)))
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IssueLine(message: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = UsageLevel.WARNING.color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UpdatedFooter(payload: UsagePayload, now: Instant) {
    val context = LocalContext.current
    val text = payload.refreshedAt?.let {
        stringResource(R.string.updated, Formatting.relative(it, now, ResourceLabels(context)))
    } ?: stringResource(R.string.waiting_for_data)
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun PairingScreen(model: AppViewModel, padding: PaddingValues) {
    var server by rememberSaveable { mutableStateOf(model.serverUrl ?: "") }
    var code by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.pairing_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = server,
            onValueChange = { server = it.trim() },
            label = { Text(stringResource(R.string.server)) },
            placeholder = { Text("https://quota.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(8) },
            label = { Text(stringResource(R.string.pairing_code)) },
            placeholder = { Text("1234 5678") },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { model.pair(server, code) },
            enabled = code.length == 8 && !model.isPairing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (model.isPairing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.pair))
            }
        }
        model.pairingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(model: AppViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.show), style = MaterialTheme.typography.titleSmall)
            DisplayMode.entries.forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().selectable(selected = model.displayMode == mode, onClick = { model.updateDisplayMode(mode) }),
                ) {
                    RadioButton(selected = model.displayMode == mode, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(if (mode == DisplayMode.REMAINING) R.string.percentage_left else R.string.percentage_used))
                }
            }
            HorizontalDivider()
            Text(stringResource(R.string.server), style = MaterialTheme.typography.titleSmall)
            Text(model.serverUrl ?: "-", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (model.canPinWidget) {
                OutlinedButton(onClick = model::pinWidget, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.add_widget))
                }
            }
            TextButton(onClick = { model.unpair(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.unpair), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
