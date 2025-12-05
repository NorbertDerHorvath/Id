package com.example.id.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.id.data.entities.LoadingEvent
import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.WorkdayEvent
import com.example.id.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ReportsScreen(navController: NavController, viewModel: MainViewModel) {
    val recentEvents by viewModel.recentEvents.collectAsState()
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadRecentEvents()
    }

    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text("Adatok törlése") },
            text = { Text("Biztosan törölni szeretnéd az összes szerveren lévő adatot? Ez a művelet nem vonható vissza.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllData()
                        showDeleteConfirmationDialog = false
                    }
                ) {
                    Text("Törlés")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmationDialog = false }) {
                    Text("Mégse")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Jelentések",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(recentEvents) { event ->
                EventCard(event = event)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showDeleteConfirmationDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Szerver adatok törlése")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Vissza")
        }
    }
}

@Composable
fun EventCard(event: Any) {
    val dateFormatter = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (event) {
                is WorkdayEvent -> {
                    Text("Munkaidő", style = MaterialTheme.typography.titleSmall)
                    Text("Kezdés: ${dateFormatter.format(event.startTime)}")
                    event.endTime?.let { Text("Befejezés: ${dateFormatter.format(it)}") }
                    event.startLocation?.let { Text("Kezdő hely: $it") }
                    event.endLocation?.let { Text("Záró hely: $it") }
                    event.carPlate?.let { Text("Rendszám: $it") }
                }
                is RefuelEvent -> {
                    Text("Tankolás", style = MaterialTheme.typography.titleSmall)
                    Text("Időpont: ${dateFormatter.format(event.timestamp)}")
                    Text("Mennyiség: ${event.fuelAmount} L")
                    event.location?.let { Text("Hely: $it") }
                    Text("Rendszám: ${event.carPlate}")
                }
                is LoadingEvent -> {
                    Text("Rakodás", style = MaterialTheme.typography.titleSmall)
                    Text("Kezdés: ${dateFormatter.format(event.startTime)}")
                    event.endTime?.let { Text("Befejezés: ${dateFormatter.format(it)}") }
                    event.location?.let { Text("Hely: $it") }
                }
                else -> {
                    Text("Ismeretlen esemény")
                }
            }
        }
    }
}
