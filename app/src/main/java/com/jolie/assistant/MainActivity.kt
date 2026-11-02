package com.jolie.assistant

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat

/** Impostazioni salvate sul telefono: sopravvivono alla chiusura dell'app. */
class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("jolie_prefs", Context.MODE_PRIVATE)

    var isSetupCompleted: Boolean
        get() = sp.getBoolean("is_setup_completed", false)
        set(value) { sp.edit().putBoolean("is_setup_completed", value).apply() }

    var language: String
        get() = sp.getString("language", "IT") ?: "IT"
        set(value) { sp.edit().putString("language", value).apply() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val prefs = Prefs(this)
        setContent {
            JolieTheme {
                var setupDone by remember { mutableStateOf(prefs.isSetupCompleted) }
                var lang by remember { mutableStateOf(prefs.language) }
