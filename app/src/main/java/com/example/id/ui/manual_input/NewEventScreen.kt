package com.example.id.ui.manual_input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import com.example.id.viewmodel.MainViewModel
import com.example.id.ui.dialogs.RefuelDialog

@Composable
fun NewEventScreen(
    navController: NavController,
    viewModel: MainViewModel,
    defaultCar: String?
) {
    var showRefuelDialog by remember { mutableStateOf(true) }

    if (showRefuelDialog) {
        RefuelDialog(
            onDismiss = {
                showRefuelDialog = false
                navController.popBackStack()
            },
            onConfirm = { odometer, fuelType, fuelAmount, paymentMethod, carPlate, value ->
                viewModel.recordRefuel(odometer, fuelType, fuelAmount, paymentMethod, carPlate, value)
                showRefuelDialog = false
                navController.popBackStack()
            },
            currentCarPlate = defaultCar
        )
    }
}