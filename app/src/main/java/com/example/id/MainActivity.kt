package com.example.id

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.id.ui.absence.AbsenceScreen
import com.example.id.ui.edit.EditLoadingScreen
import com.example.id.ui.edit.EditRefuelScreen
import com.example.id.ui.edit.EditWorkdayScreen
import com.example.id.ui.login.LoginScreen
import com.example.id.ui.manual_input.ManualDataEntryScreen
import com.example.id.ui.manual_input.NewEventScreen
import com.example.id.ui.manual_input.NewWorkdayScreen
import com.example.id.ui.options.OptionsScreen
import com.example.id.ui.reports.ReportsScreen
import com.example.id.ui.summary.SummaryScreen
import com.example.id.ui.theme.IdTheme
import com.example.id.util.DataManager
import com.example.id.util.LocaleManager
import com.example.id.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

// SharedPreferences kulcsok és értékek
const val PREFS_NAME = "MyPrefs"
const val IS_FIRST_LAUNCH_KEY = "is_first_launch"
const val USER_ROLE_KEY = "user_role"
const val USER_NAME_KEY = "user_name"
const val DEFAULT_CAR_KEY = "default_car"
const val IS_INITIAL_SETUP_COMPLETE_KEY = "is_initial_setup_complete"
const val LANGUAGE_KEY = "language_key"

const val ROLE_DRIVER = "driver"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var action = mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lang = prefs.getString(LANGUAGE_KEY, "hu") ?: "hu"
        super.attachBaseContext(LocaleManager.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            setContent { AppContent(application, action) } // application átadása
        }

        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        action.value = intent?.action
    }

    companion object {
        const val ACTION_SHOW_WORK_DIALOG = "com.example.id.ACTION_SHOW_WORK_DIALOG"
        const val ACTION_SHOW_REFUEL_DIALOG = "com.example.id.ACTION_SHOW_REFUEL_DIALOG"
    }
}

@Composable
fun AppContent(application: Application, action: MutableState<String?>) { // application paraméter
    IdTheme {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

        val authToken = prefs.getString("auth_token", null)
        val startDestination = if (authToken == null) "login" else "main"

        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable("login") {
                LoginScreen(navController = navController)
            }
            composable("main") {
                val viewModel: MainViewModel = hiltViewModel()
                val currentUserId = prefs.getString(USER_NAME_KEY, "unknown_user") ?: "unknown_user"
                val currentUserRole = prefs.getString(USER_ROLE_KEY, ROLE_DRIVER) ?: ROLE_DRIVER
                val defaultCar = prefs.getString(DEFAULT_CAR_KEY, null)

                MainScreen(
                    viewModel = viewModel,
                    navController = navController,
                    action = action,
                    userId = currentUserId,
                    userRole = currentUserRole,
                    defaultCar = defaultCar
                )
            }
            composable("summary") {
                val viewModel: MainViewModel = hiltViewModel()
                val summaryText by viewModel.summaryText.collectAsState()
                SummaryScreen(navController = navController, summaryText = summaryText)
            }
            // ... (többi útvonal)
        }
    }
}

// ... (a fájl többi része változatlan)
