package com.openinglab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.openinglab.app.content.BundledContent
import com.openinglab.app.ui.AppViewModel
import com.openinglab.app.ui.OpeningLabApp
import com.openinglab.app.ui.theme.OpeningLabTheme

class MainActivity : ComponentActivity() {
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = viewModelFactory {
            initializer {
                AppViewModel(createSavedStateHandle(),
                    learningStore = (application as OpeningLabApplication).learningStore,
                    packReader = { BundledContent.read(application.assets, it) })
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpeningLabTheme { OpeningLabApp() }
        }
    }
}
