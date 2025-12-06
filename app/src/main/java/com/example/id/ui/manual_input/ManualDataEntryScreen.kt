package com.example.id.ui.manual_input

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.id.R
import com.example.id.data.entities.EventType
import com.example.id.data.entities.LoadingEvent
import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.WorkdayEvent
import com.example.id.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ManualDataEntryScreen(navController: NavController, viewModel: MainViewModel) {

    LaunchedEffect(Unit) {
        viewModel.loadRecentEvents()
    }

    val recentEvents by viewModel.recentEvents.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.manual_data_entry),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(onClick = { navController.navigate("new_event") }) {
            Text(stringResource(R.string.add_new_event))
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Text(stringResource(R.string.past_7_days_events), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(recentEvents) { event ->
                when (event) {
                    is WorkdayEvent -> WorkdayItemCard(navController, event, viewModel)
                    is RefuelEvent -> RefuelItemCard(navController, event, viewModel)
                    is LoadingEvent -> LoadingItemCard(navController, event, viewModel)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Button(onClick = {
            navController.popBackStack()
        }) {
            Text(stringResource(id = R.string.back))
        }
    }
}

@Composable
fun WorkdayItemCard(navController: NavController, event: WorkdayEvent, viewModel: MainViewModel) {
    val dateTimeFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN) }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN) }

    Card(modifier = Modifier.fillMaxWidth().border(1.dp, Color.LightGray)) {
        Column(modifier = Modifier.padding(8.dp)) {
            val title = when (event.type) {
                EventType.WORK -> stringResource(R.string.workday)
                EventType.VACATION -> stringResource(R.string.vacation)
                EventType.SICK_LEAVE -> stringResource(R.string.sick_leave)
            }
            Text(title, style = MaterialTheme.typography.titleMedium)

            when (event.type) {
                EventType.WORK -> {
                    val totalKm = if (event.endOdometer != null && event.startOdometer != null) {
                        event.endOdometer - event.startOdometer
                    } else {
                        0
                    }
                    val netWorkTime = if (event.endTime != null) {
                        val diff = event.endTime.time - event.startTime.time
                        val hours = TimeUnit.MILLISECONDS.toHours(diff)
                        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
                        String.format("%02d:%02d", hours, minutes)
                    } else {
                        "N/A"
                    }

                    Text("${stringResource(R.string.user_name_label)} ${event.user?.username ?: event.userId}")
                    Text("${stringResource(R.string.start_time_label)} ${dateTimeFormat.format(event.startTime)} - ${event.startLocation}")
                    event.endTime?.let { Text("${stringResource(R.string.end_time_label)} ${dateTimeFormat.format(it)} - ${event.endLocation}") }
                    Text("${stringResource(R.string.net_work_time_label)} $netWorkTime")
                    if (event.breakTime > 0) {
                        Text("${stringResource(R.string.break_time_label)} ${event.breakTime} ${stringResource(R.string.break_time_unit)}")
                    }
                    Text("${stringResource(R.string.start_odometer_label)} ${event.startOdometer}")
                    event.endOdometer?.let { Text("${stringResource(R.string.end_odometer_label)} $it") }
                    Text("${stringResource(R.string.total_km_label)} $totalKm km")

                }
                EventType.VACATION, EventType.SICK_LEAVE -> {
                    Text("${stringResource(R.string.start_time_label)} ${dateFormat.format(event.startTime)}")
                    event.endTime?.let { Text("${stringResource(R.string.end_time_label)} ${dateFormat.format(it)}") }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { navController.navigate("edit_workday/${event.localId}") }) {
                    Text(stringResource(id = R.string.edit))
                }
                TextButton(onClick = { viewModel.deleteWorkday(event.localId) }) {
                    Text(stringResource(id = R.string.delete), color = Color.Red)
                }
            }
        }
    }
}

@Composable
fun RefuelItemCard(navController: NavController, event: RefuelEvent, viewModel: MainViewModel) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN) }
    Card(modifier = Modifier.fillMaxWidth().border(1.dp, Color.LightGray)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(stringResource(R.string.refueling), style = MaterialTheme.typography.titleMedium)
            Text("${stringResource(R.string.date_time_label)} ${dateFormat.format(event.timestamp)}")
            Text("${event.fuelAmount}L ${event.fuelType} - ${event.carPlate}")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { navController.navigate("edit_refuel/${event.localId}") }) {
                    Text(stringResource(id = R.string.edit))
                }
                TextButton(onClick = { viewModel.deleteRefuel(event.localId) }) {
                    Text(stringResource(id = R.string.delete), color = Color.Red)
                }
            }
        }
    }
}

@Composable
fun LoadingItemCard(navController: NavController, event: LoadingEvent, viewModel: MainViewModel) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN) }
    Card(modifier = Modifier.fillMaxWidth().border(1.dp, Color.LightGray)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(stringResource(R.string.loading), style = MaterialTheme.typography.titleMedium)
            event.startTime?.let { Text("${stringResource(R.string.start_time_label)} ${dateFormat.format(it)}") }
            event.endTime?.let { Text("${stringResource(R.string.end_time_label)} ${dateFormat.format(it)}") }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { navController.navigate("edit_loading/${event.localId}") }) {
                    Text(stringResource(id = R.string.edit))
                }
                TextButton(onClick = { viewModel.deleteLoading(event.localId) }) {
                    Text(stringResource(id = R.string.delete), color = Color.Red)
                }
            }
        }
    }
}