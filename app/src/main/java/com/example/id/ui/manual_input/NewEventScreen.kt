package com.example.id.ui.manual_input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.id.R
import com.example.id.RefuelDialog
import com.example.id.viewmodel.MainViewModel

@Composable
fun NewEventScreen(navController: NavController, viewModel: MainViewModel, defaultCar: String?) {
    var showRefuelDialog by remember { mutableStateOf(false) }

    if (showRefuelDialog) {
        RefuelDialog(
            onDismiss = { showRefuelDialog = false },
            onConfirm = { odometer, fuelType, fuelAmount, paymentMethod, carPlate ->
                viewModel.recordRefuel(odometer, fuelType, fuelAmount, carPlate, paymentMethod)
                showRefuelDialog = false
                // navController.popBackStack() // Don't pop back here, stay on the selection screen
            },
            currentCarPlate = defaultCar
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.add_new_event),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = { navController.navigate("new_workday") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.new_workday))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showRefuelDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.new_refuel))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { /* TODO: Implement Loading event creation */ },
            modifier = Modifier.fillMaxWidth(),
            enabled = false // Disabled for now
        ) {
            Text(stringResource(R.string.new_loading))
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = { navController.popBackStack() }) {
            Text(stringResource(id = R.string.back))
        }
    }
}
