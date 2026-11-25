package com.example.wearhealthdemo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import com.example.wearhealthdemo.ui.screens.LoginFreeScreen
import com.example.wearhealthdemo.viewmodels.MainViewModel

@Composable
fun LoginFreeApp(viewModel: MainViewModel) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { contentPadding ->
        LoginFreeScreen(viewModel, contentPadding)
    }
}
