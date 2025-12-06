package com.example.id.ui.main

import android.app.Activity
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.id.R
import com.example.id.data.entities.WorkdayEvent
import com.example.id.ui.dialogs.RefuelDialog
import com.example.id.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val isWorkdayStarted by viewModel.isWorkdayStarted.collectAsState()
    val isBreakStarted by viewModel.isBreakStarted.collectAsState()
    val workDuration by viewModel.workDuration.collectAsState()
    val breakDuration by viewModel.breakDuration.collectAsState()
    val overtime by viewModel.overtime.collectAsState()
    val recentWorkdays by viewModel.recentWorkdays.collectAsState()

    var showStartOdometerDialog by remember { mutableStateOf(false) }
    var showEndOdometerDialog by remember { mutableStateOf(false) }
    var showRefuelDialog by remember { mutableStateOf(false) }
    var showCarPlateDialog by remember { mutableStateOf(false) }
    var carPlateForWorkday by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    if (showCarPlateDialog) {
        CarPlateConfirmationDialog(
            defaultCarPlate = "", // TODO: Get from prefs
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
            onConfirm = { odometer, fuelType, fuelAmount, paymentMethod, carPlate, value ->
                viewModel.recordRefuel(odometer, fuelType, fuelAmount, paymentMethod, carPlate, value)
                showRefuelDialog = false
            },
            currentCarPlate = ""
        )
    }

    Box(modifier = Modifier.fillMaxSize()) { // Root Box for background
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = null, // Decorative image
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.3f), // HALVÁNYÍTÁS A COMPOSE-BAN
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top controls and timers
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.net_work_time),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black
                )
                Text(
                    text = viewModel.formatDuration(workDuration),
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
                    text = viewModel.formatDuration(breakDuration),
                    fontSize = 32.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${stringResource(R.string.overtime)}: ${viewModel.formatDuration(overtime)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(32.dp))

                Row {
                    Button(onClick = {
                        if (isWorkdayStarted) {
                            showEndOdometerDialog = true
                        } else {
                            showCarPlateDialog = true
                        }
                    }) {
                        Text(if (isWorkdayStarted) stringResource(R.string.end_workday) else stringResource(R.string.start_workday))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { if (isBreakStarted) viewModel.endBreak() else viewModel.startBreak() }, enabled = isWorkdayStarted) {
                        Text(if (isBreakStarted) stringResource(R.string.end_break) else stringResource(R.string.start_break))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    Button(onClick = { showRefuelDialog = true }, enabled = isWorkdayStarted) {
                        Text(stringResource(R.string.record_refuel))
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))

                Button(onClick = { navController.navigate("query_screen") }) {
                    Text(stringResource(R.string.queries))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.logout() }) {
                    Text("Kijelentkezés")
                }
            }
            
            // Recent workdays list
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(recentWorkdays) { workday ->
                    WorkdayListItem(
                        workday = workday,
                        onEdit = { /* TODO: Navigate to edit screen */ },
                        onDelete = { viewModel.deleteWorkday(workday.localId) }
                    )
                }
            }
        }
    }
}

@Composable
fun WorkdayListItem(
    workday: WorkdayEvent,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
    val durationFormat: (Long) -> String = { millis ->
        if (millis < 0) {
            "00:00:00"
        } else {
            val hours = TimeUnit.MILLISECONDS.toHours(millis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Felhasználó: ${workday.userId}", fontWeight = FontWeight.Bold)
            Text("Munkaidő kezdete: ${dateFormat.format(workday.startTime)}")
            workday.endTime?.let { Text("Munkaidő vége: ${dateFormat.format(it)}") }
            Text("Város: ${workday.startLocation ?: "N/A"}")
            Text("Induló km: ${workday.startOdometer ?: "N/A"}")
            workday.endOdometer?.let { Text("Záró km: $it") }

            val netWorkTimeMillis = workday.endTime?.let {
                val totalDuration = it.time - workday.startTime.time
                val breakMillis = workday.breakTime * 60 * 1000L
                totalDuration - breakMillis
            }

            Text("Összes szünet: ${workday.breakTime} perc")
            netWorkTimeMillis?.let { Text("Nettó munkaidő: ${durationFormat(it)}") }

            val drivenKm = workday.endOdometer?.let { endOdo ->
                workday.startOdometer?.let { startOdo ->
                    endOdo - startOdo
                }
            }
            drivenKm?.let { Text("Megtett km: $it") }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onEdit) {
                    Text("Szerkesztés")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onDelete) {
                    Text("Törlés")
                }
            }
        }
    }
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
            TextButton(onClick = { odometer.toIntOrNull()?.let(onConfirm) }) {
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
