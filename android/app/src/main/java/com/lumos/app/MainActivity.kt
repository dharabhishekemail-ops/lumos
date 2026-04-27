package com.lumos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lumos.designsystem.LumosTheme
import com.lumos.app.nav.LumosNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LumosTheme {
                LumosNavHost()
            }
        }
    }
}
