package dev.quiettasks

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import dev.quiettasks.ui.TasksApp
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {
    private lateinit var model: TaskViewModel
    private var launchNonce by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[TaskViewModel::class.java]
        setContent { key(launchNonce) { TasksApp(model, intent.getStringExtra("taskId"), intent.getStringExtra("createListId")) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); launchNonce++ }
    override fun onStart() { super.onStart(); if (::model.isInitialized) model.visible(true) }
    override fun onStop() { if (::model.isInitialized) model.visible(false); super.onStop() }
}
