package com.example.id.ui.edit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.id.R
import com.example.id.data.entities.EventType
import com.example.id.data.entities.WorkdayEvent
import com.example.id.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkdayScreen(navController: NavController, viewModel: MainViewModel, workdayId: Long) {

    LaunchedEffect(workdayId) {
        viewModel.loadWorkdayForEdit(workdayId)
    }

    val eventToEdit by viewModel.editingEvent.collectAsState()

    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var breakTimeInMinutes by remember { mutableStateOf("0") }
    var startLocation by remember { mutableStateOf("") }
    var endLocation by remember { mutableStateOf("") }
    var startOdometer by remember { mutableStateOf("") }
    var endOdometer by remember { mutableStateOf("") }
    var carPlate by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf(EventType.WORK) }

    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()) }
    val eventTypeTranslations = remember {
        mapOf(
            EventType.WORK to R.string.workday,
            EventType.VACATION to R.string.vacation,
            EventType.SICK_LEAVE to R.string.sick_leave
        )
    }

    LaunchedEffect(eventToEdit) {
        (eventToEdit as? WorkdayEvent)?.let {
            startTime = dateFormat.format(it.startTime)
            endTime = it.endTime?.let { d -> dateFormat.format(d) } ?: ""
            breakTimeInMinutes = it.breakTime.toString()
            startLocation = it.startLocation ?: ""
            endLocation = it.endLocation ?: ""
            startOdometer = it.startOdometer?.toString() ?: ""
            endOdometer = it.endOdometer?.toString() ?: ""
            carPlate = it.carPlate ?: ""
            eventType = it.type
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearEditingEvent()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.edit_event), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                value = stringResource(id = eventTypeTranslations[eventType] ?: R.string.workday),
                onValueChange = { },
                label = { Text(stringResource(R.string.type)) },
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                eventTypeTranslations.forEach { (type, nameResId) ->
                    DropdownMenuItem(
                        text = { Text(text = stringResource(id = nameResId)) },
                        onClick = {
                            eventType = type
                            expanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = startTime,
            onValueChange = { startTime = it },
            label = { Text(stringResource(R.string.start_time_full)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = endTime,
            onValueChange = { endTime = it },
            label = { Text(stringResource(R.string.end_time_full)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = breakTimeInMinutes,
            onValueChange = { breakTimeInMinutes = it },
            label = { Text(stringResource(R.string.break_time_in_minutes)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = startLocation,
            onValueChange = { startLocation = it },
            label = { Text(stringResource(R.string.start_location)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = endLocation,
            onValueChange = { endLocation = it },
            label = { Text(stringResource(R.string.end_location)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = carPlate,
            onValueChange = { carPlate = it },
            label = { Text(stringResource(R.string.car_plate)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = startOdometer,
            onValueChange = { startOdometer = it },
            label = { Text(stringResource(R.string.start_odometer)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = endOdometer,
            onValueChange = { endOdometer = it },
            label = { Text(stringResource(R.string.end_odometer)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                (eventToEdit as? WorkdayEvent)?.let {
                    val totalBreakMinutes = breakTimeInMinutes.toIntOrNull() ?: 0

                    val startDateTime = try { dateFormat.parse(startTime) } catch (e: Exception) { it.startTime }
                    val updatedEvent = it.copy(
                        startTime = startDateTime,
                        endTime = try { dateFormat.parse(endTime) } catch (e: Exception) { null },
                        breakTime = totalBreakMinutes,
                        startLocation = startLocation.takeIf { s -> s.isNotBlank() },
                        endLocation = endLocation.takeIf { s -> s.isNotBlank() },
                        carPlate = carPlate.takeIf { s -> s.isNotBlank() },
                        startOdometer = startOdometer.toIntOrNull(),
                        endOdometer = endOdometer.toIntOrNull(),
                        type = eventType
                    )
                    viewModel.updateWorkday(updatedEvent)
                }
                navController.popBackStack()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(id = R.string.save))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(id = R.string.cancel))
        }
    }
}
