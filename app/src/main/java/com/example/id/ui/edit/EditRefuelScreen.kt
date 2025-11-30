package com.example.id.ui.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.id.R
import com.example.id.data.entities.RefuelEvent
import com.example.id.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRefuelScreen(navController: NavController, viewModel: MainViewModel, refuelId: Long) {

    LaunchedEffect(refuelId) {
        viewModel.loadRefuelForEdit(refuelId)
    }

    val eventToEdit by viewModel.editingEvent.collectAsState()

    var odometer by remember { mutableStateOf("") }
    var fuelAmount by remember { mutableStateOf("") }
    var carPlate by remember { mutableStateOf("") }
    var fuelType by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("") }
    var timestamp by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }

    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()) }

    val fuelTypes = listOf(
        stringResource(R.string.fuel_type_diesel),
        stringResource(R.string.fuel_type_adblue)
    )
    val paymentMethods = listOf(
        stringResource(R.string.payment_method_chip),
        stringResource(R.string.payment_method_dkv),
        stringResource(R.string.payment_method_cash)
    )
    var fuelTypeExpanded by remember { mutableStateOf(false) }
    var paymentMethodExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(eventToEdit) {
        (eventToEdit as? RefuelEvent)?.let {
            odometer = it.odometer.toString()
            fuelAmount = it.fuelAmount.toString()
            carPlate = it.carPlate
            fuelType = it.fuelType
            paymentMethod = it.paymentMethod
            timestamp = dateFormat.format(it.timestamp)
            location = it.location ?: ""
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
        Text(stringResource(R.string.edit_refuel), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = timestamp,
            onValueChange = { timestamp = it },
            label = { Text(stringResource(R.string.date_time)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text(stringResource(R.string.location)) },
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
            value = odometer,
            onValueChange = { odometer = it },
            label = { Text(stringResource(R.string.odometer)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = fuelAmount,
            onValueChange = { fuelAmount = it },
            label = { Text(stringResource(R.string.fuel_amount)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
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
                label = { Text(stringResource(R.string.fuel_type)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fuelTypeExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = fuelTypeExpanded,
                onDismissRequest = { fuelTypeExpanded = false }
            ) {
                fuelTypes.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            fuelType = selectionOption
                            fuelTypeExpanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = paymentMethodExpanded,
            onExpandedChange = { paymentMethodExpanded = !paymentMethodExpanded }
        ) {
            OutlinedTextField(
                value = paymentMethod,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.payment_method)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paymentMethodExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = paymentMethodExpanded,
                onDismissRequest = { paymentMethodExpanded = false }
            ) {
                paymentMethods.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            paymentMethod = selectionOption
                            paymentMethodExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                (eventToEdit as? RefuelEvent)?.let {
                    val updatedEvent = it.copy(
                        carPlate = carPlate,
                        odometer = odometer.toIntOrNull() ?: it.odometer,
                        fuelAmount = fuelAmount.toDoubleOrNull() ?: it.fuelAmount,
                        fuelType = fuelType,
                        paymentMethod = paymentMethod,
                        timestamp = try { dateFormat.parse(timestamp)!! } catch (e: Exception) { it.timestamp },
                        location = location
                    )
                    viewModel.updateRefuel(updatedEvent)
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
