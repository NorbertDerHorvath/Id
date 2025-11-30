package com.example.id.ui.manual_input

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
import com.example.id.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewWorkdayScreen(navController: NavController, viewModel: MainViewModel) {

    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var breakTime by remember { mutableStateOf("0") }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.add_new_event), style = MaterialTheme.typography.headlineMedium)
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
            value = breakTime,
            onValueChange = { breakTime = it },
            label = { Text(stringResource(R.string.break_minute)) },
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
                val startDateTime = try { dateFormat.parse(startTime) } catch (e: Exception) { null }
                if (startDateTime != null) {
                    viewModel.insertManualWorkday(
                        startTime = startDateTime,
                        endTime = try { dateFormat.parse(endTime) } catch (e: Exception) { null },
                        startLocation = startLocation.takeIf { it.isNotBlank() },
                        endLocation = endLocation.takeIf { it.isNotBlank() },
                        carPlate = carPlate.takeIf { it.isNotBlank() },
                        startOdometer = startOdometer.toIntOrNull(),
                        endOdometer = endOdometer.toIntOrNull(),
                        breakTime = breakTime.toIntOrNull() ?: 0,
                        type = eventType
                    )
                    navController.popBackStack()
                }
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
