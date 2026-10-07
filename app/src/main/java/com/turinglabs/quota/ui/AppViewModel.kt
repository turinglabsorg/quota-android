package com.turinglabs.quota.ui

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.turinglabs.quota.R
import com.turinglabs.quota.data.DisplayMode
import com.turinglabs.quota.data.QuotaClient
import com.turinglabs.quota.data.QuotaError
import com.turinglabs.quota.data.QuotaStore
import com.turinglabs.quota.model.UsagePayload
import com.turinglabs.quota.widget.QuotaWidget
import com.turinglabs.quota.widget.QuotaWidgetReceiver
import com.turinglabs.quota.work.RefreshWorker
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val store = QuotaStore(application)
    private val client = QuotaClient(store)

    var payload by mutableStateOf(if (store.showsSample) UsagePayload.sample() else store.cachedPayload())
        private set
    var isPaired by mutableStateOf(store.showsSample || store.isPaired)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var isPairing by mutableStateOf(false)
        private set
    var pairingError by mutableStateOf<String?>(null)
        private set
    var displayMode by mutableStateOf(store.displayMode)
        private set

    val serverUrl: String? get() = store.serverUrl

    val canPinWidget: Boolean
        get() = AppWidgetManager.getInstance(app).isRequestPinAppWidgetSupported

    fun refresh() {
        if (!isPaired || isLoading || store.showsSample) return
        viewModelScope.launch {
            isLoading = true
            try {
                payload = client.fetchUsage()
                error = null
                QuotaWidget().updateAll(app)
            } catch (failure: QuotaError.Unauthorized) {
                unpair()
                error = app.message(failure)
            } catch (failure: Exception) {
                error = app.message(failure)
            } finally {
                isLoading = false
            }
        }
    }

    fun pair(server: String, code: String) {
        val address = server.trim().let { if (it.contains("://")) it else "https://$it" }
        if (Uri.parse(address).host.isNullOrEmpty()) {
            pairingError = app.getString(R.string.enter_server)
            return
        }
        viewModelScope.launch {
            isPairing = true
            try {
                client.pair(address, code, deviceName())
                pairingError = null
                isPaired = true
                RefreshWorker.schedule(app)
                refresh()
            } catch (failure: Exception) {
                pairingError = app.message(failure)
            } finally {
                isPairing = false
            }
        }
    }

    fun updateDisplayMode(mode: DisplayMode) {
        store.displayMode = mode
        displayMode = mode
        viewModelScope.launch { QuotaWidget().updateAll(app) }
    }

    fun pinWidget() {
        AppWidgetManager.getInstance(app).requestPinAppWidget(ComponentName(app, QuotaWidgetReceiver::class.java), null, null)
    }

    fun unpair() {
        store.unpair()
        isPaired = false
        payload = null
        viewModelScope.launch { QuotaWidget().updateAll(app) }
    }

    private fun deviceName(): String =
        Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL
}
