package com.example.id

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.id.ui.login.LoginScreen
import com.example.id.ui.main.MainScreen
import com.example.id.ui.reports.ReportsScreen
import com.example.id.ui.theme.IdTheme
import com.example.id.viewmodel.LoginUiState
import com.example.id.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IdTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = hiltViewModel()
    val loginState by mainViewModel.loginState.collectAsState()

    // Start destination depends on whether the user is already logged in
    val startDestination = if (loginState is LoginUiState.Success) "main" else "login"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            // The LoginScreen now handles its own navigation on success
            LoginScreen(navController = navController, viewModel = mainViewModel)
        }
        composable("main") {
            MainScreen(navController = navController, viewModel = mainViewModel)
        }
        composable("reports") {
            ReportsScreen(navController = navController, viewModel = mainViewModel)
        }
        // Add other composable routes from the old navigation graph here as needed
    }

    // This LaunchedEffect will react to logout
    LaunchedEffect(loginState) {
        if (loginState is LoginUiState.Idle && navController.currentBackStackEntry?.destination?.route != "login") {
            navController.navigate("login") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        }
    }
}
