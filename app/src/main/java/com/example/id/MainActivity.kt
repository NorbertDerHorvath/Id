package com.example.id

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IdTheme {
                PermissionWrapper()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionWrapper() {
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    if (locationPermissionState.status.isGranted) {
        AppNavigation()
    } else {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "A helymeghatározás engedélyezése szükséges az alkalmazás működéséhez.",
                modifier = Modifier.align(Alignment.Center)
            )
            Button(
                onClick = { locationPermissionState.launchPermissionRequest() },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Text("Engedély kérése")
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = hiltViewModel()
    val loginState by mainViewModel.loginState.collectAsState()

    LaunchedEffect(Unit) {
        mainViewModel.synchronizeWithServer()
    }

    val startDestination = if (loginState is LoginUiState.Success) "main" else "login"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(navController = navController, viewModel = mainViewModel)
        }
        composable("main") {
            MainScreen(navController = navController, viewModel = mainViewModel)
        }
        composable("reports") {
            ReportsScreen(navController = navController, viewModel = mainViewModel)
        }
    }

    LaunchedEffect(loginState) {
        if (loginState is LoginUiState.Idle && navController.currentBackStackEntry?.destination?.route != "login") {
            navController.navigate("login") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        }
    }
}
