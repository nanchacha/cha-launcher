package com.example.chalauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.chalauncher.theme.ChaLauncherTheme

class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      val themeMode by viewModel.themeMode.collectAsState()
      val isDarkTheme = when (themeMode) {
          ThemeMode.SYSTEM -> isSystemInDarkTheme()
          ThemeMode.LIGHT -> false
          ThemeMode.DARK -> true
      }
      
      ChaLauncherTheme(darkTheme = isDarkTheme) { 
        Surface(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(), 
            color = MaterialTheme.colorScheme.background
        ) { 
          val appState by viewModel.appState.collectAsState()
          
          when (appState) {
              AppState.LOADING -> {
                  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                      CircularProgressIndicator()
                  }
              }
              AppState.SETUP -> {
                  SetupScreen(viewModel)
              }
              AppState.HOME -> {
                  HeatmapScreen(viewModel) 
              }
          }
        } 
      }
    }
  }
}
