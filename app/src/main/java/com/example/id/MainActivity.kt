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
import androidx.lifecycle.viewmodel.compose.viewModel // Import viewModel composable
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
import com.example.id.viewmodel.MainViewModelFactory // Import the factory
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

        // Read initial values from SharedPreferences
        val initialIsFirstLaunch = prefs.getBoolean(IS_FIRST_LAUNCH_KEY, true)
        val initialUserRole = prefs.getString(USER_ROLE_KEY, null)
        val initialIsInitialSetupComplete = prefs.getBoolean(IS_INITIAL_SETUP_COMPLETE_KEY, false)
        val initialUserName = prefs.getString(USER_NAME_KEY, null)
        val initialDefaultCar = prefs.getString(DEFAULT_CAR_KEY, null)

        val isFirstLaunch = remember { mutableStateOf(initialIsFirstLaunch) }
        val userRole = remember { mutableStateOf(initialUserRole) }
        val isInitialSetupComplete = remember { mutableStateOf(initialIsInitialSetupComplete) }
        val userName = remember { mutableStateOf(initialUserName) }
        val defaultCar = remember { mutableStateOf(initialDefaultCar) }


        LaunchedEffect(Unit) {
            Log.d("AppContent", "Initial state: isFirstLaunch=${isFirstLaunch.value}, userRole=${userRole.value}, isInitialSetupComplete=${isInitialSetupComplete.value}, userName=${userName.value}, defaultCar=${defaultCar.value}")
        }

        val navController = rememberNavController()

        // ViewModel példányosítása a Factory segítségével
        val app = application as App
        val dataManager = remember { DataManager(application) }
        val currentUserId = userName.value ?: "unknown_user" // Fallback user ID
        val currentUserRole = userRole.value ?: ROLE_DRIVER // Fallback role
        val viewModel: MainViewModel = viewModel(
            key = "$currentUserId-$currentUserRole", // Recreate ViewModel on user/role change
            factory = MainViewModelFactory(app, app.repository, currentUserId, currentUserRole, dataManager)
        )

        // Determine start destination based on first launch, user role, and initial setup
        val startDestination = if (isFirstLaunch.value) {
            "role_selection"
        } else if (!isInitialSetupComplete.value) {
            "initial_setup"
        } else {
            "main"
        }

        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable("role_selection") {
                RoleSelectionScreen(navController = navController, prefs = prefs, isFirstLaunch = isFirstLaunch, userRole = userRole)
            }
            composable("initial_setup") {
                InitialSetupScreen(navController = navController, prefs = prefs, userRole = userRole.value ?: "", isInitialSetupComplete = isInitialSetupComplete, userName = userName, defaultCar = defaultCar)
            }
            composable("main") {
                MainScreen(
                    viewModel = viewModel,
                    navController = navController,
                    action = action,
                    userId = currentUserId,
                    userRole = currentUserRole,
                    defaultCar = defaultCar.value
                )
            }
            composable("summary") {
                val summaryText by viewModel.summaryText.collectAsState()
                SummaryScreen(navController = navController, summaryText = summaryText)
            }
            composable("manual_data_entry") {
                ManualDataEntryScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }
            composable("reports") {
                ReportsScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }
            composable("options") { // New route for OptionsScreen
                OptionsScreen(navController = navController, viewModel = viewModel)
            }
            composable("new_event") {
                NewEventScreen(
                    navController = navController,
                    viewModel = viewModel,
                    defaultCar = defaultCar.value
                )
            }
            composable("new_workday") {
                NewWorkdayScreen(navController = navController, viewModel = viewModel)
            }
            composable("edit_workday/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                val id = it.arguments?.getLong("id") ?: 0L
                EditWorkdayScreen(navController = navController, viewModel = viewModel, workdayId = id)
            }
            composable("edit_refuel/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                val id = it.arguments?.getLong("id") ?: 0L
                EditRefuelScreen(navController = navController, viewModel = viewModel, refuelId = id)
            }
            composable("edit_loading/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                val id = it.arguments?.getLong("id") ?: 0L
                EditLoadingScreen(navController = navController, viewModel = viewModel, loadingId = id)
            }
            composable("absence/{type}", arguments = listOf(navArgument("type") { type = NavType.StringType })) {
                val type = it.arguments?.getString("type") ?: ""
                AbsenceScreen(navController = navController, viewModel = viewModel, absenceType = type)
            }
        }
    }
}

@Composable
fun NorbAppLogo() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp), // Increased padding
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.height(30.dp).width(40.dp)) { // Adjusted size
            val strokeWidth = 6f
            val path = Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.9f) // Start at bottom-left
                lineTo(size.width * 0.2f, size.height * 0.1f) // Draw left vertical line up
                lineTo(size.width * 0.8f, size.height * 0.9f) // Draw diagonal to bottom-right
                lineTo(size.width * 0.8f, size.height * 0.1f) // Draw right vertical line up
            }
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF40E0D0), Color(0xFF00A693))
                ),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("Norb")
                }
                withStyle(style = SpanStyle(fontWeight = FontWeight.Light)) {
                    append("App")
                }
            },
            fontSize = 22.sp, // Adjusted size
            fontFamily = FontFamily.SansSerif,
            color = Color(0xFF212529)
        )
    }
}

@Composable
fun RoleSelectionScreen(navController: NavController, prefs: android.content.SharedPreferences, isFirstLaunch: MutableState<Boolean>, userRole: MutableState<String?>) {
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.3f),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.select_role),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp),
                color = Color.Black // Szövegszín feketére állítva
            )

            Button(
                onClick = {
                    scope.launch {
                        val editor = prefs.edit()
                        editor.putString(USER_ROLE_KEY, ROLE_DRIVER)
                        editor.putBoolean(IS_FIRST_LAUNCH_KEY, false)
                        editor.apply()
                        Log.d("RoleSelectionScreen", "Driver role saved.")
                        isFirstLaunch.value = false
                        userRole.value = ROLE_DRIVER
                        navController.navigate("initial_setup") {
                            popUpTo("role_selection") { inclusive = true }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.driver))
            }
        }
    }
}

@Composable
fun InitialSetupScreen(navController: NavController, prefs: android.content.SharedPreferences, userRole: String, isInitialSetupComplete: MutableState<Boolean>, userName: MutableState<String?>, defaultCar: MutableState<String?>) {
    val scope = rememberCoroutineScope()
    var currentUserName by remember { mutableStateOf(userName.value ?: "") }
    var currentDefaultCar by remember { mutableStateOf(defaultCar.value ?: "") }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.3f),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.initial_setup),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp),
                color = Color.Black // Szövegszín feketére állítva
            )

            OutlinedTextField(
                value = currentUserName,
                onValueChange = { currentUserName = it },
                label = { Text(stringResource(R.string.name), color = Color.Black) }, // Szövegszín feketére állítva
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = currentDefaultCar,
                onValueChange = { currentDefaultCar = it },
                label = { Text(stringResource(R.string.default_car_plate), color = Color.Black) }, // Szövegszín feketére állítva
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            Button(
                onClick = {
                    scope.launch {
                        val editor = prefs.edit()
                        editor.putString(USER_NAME_KEY, currentUserName)
                        editor.putString(DEFAULT_CAR_KEY, currentDefaultCar)
                        editor.putBoolean(IS_INITIAL_SETUP_COMPLETE_KEY, true)
                        editor.apply()
                        Log.d("InitialSetupScreen", "Initial setup saved.")
                        isInitialSetupComplete.value = true
                        userName.value = currentUserName // Update userName in AppContent
                        defaultCar.value = currentDefaultCar // Update defaultCar in AppContent

                        navController.navigate("main") {
                            popUpTo("initial_setup") { inclusive = true }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.save_and_continue))
            }
        }
    }
}


@Composable
fun MainScreen(
    viewModel: MainViewModel,
    navController: NavController,
    action: MutableState<String?>?,
    userId: String,
    userRole: String,
    defaultCar: String?
) {
    val isWorkdayStarted by viewModel.isWorkdayStarted.collectAsState()
    val isBreakStarted by viewModel.isBreakStarted.collectAsState()
    val isloadingStarted by viewModel.isloadingStarted.collectAsState()
    val workDuration by viewModel.workDuration.collectAsState()
    val breakDuration by viewModel.breakDuration.collectAsState()
    val overtime by viewModel.overtime.collectAsState()

    var showStartOdometerDialog by remember { mutableStateOf(false) }
    var showEndOdometerDialog by remember { mutableStateOf(false) }
    var showRefuelDialog by remember { mutableStateOf(false) }
    var showCarChangeDialog by remember { mutableStateOf(false) }
    var showCarPlateDialog by remember { mutableStateOf(false) }
    var carPlateForWorkday by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = (LocalContext.current as? Activity)

    LaunchedEffect(action?.value) {
        if (action?.value != null) {
            when (action.value) {
                MainActivity.ACTION_SHOW_WORK_DIALOG -> {
                    if (isWorkdayStarted) {
                        showEndOdometerDialog = true
                    } else {
                        showCarPlateDialog = true
                    }
                }
                MainActivity.ACTION_SHOW_REFUEL_DIALOG -> {
                    showRefuelDialog = true
                }
            }
            action.value = null // Reset action after it's handled
        }
    }

    if (showCarPlateDialog) {
        CarPlateConfirmationDialog(
            defaultCarPlate = defaultCar,
            onDismiss = { showCarPlateDialog = false },
            onConfirm = { carPlate ->
                carPlateForWorkday = carPlate
                showCarPlateDialog = false
                showStartOdometerDialog = true
            }
        )
    }

    if (showStartOdometerDialog) {
        OdometerDialog(
            title = stringResource(R.string.start_odometer),
            onDismiss = { showStartOdometerDialog = false },
            onConfirm = { odometer ->
                viewModel.startWorkday(odometer, carPlateForWorkday)
                showStartOdometerDialog = false
            }
        )
    }

    if (showEndOdometerDialog) {
        OdometerDialog(
            title = stringResource(R.string.end_odometer),
            onDismiss = { showEndOdometerDialog = false },
            onConfirm = { odometer ->
                viewModel.endWorkday(odometer)
                showEndOdometerDialog = false
            }
        )
    }

    if (showRefuelDialog) {
        RefuelDialog(
            onDismiss = { showRefuelDialog = false },
            onConfirm = { odometer, fuelType, fuelAmount, paymentMethod, carPlate ->
                viewModel.recordRefuel(odometer, fuelType, fuelAmount, paymentMethod, carPlate)
                showRefuelDialog = false
            },
            currentCarPlate = defaultCar
        )
    }

    if (showCarChangeDialog) {
        CarChangeDialog(
            onDismiss = { showCarChangeDialog = false },
            onConfirm = { newCarPlate ->
                // TODO: Implement car change logic in ViewModel
                Log.d("MainScreen", "Car changed to: $newCarPlate")
                showCarChangeDialog = false
            }
        )
    }

    MainScreenLayout(
        isWorkdayStarted = isWorkdayStarted,
        isBreakStarted = isBreakStarted,
        isloadingStarted = isloadingStarted,
        workDurationText = viewModel.formatDuration(workDuration),
        breakDurationText = viewModel.formatDuration(breakDuration),
        overtimeText = viewModel.formatDuration(overtime),
        onWorkClick = {
            if (isWorkdayStarted) {
                showEndOdometerDialog = true
            } else {
                showCarPlateDialog = true
            }
        },
        onBreakClick = { if (isBreakStarted) viewModel.endBreak() else viewModel.startBreak() },
        onRefuelClick = { showRefuelDialog = true },
        onSummaryClick = {
            viewModel.generateSummary(context) // Pass context here
            navController.navigate("summary")
        },
        onCarChangeClick = { showCarChangeDialog = true },
        onManualDataEntryClick = { navController.navigate("manual_data_entry") },
        onReportsClick = { navController.navigate("reports") },
        onSettingsClick = { navController.navigate("options") }, // Navigate to OptionsScreen
        onVacationClick = { navController.navigate("absence/VACATION") },
        onSickLeaveClick = { navController.navigate("absence/SICK_LEAVE") },
        onResetOvertimeClick = { viewModel.resetOvertime() },
        onLanguageChange = { language ->
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(LANGUAGE_KEY, language).apply()
            activity?.recreate()
        }
    )
}

@Composable
fun CarChangeDialog(onDismiss: () -> Unit, onConfirm: (newCarPlate: String) -> Unit) {
    var newCarPlate by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_car), color = Color.Black) },
        text = {
            OutlinedTextField(
                value = newCarPlate,
                onValueChange = { newCarPlate = it },
                label = { Text(stringResource(R.string.new_car_plate), color = Color.Black) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(newCarPlate) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun CarPlateConfirmationDialog(
    defaultCarPlate: String?,
    onDismiss: () -> Unit,
    onConfirm: (carPlate: String) -> Unit
) {
    var carPlate by remember { mutableStateOf(defaultCarPlate ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_car_plate)) },
        text = {
            OutlinedTextField(
                value = carPlate,
                onValueChange = { carPlate = it },
                label = { Text(stringResource(R.string.car_plate)) },
                placeholder = { Text("Pl. ABC-123") }
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(carPlate) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}


@Composable
fun MainScreenLayout(
    isWorkdayStarted: Boolean,
    isBreakStarted: Boolean,
    isloadingStarted: Boolean,
    workDurationText: String,
    breakDurationText: String,
    overtimeText: String,
    onWorkClick: () -> Unit,
    onBreakClick: () -> Unit,
    onRefuelClick: () -> Unit,
    onSummaryClick: () -> Unit,
    onCarChangeClick: () -> Unit,
    onManualDataEntryClick: () -> Unit,
    onReportsClick: () -> Unit,
    onSettingsClick: () -> Unit, // New callback for settings
    onVacationClick: () -> Unit,
    onSickLeaveClick: () -> Unit,
    onResetOvertimeClick: () -> Unit,
    onLanguageChange: (String) -> Unit
) {
    var showOptionsMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) { // Root Box for background
        Image(
            painter = painterResource(id = R.drawable.logo), // KÖZVETLENÜL A LOGO.PNG-T HASZNÁLJA
            contentDescription = null, // Decorative image
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.3f), // HALVÁNYÍTÁS A COMPOSE-BAN
            contentScale = ContentScale.Crop
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.net_work_time),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black
                )
                Text(
                    text = workDurationText,
                    fontSize = 48.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.total_break_time),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black
                )
                Text(
                    text = breakDurationText,
                    fontSize = 32.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${stringResource(R.string.overtime)}: $overtimeText",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black
                )
                Button(onClick = onResetOvertimeClick) {
                    Text(stringResource(R.string.reset_overtime))
                }
                Spacer(modifier = Modifier.height(32.dp))

                Row {
                    Button(onClick = onWorkClick) {
                        Text(if (isWorkdayStarted) stringResource(R.string.end_workday) else stringResource(R.string.start_workday))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onBreakClick, enabled = isWorkdayStarted) {
                        Text(if (isBreakStarted) stringResource(R.string.end_break) else stringResource(R.string.start_break))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    Button(onClick = onRefuelClick, enabled = isWorkdayStarted) {
                        Text(stringResource(R.string.record_refuel))
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Row {
                    Button(onClick = onVacationClick) {
                        Text(stringResource(R.string.vacation))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onSickLeaveClick) {
                        Text(stringResource(R.string.sick_leave))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onSummaryClick) {
                    Text(stringResource(R.string.daily_summary))
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = { showOptionsMenu = !showOptionsMenu }) {
                    Text(stringResource(R.string.options))
                }
                if (showOptionsMenu) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = onCarChangeClick, enabled = isWorkdayStarted) {
                            Text(stringResource(R.string.change_car))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onReportsClick) {
                            Text(stringResource(R.string.queries))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onManualDataEntryClick) {
                            Text(stringResource(R.string.manual_data_entry))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onSettingsClick) { // New Settings button
                            Text(stringResource(R.string.settings))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Button(onClick = { onLanguageChange("hu") }) {
                                Text("Magyar")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { onLanguageChange("de") }) {
                                Text("Deutsch")
                            }
                        }
                    }
                }
            }
            // Fixed logo at the bottom
            NorbAppLogo()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    IdTheme {
        MainScreenLayout(
            isWorkdayStarted = true,
            isBreakStarted = false,
            isloadingStarted = false,
            workDurationText = "04:35:12",
            breakDurationText = "00:21:30",
            overtimeText = "00:00:00",
            onWorkClick = {},
            onBreakClick = {},
            onRefuelClick = {},
            onSummaryClick = {},
            onCarChangeClick = {},
            onManualDataEntryClick = {},
            onReportsClick = {},
            onSettingsClick = {}, // Added for preview
            onVacationClick = {},
            onSickLeaveClick = {},
            onResetOvertimeClick = {},
            onLanguageChange = {}
        )
    }
}


@Composable
fun DeleteConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_delete), color = Color.Black) },
        text = { Text(stringResource(R.string.confirm_delete_message), color = Color.Black) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
            ) {
                Text(stringResource(R.string.delete), color = Color.Red)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun RestartDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restart_required)) },
        text = { Text(stringResource(R.string.database_restored_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.restart))
            }
        }
    )
}

@Composable
fun OdometerDialog(title: String, onDismiss: () -> Unit, onConfirm: (odometer: Int) -> Unit) {
    var odometer by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.Black) },
        text = {
            OutlinedTextField(
                value = odometer,
                onValueChange = { odometer = it },
                label = { Text(stringResource(R.string.odometer), color = Color.Black) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(odometer.toInt()) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefuelDialog(
    onDismiss: () -> Unit,
    onConfirm: (odometer: Int, fuelType: String, fuelAmount: Double, paymentMethod: String, carPlate: String) -> Unit,
    currentCarPlate: String?
) {
    val dieselStr = stringResource(R.string.fuel_type_diesel)
    val adblueStr = stringResource(R.string.fuel_type_adblue)
    val chipStr = stringResource(R.string.payment_method_chip)
    val dkvStr = stringResource(R.string.payment_method_dkv)
    val cashStr = stringResource(R.string.payment_method_cash)

    var odometer by remember { mutableStateOf("") }
    var fuelType by remember { mutableStateOf(dieselStr) }
    var fuelAmount by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(chipStr) }
    var carPlate by remember { mutableStateOf(currentCarPlate ?: "") }

    val fuelTypes = listOf(dieselStr, adblueStr)
    val paymentMethods = listOf(chipStr, dkvStr, cashStr)

    var fuelTypeExpanded by remember { mutableStateOf(false) }
    var paymentMethodExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.refuel_details), color = Color.Black) },
        text = {
            Column {
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it },
                    label = { Text(stringResource(R.string.odometer), color = Color.Black) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = fuelTypeExpanded,
                    onExpandedChange = { fuelTypeExpanded = !fuelTypeExpanded }
                ) {
                    OutlinedTextField(
                        value = fuelType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.fuel_type), color = Color.Black) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fuelTypeExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = fuelTypeExpanded,
                        onDismissRequest = { fuelTypeExpanded = false }
                    ) {
                        fuelTypes.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption, color = Color.Black) },
                                onClick = {
                                    fuelType = selectionOption
                                    fuelTypeExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = fuelAmount,
                    onValueChange = { fuelAmount = it },
                    label = { Text(stringResource(R.string.fuel_amount), color = Color.Black) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = paymentMethodExpanded,
                    onExpandedChange = { paymentMethodExpanded = !paymentMethodExpanded }
                ) {
                    OutlinedTextField(
                        value = paymentMethod,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.payment_method), color = Color.Black) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paymentMethodExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = paymentMethodExpanded,
                        onDismissRequest = { paymentMethodExpanded = false }
                    ) {
                        paymentMethods.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption, color = Color.Black) },
                                onClick = {
                                    paymentMethod = selectionOption
                                    paymentMethodExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = carPlate,
                    onValueChange = { carPlate = it },
                    label = { Text(stringResource(R.string.car_plate)) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(odometer.toInt(), fuelType, fuelAmount.toDouble(), paymentMethod, carPlate) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
