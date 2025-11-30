package com.example.id.ui.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.id.R
import com.example.id.data.entities.LoadingEvent
import com.example.id.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun EditLoadingScreen(navController: NavController, viewModel: MainViewModel, loadingId: Long) {

    LaunchedEffect(loadingId) {
        viewModel.loadLoadingForEdit(loadingId)
    }

    val eventToEdit by viewModel.editingEvent.collectAsState()

    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }

    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(eventToEdit) {
        (eventToEdit as? LoadingEvent)?.let {
            it.startTime?.let { date -> startTime = dateFormat.format(date) }
            it.endTime?.let { date -> endTime = dateFormat.format(date) }
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
    ) {
        Text(stringResource(R.string.edit_loading), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = startTime,
            onValueChange = { startTime = it },
            label = { Text(stringResource(R.string.start_time_full)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = endTime,
            onValueChange = { endTime = it },
            label = { Text(stringResource(R.string.end_time_full)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text(stringResource(R.string.location)) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                (eventToEdit as? LoadingEvent)?.let {
                    val updatedEvent = it.copy(
                        startTime = try { dateFormat.parse(startTime) } catch (e: Exception) { it.startTime },
                        endTime = try { dateFormat.parse(endTime) } catch (e: Exception) { it.endTime },
                        location = location
                    )
                    viewModel.updateLoading(updatedEvent)
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
