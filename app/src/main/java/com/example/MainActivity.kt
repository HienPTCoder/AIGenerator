package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.repository.PortraitRepository
import com.example.ui.screens.AppNavigationUI
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PortraitViewModel
import com.example.ui.viewmodel.PortraitViewModelFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Instantiate Local Cache Database and Controller Components
    val database = Room.databaseBuilder(
      applicationContext,
      AppDatabase::class.java,
      "ai_portrait_generator.db"
    ).build()

    val repository = PortraitRepository(applicationContext, database)
    val factory = PortraitViewModelFactory(repository)
    val viewModel = ViewModelProvider(this, factory)[PortraitViewModel::class.java]

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        AppNavigationUI(viewModel)
      }
    }
  }
}

