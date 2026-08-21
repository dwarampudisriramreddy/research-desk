package com.ram.researchdesk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FavoriteBorder
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
                    val savedIdeas = remember { mutableStateOf<List<ProjectIdea>>(emptyList()) }

                    val hasDesk = selectedYearId != null && selectedSubjectId != null

                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                                    label = { Text("Desk") },
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Default.FavoriteBorder, contentDescription = null) },
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
                                    DeskScreen(
                                        viewModel = vm,
                                        modifier = Modifier.padding(innerPadding),
                                        onSaveIdea = { idea ->
                                            if (savedIdeas.value.none { it.title == idea.title }) {
                                                savedIdeas.value = savedIdeas.value + idea
                                            }
                                        },
                                    )
                                } else {
                                    SubjectPicker(
                                        modifier = Modifier.padding(innerPadding),
                                        onSelect = { yearId, subjectId ->
                                            selectedYearId = yearId
                                            selectedSubjectId = subjectId
                                        },
                                    )
                                }
                            }
                            1 -> {
                                SavedIdeasScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    savedIdeas = savedIdeas.value,
                                    onRemove = { idea ->
                                        savedIdeas.value = savedIdeas.value.filter { it.title != idea.title }
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
