package com.turinglabs.quota.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.turinglabs.quota.data.QuotaStore
import com.turinglabs.quota.work.RefreshWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // `adb shell am start -n com.turinglabs.quota/.ui.MainActivity --ez sample true` shows sample accounts.
        val store = QuotaStore(this)
        val sample = intent.getBooleanExtra("sample", false)
        if (store.showsSample != sample) {
            store.showsSample = sample
            RefreshWorker.refreshNow(this)
        }
        RefreshWorker.schedule(this)
        enableEdgeToEdge()
        setContent {
            QuotaTheme { QuotaApp() }
        }
    }
}

@Composable
fun QuotaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
