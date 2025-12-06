package com.example.id.ui.query

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.id.viewmodel.MainViewModel

@Composable
fun QueryResultScreen(
    navController: NavController,
    viewModel: MainViewModel,
    queryType: String
) {
    val workdayResults by viewModel.workdayQueryResults.collectAsState()
    val refuelResults by viewModel.refuelQueryResults.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (queryType == "workday") {
                items(workdayResults) {
                    WorkdayResultItem(it)
                    Divider()
                }
            } else {
                items(refuelResults) {
                    RefuelResultItem(it)
                    Divider()
                }
            }
        }
        Row {
            Button(onClick = {
                if (queryType == "workday") {
                    viewModel.clearWorkdayResults()
                } else {
                    viewModel.clearRefuelResults()
                }
                navController.popBackStack()
            }) {
                Text("Vissza")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { viewModel.refreshLastQuery() }) {
                Text("Frissítés")
            }
        }
    }
}