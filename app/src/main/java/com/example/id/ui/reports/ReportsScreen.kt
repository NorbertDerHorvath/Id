package com.example.id.ui.reports

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.id.R
import com.example.id.data.entities.EventType
import com.example.id.data.entities.LoadingEvent
import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.WorkdayReportItem
import com.example.id.data.entities.Workday
import com.example.id.util.PdfExporter
import com.example.id.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(navController: NavController, viewModel: MainViewModel) {
    val context = LocalContext.current

    val reportTypes = remember {
        mapOf(
            "work_time" to R.string.work_time,
            "refueling" to R.string.refueling,
            "loading" to R.string.loading
        )
    }

    var eventTypeKey by remember { mutableStateOf("work_time") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var carPlate by remember { mutableStateOf("") }
    var fuelType by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("") }

    var eventTypeExpanded by remember { mutableStateOf(false) }

    val fuelTypes = listOf("", stringResource(R.string.fuel_type_diesel), stringResource(R.string.fuel_type_adblue))
    var fuelTypeExpanded by remember { mutableStateOf(false) }
    val paymentMethods = listOf("", stringResource(R.string.payment_method_chip), stringResource(R.string.payment_method_dkv), stringResource(R.string.payment_method_cash))
    var paymentMethodExpanded by remember { mutableStateOf(false) }

    val reportResults by viewModel.reportResults.collectAsState()

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val pdfExporter = remember { PdfExporter(context) }
    val prefs = remember { context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE) }
    val userName = prefs.getString("user_name", "Ismeretlen") ?: "Ismeretlen"

    // Reset filters when event type changes
    LaunchedEffect(eventTypeKey) {
        carPlate = ""
        fuelType = ""
        paymentMethod = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.reports_and_queries),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Filters
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Column {
                    // Event Type Filter
                    ExposedDropdownMenuBox(
                        expanded = eventTypeExpanded,
                        onExpandedChange = { eventTypeExpanded = !eventTypeExpanded }
                    ) {
                        OutlinedTextField(
                            value = stringResource(id = reportTypes[eventTypeKey] ?: R.string.work_time),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.event_type)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = eventTypeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = eventTypeExpanded,
                            onDismissRequest = { eventTypeExpanded = false }
                        ) {
                            reportTypes.forEach { (key, nameResId) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = nameResId)) },
                                    onClick = {
                                        eventTypeKey = key
                                        eventTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Date Filters
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = startDate,
                                onValueChange = { startDate = it },
                                label = { Text(stringResource(R.string.start_date)) },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { showStartDatePicker = true })
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = endDate,
                                onValueChange = { endDate = it },
                                label = { Text(stringResource(R.string.end_date)) },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { showEndDatePicker = true })
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Car Plate filter for relevant types
                    if (eventTypeKey == "work_time" || eventTypeKey == "refueling" || eventTypeKey == "loading") {
                        OutlinedTextField(
                            value = carPlate,
                            onValueChange = { carPlate = it },
                            label = { Text(stringResource(R.string.car_plate)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Refuel-specific filters
                    if (eventTypeKey == "refueling") {
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
                                        text = { Text(if (selectionOption.isEmpty()) stringResource(R.string.all) else selectionOption) },
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
                                        text = { Text(if (selectionOption.isEmpty()) stringResource(R.string.all) else selectionOption) },
                                        onClick = {
                                            paymentMethod = selectionOption
                                            paymentMethodExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.runReport(
                                eventTypeKey = eventTypeKey,
                                startDateString = startDate,
                                endDateString = endDate,
                                carPlate = carPlate,
                                fuelType = fuelType,
                                paymentMethod = paymentMethod
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.query))
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(stringResource(R.string.results), style = MaterialTheme.typography.titleLarge)
                }
            }

            // Results
            items(reportResults) { result ->
                when (result) {
                    is WorkdayReportItem -> WorkdayResultCard(result, viewModel)
                    is RefuelEvent -> RefuelResultCard(result)
                    is LoadingEvent -> LoadingResultCard(result, viewModel)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Bottom buttons
        Column {
            Button(
                onClick = { 
                    pdfExporter.exportReportToPdf(eventTypeKey, reportResults, viewModel, userName)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = reportResults.isNotEmpty()
            ) {
                Text(stringResource(R.string.export_to_pdf))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { 
                    pdfExporter.exportAndSendReportToWhatsApp(eventTypeKey, reportResults, viewModel, userName)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = reportResults.isNotEmpty()
            ) {
                Text(stringResource(R.string.send_to_boss_whatsapp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                navController.popBackStack()
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.back))
            }
        }
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        startDate = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).format(Date(it))
                    }
                    showStartDatePicker = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        endDate = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).format(Date(it))
                    }
                    showEndDatePicker = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun WorkdayResultCard(item: WorkdayReportItem, viewModel: MainViewModel) {
    val dateTimeFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN) }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN) }
    val event = item.workday
    val notAvailable = stringResource(R.string.not_available)
    Card(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray)) {
        Column(modifier = Modifier.padding(8.dp)) {
            val title = when (event.type) {
                EventType.WORK -> stringResource(R.string.workday)
                EventType.VACATION -> stringResource(R.string.vacation)
                EventType.SICK_LEAVE -> stringResource(R.string.sick_leave)
                else -> ""
            }
            Text(title, style = MaterialTheme.typography.titleMedium)

            when (event.type) {
                EventType.WORK -> {
                    Text("${stringResource(R.string.start_time)}: ${dateTimeFormat.format(event.startTime)} - ${event.startLocation ?: ""}")
                    event.endTime?.let { Text("${stringResource(R.string.end_time)}: ${dateTimeFormat.format(it)} - ${event.endLocation ?: ""}") }
                    Text("${stringResource(R.string.net_work_duration)}: ${viewModel.formatDuration(item.netWorkDuration)}")
                    Text("${stringResource(R.string.total_break)}: ${viewModel.formatDuration(item.totalBreakDuration)}")
                    Text("${stringResource(R.string.car_plate)}: ${event.carPlate ?: notAvailable}")
                    Text("${stringResource(R.string.start_km)}: ${event.startOdometer ?: notAvailable}")
                    event.endOdometer?.let { Text("${stringResource(R.string.end_km)}: $it") }
                }
                EventType.VACATION, EventType.SICK_LEAVE -> {
                    event.startDate?.let { Text("${stringResource(R.string.start_date)}: ${dateFormat.format(it)}") }
                    event.endDate?.let { Text("${stringResource(R.string.end_date)}: ${dateFormat.format(it)}") }
                }
            }
        }
    }
}

@Composable
fun RefuelResultCard(event: RefuelEvent) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN) }
    Card(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(stringResource(R.string.refueling), style = MaterialTheme.typography.titleMedium)
            Text("${stringResource(R.string.date_time)}: ${dateFormat.format(event.timestamp)}")
            Text("${stringResource(R.string.type)}: ${event.fuelType}")
            Text("${stringResource(R.string.amount)}: ${event.fuelAmount} ${stringResource(R.string.liter_unit)}")
            Text("${stringResource(R.string.payment_method)}: ${event.paymentMethod}")
            Text("${stringResource(R.string.car_plate)}: ${event.carPlate}")
            Text("${stringResource(R.string.odometer)}: ${event.odometer}")
        }
    }
}

@Composable
fun LoadingResultCard(event: LoadingEvent, viewModel: MainViewModel) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN) }
    val notAvailable = stringResource(R.string.not_available)
    Card(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(stringResource(R.string.loading), style = MaterialTheme.typography.titleMedium)
            event.startTime?.let { Text("${stringResource(R.string.start_time)}: ${dateFormat.format(it)}") }
            event.endTime?.let { Text("${stringResource(R.string.end_time)}: ${dateFormat.format(it)}") }
            val duration = (event.endTime?.time ?: 0) - (event.startTime?.time ?: 0)
            if (duration > 0) {
                Text("${stringResource(R.string.duration)}: ${viewModel.formatDuration(duration)}")
            }
            Text("${stringResource(R.string.location)}: ${event.location ?: notAvailable}")
        }
    }
}
