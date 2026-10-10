package com.craftflowtechnologies.meetingmind.feature.companionlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Companion Lab (Z-6): a debug-only playground for every form, state, Create mode, size, accent and
 * theme, with a live mic-level slider and the renderer in use (Rive or Canvas). It lives in the
 * debug source set with its own launcher entry, so release builds do not contain it.
 */
class CompanionLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CompanionLabScreen() }
    }
}
