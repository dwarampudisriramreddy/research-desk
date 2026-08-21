package com.ram.researchdesk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
                    var selectedTab by remember { mutableIntStateOf(0) }
                    var selectedYearId by remember { mutableStateOf<String?>(null) }
                    var selectedSubjectId by remember { mutableStateOf<String?>(null) }

                    val hasDesk = selectedYearId != null && selectedSubjectId != null

                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                    label = { Text("Home") },
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Default.School, contentDescription = null) },
                                    label = { Text("Curriculum") },
                                )
                            }
                        },
                    ) { innerPadding ->
                        when (selectedTab) {
                            0 -> {
                                if (hasDesk) {
                                    val vm: DeskViewModel = viewModel(
                                        key = "desk-${selectedYearId!!}-${selectedSubjectId!!}",
                                        factory = DeskViewModel.factory(application, selectedYearId!!, selectedSubjectId!!),
                                    )
                                    DeskScreen(viewModel = vm, modifier = Modifier.padding(innerPadding))
                                } else {
                                    CurriculumPrompt(
                                        modifier = Modifier.padding(innerPadding),
                                        onGoToCurriculum = { selectedTab = 1 },
                                    )
                                }
                            }
                            1 -> {
                                CurriculumScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    onSelectSubject = { yearId, subjectId ->
                                        selectedYearId = yearId
                                        selectedSubjectId = subjectId
                                        selectedTab = 0
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
