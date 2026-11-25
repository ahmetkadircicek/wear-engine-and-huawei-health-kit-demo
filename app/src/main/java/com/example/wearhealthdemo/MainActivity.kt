package com.example.wearhealthdemo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.wearhealthdemo.healthkit.HealthKitClient
import com.example.wearhealthdemo.push.WorkoutSubscriptions
import com.example.wearhealthdemo.ui.LoginFreeApp
import com.example.wearhealthdemo.ui.theme.LoginFreeTheme
import com.example.wearhealthdemo.viewmodels.MainViewModel
import com.example.wearhealthdemo.viewmodels.MainViewModelFactory

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The screen is always dark, so keep light bar icons
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        viewModel = ViewModelProvider(
            this,
            MainViewModelFactory(
                healthKitClient = HealthKitClient(this),
                workoutSubscriptions = WorkoutSubscriptions(this),
            ),
        )[MainViewModel::class.java]

        setContent {
            LoginFreeTheme {
                LoginFreeApp(viewModel = viewModel)
            }
        }

        intent?.data?.let(viewModel::handleHealthKitRedirect)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let(viewModel::handleHealthKitRedirect)
    }

    @Deprecated("Kept because the login-free SDK reports its outcome this way.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        viewModel.handleAuthorizationActivityResult(this, requestCode, resultCode, data)
    }
}
