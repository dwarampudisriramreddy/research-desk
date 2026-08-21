package com.ram.researchdesk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/** Simple app navigation state (no external navigation library). */
sealed class Route {
    data object Home : Route()
    data class Desk(val yearId: String, val subjectId: String) : Route()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Release LLM engine when app goes to background, re-init on foreground
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    LlmRuntime.releaseOnBackground()
                }
                Lifecycle.Event.ON_START -> {
                    if (ModelDownloader.isDownloaded(applicationContext)) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            LlmRuntime.ensureReady(applicationContext)
                        }
                    }
                }
                else -> {}
            }
        })

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var route by remember { mutableStateOf<Route>(Route.Home) }

                    BackHandler(enabled = route is Route.Desk) { route = Route.Home }

                    when (val r = route) {
                        Route.Home -> {
                            HomeScreen(
                                onOpenDesk = { yearId, subjectId ->
                                    route = Route.Desk(yearId, subjectId)
                                },
                            )
                        }

                        is Route.Desk -> {
                            val vm: DeskViewModel = viewModel(
                                key = "desk-${r.yearId}-${r.subjectId}",
                                factory = DeskViewModel.factory(application, r.yearId, r.subjectId),
                            )
                            DeskScreen(viewModel = vm, onBack = { route = Route.Home })
                        }
                    }
                }
            }
        }
    }
}
