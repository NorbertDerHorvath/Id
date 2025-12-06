package com.example.id.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.id.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefuelDialog(
    onDismiss: () -> Unit,
    onConfirm: (odometer: Int, fuelType: String, fuelAmount: Double, paymentMethod: String, carPlate: String, value: Double?) -> Unit,
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
    var value by remember { mutableStateOf("") }
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

                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.value_in_huf), color = Color.Black) },
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
            TextButton(onClick = { onConfirm(odometer.toInt(), fuelType, fuelAmount.toDouble(), paymentMethod, carPlate, value.toDoubleOrNull()) }) {
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
