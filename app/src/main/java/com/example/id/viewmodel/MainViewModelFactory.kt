package com.example.id.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.id.data.AppRepository
import com.example.id.util.DataManager

class MainViewModelFactory(
    private val application: Application, 
    private val repository: AppRepository, 
    private val userId: String, 
    private val userRole: String,
    private val dataManager: DataManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repository, userId, userRole, dataManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
