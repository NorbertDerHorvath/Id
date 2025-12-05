package com.example.id.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.id.data.entities.LoadingEvent
import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.WorkdayEvent
import com.example.id.viewmodel.MainViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val reportResults by viewModel.reportResults.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { viewModel.fetchData() }) {
                Text("Adatok lekérdezése")
            }
            LazyColumn {
                items(reportResults) { event ->
                    when (event) {
                        is WorkdayEvent -> {
                            Text("Munkanap: ${event.startTime} - ${event.endTime}")
                        }
                        is RefuelEvent -> {
                            Text("Tankolás: ${event.timestamp} - ${event.fuelAmount}L")
                        }
                        is LoadingEvent -> {
                            Text("Rakodás: ${event.startTime} - ${event.location}")
                        }
                    }
                }
            }
        }
    }
}
