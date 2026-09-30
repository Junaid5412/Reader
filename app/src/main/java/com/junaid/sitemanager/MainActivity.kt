package com.junaid.sitemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.junaid.sitemanager.ui.navigation.AppNav
import com.junaid.sitemanager.ui.theme.SiteManagerTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val app = application as SiteManagerApp
            val themeMode by app.settings.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            SiteManagerTheme(themeMode = themeMode) {
                AppNav()
            }
        }
    }
}
