package com.ram.researchdesk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> LlmRuntime.releaseOnBackground()
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
                    var selectedYearId by remember { mutableStateOf<String?>(null) }
                    var selectedSubjectId by remember { mutableStateOf<String?>(null) }
                    var initialQuery by remember { mutableStateOf("") }
                    var webViewUrl by remember { mutableStateOf<String?>(null) }
                    var webViewTitle by remember { mutableStateOf("") }

                    val hasDesk = selectedYearId != null && selectedSubjectId != null

                    if (webViewUrl != null) {
                        WebViewScreen(
                            url = webViewUrl!!,
                            title = webViewTitle,
                            onBack = { webViewUrl = null },
                        )
                    } else {
                        if (hasDesk) {
                            val vm: DeskViewModel = viewModel(
                                key = "desk-${selectedYearId!!}-${selectedSubjectId!!}",
                                factory = DeskViewModel.factory(application, selectedYearId!!, selectedSubjectId!!),
                            )
                            DeskScreen(
                                viewModel = vm,
                                initialQuery = initialQuery,
                                modifier = Modifier,
                                onSaveIdea = {},
                                onOpenUrl = { url, title ->
                                    webViewUrl = url
                                    webViewTitle = title
                                },
                                onBack = {
                                    selectedYearId = null
                                    selectedSubjectId = null
                                    initialQuery = ""
                                },
                            )
                        } else {
                            SubjectPicker(
                                modifier = Modifier,
                                onSelect = { yearId, subjectId, query ->
                                    selectedYearId = yearId
                                    selectedSubjectId = subjectId
                                    initialQuery = query
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
