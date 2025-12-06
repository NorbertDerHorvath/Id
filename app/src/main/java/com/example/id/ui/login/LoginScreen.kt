package com.example.id.ui.login

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.id.viewmodel.LoginUiState
import com.example.id.viewmodel.MainViewModel
import com.example.id.viewmodel.RegisterUiState

@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel()
) {
    var showLogin by remember { mutableStateOf(true) }

    if (showLogin) {
        LoginContent(viewModel = viewModel, onSwitchToRegister = { showLogin = false }, navController = navController)
    } else {
        RegisterContent(viewModel = viewModel, onSwitchToLogin = { showLogin = true })
    }
}

@Composable
fun LoginContent(
    viewModel: MainViewModel,
    onSwitchToRegister: () -> Unit,
    navController: NavController
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val loginState by viewModel.loginState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is LoginUiState.Success -> {
                Toast.makeText(context, "Login Successful!", Toast.LENGTH_SHORT).show()
                navController.navigate("main") {
                    popUpTo("login") { inclusive = true }
                }
            }
            is LoginUiState.Error -> {
                Toast.makeText(context, "Login Failed: ${state.message}", Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Login", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.login(username, password) }, enabled = loginState !is LoginUiState.Loading, modifier = Modifier.fillMaxWidth()) {
            if (loginState is LoginUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Login")
            }
        }
        TextButton(onClick = onSwitchToRegister) {
            Text("Don't have an account? Register")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterContent(
    viewModel: MainViewModel,
    onSwitchToLogin: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("user") }
    var companyName by remember { mutableStateOf("") }
    var adminEmail by remember { mutableStateOf("") }
    val registerState by viewModel.registerState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(registerState) {
        when (registerState) {
            is RegisterUiState.Success -> {
                Toast.makeText(context, "Registration Successful! Please log in.", Toast.LENGTH_LONG).show()
                viewModel.resetRegisterState()
                onSwitchToLogin()
            }
            is RegisterUiState.Error -> {
                Toast.makeText(context, "Registration Failed: ${(registerState as RegisterUiState.Error).message}", Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Register", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = role,
                onValueChange = {},
                label = { Text("Role") },
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("User") }, onClick = { role = "user"; expanded = false })
                DropdownMenuItem(text = { Text("Admin") }, onClick = { role = "admin"; expanded = false })
            }
        }

        if (role == "admin") {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = companyName, onValueChange = { companyName = it }, label = { Text("Company Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = adminEmail, onValueChange = { adminEmail = it }, label = { Text("Admin Email") }, modifier = Modifier.fillMaxWidth())
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.register(username, password, role, if (role == "admin") companyName else null, if (role == "admin") adminEmail else null) }, 
            enabled = registerState !is RegisterUiState.Loading, 
            modifier = Modifier.fillMaxWidth()
        ) {
            if (registerState is RegisterUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Register")
            }
        }
        TextButton(onClick = onSwitchToLogin) {
            Text("Already have an account? Login")
        }
    }
}
