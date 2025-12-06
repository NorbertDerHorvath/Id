package com.example.id.ui.query

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.WorkdayEvent
import com.example.id.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun QueryScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    var workdayStartDate by remember { mutableStateOf("") }
    var workdayEndDate by remember { mutableStateOf("") }
    var workdayCarPlate by remember { mutableStateOf("") }

    var refuelStartDate by remember { mutableStateOf("") }
    var refuelEndDate by remember { mutableStateOf("") }
    var refuelCarPlate by remember { mutableStateOf("") }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text("Munkaidő lekérdezés", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            DateInputField(label = "Kezdő dátum", date = workdayStartDate, onDateChange = { workdayStartDate = it })
            Spacer(modifier = Modifier.height(8.dp))
            DateInputField(label = "Záró dátum", date = workdayEndDate, onDateChange = { workdayEndDate = it })
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = workdayCarPlate,
                onValueChange = { workdayCarPlate = it },
                label = { Text("Rendszám (opcionális)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.queryWorkdays(workdayStartDate, workdayEndDate, workdayCarPlate)
                    navController.navigate("query_result/workday")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Munkaidő lekérdezése")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
            Text("Tankolás lekérdezés", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            DateInputField(label = "Kezdő dátum", date = refuelStartDate, onDateChange = { refuelStartDate = it })
            Spacer(modifier = Modifier.height(8.dp))
            DateInputField(label = "Záró dátum", date = refuelEndDate, onDateChange = { refuelEndDate = it })
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = refuelCarPlate,
                onValueChange = { refuelCarPlate = it },
                label = { Text("Rendszám (opcionális)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.queryRefuels(refuelStartDate, refuelEndDate, refuelCarPlate)
                    navController.navigate("query_result/refuel")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tankolások lekérdezése")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { navController.popBackStack() }) {
                Text("Vissza")
            }
        }
    }
}

@Composable
fun DateInputField(label: String, date: String, onDateChange: (String) -> Unit) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    OutlinedTextField(
        value = date,
        onValueChange = { onDateChange(it) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        readOnly = true,
        trailingIcon = {
            IconButton(onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        calendar.set(year, month, dayOfMonth)
                        onDateChange(SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(calendar.time))
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }) {
                Icon(Icons.Default.DateRange, contentDescription = "Dátumválasztó")
            }
        }
    )
}


@Composable
fun WorkdayResultItem(event: WorkdayEvent) {
    val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Munkanap", style = MaterialTheme.typography.titleMedium)
        Text("Kezdés: ${dateFormat.format(event.startTime)}")
        event.endTime?.let { Text("Vége: ${dateFormat.format(it)}") }
        Text("Rendszám: ${event.carPlate ?: "N/A"}")
    }
}

@Composable
fun RefuelResultItem(event: RefuelEvent) {
    val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Tankolás", style = MaterialTheme.typography.titleMedium)
        Text("Időpont: ${dateFormat.format(event.timestamp)}")
        Text("Rendszám: ${event.carPlate}")
        Text("Mennyiség: ${event.fuelAmount} L")
        event.value?.let { Text("Érték: $it EUR") }
    }
}
