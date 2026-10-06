package com.openinglab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.openinglab.app.content.BundledContent
import com.openinglab.app.content.BundledCourses
import com.openinglab.app.analysis.AndroidStockfish
import com.openinglab.app.ui.AppViewModel
import com.openinglab.app.ui.OpeningLabApp
import com.openinglab.app.ui.theme.OpeningLabTheme

class MainActivity : ComponentActivity() {
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = viewModelFactory {
            initializer {
                AppViewModel(createSavedStateHandle(),
                    learningStore = (application as OpeningLabApplication).learningStore,
                    analysisEngine = AndroidStockfish.engine(application),
                    packReader = { BundledContent.read(application.assets, it) },
                    autoInstallBundledOpenings = true,
                    presentationCache = (application as OpeningLabApplication).openingPresentationCache,
                    deepCourseSource = { BundledCourses.read(application.assets) },
                    courseFeedbackStore = (application as OpeningLabApplication).courseFeedbackStore,
                    appPreferences = com.openinglab.app.content.AppPreferences(application.getSharedPreferences("app-settings", android.content.Context.MODE_PRIVATE)),
                    tacticsSource = { com.openinglab.app.tactics.BundledTactics.read(application.assets) },
                    deepCourseValidation = (application as OpeningLabApplication).courseValidation,
                    tacticsValidation = (application as OpeningLabApplication).tacticsValidation,
                    deferObservedReplies = true)
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpeningLabTheme {
                val startup: AppViewModel = viewModel()
                LaunchedEffect(startup) { withFrameNanos { }; withFrameNanos { }; startup.onFirstFrame() }
                OpeningLabApp()
            }
        }
    }
}
