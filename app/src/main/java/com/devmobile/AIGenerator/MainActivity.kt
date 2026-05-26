package com.devmobile.AIGenerator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.devmobile.AIGenerator.data.local.AppDatabase
import com.devmobile.AIGenerator.data.repository.PortraitRepository
import com.devmobile.AIGenerator.ui.screens.AppNavigationUI
import com.devmobile.AIGenerator.ui.theme.MyApplicationTheme
import com.devmobile.AIGenerator.ui.viewmodel.PortraitViewModel
import com.devmobile.AIGenerator.ui.viewmodel.PortraitViewModelFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

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

