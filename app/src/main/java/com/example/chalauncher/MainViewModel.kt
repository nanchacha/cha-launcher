package com.example.chalauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppState {
    LOADING, SETUP, HOME
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)

    private val _appState = MutableStateFlow(AppState.LOADING)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()
    
    private var allAppsCache: List<AppInfo> = emptyList()

    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<AppInfo>>(emptyList())
    val searchResults: StateFlow<List<AppInfo>> = _searchResults.asStateFlow()

    init {
        checkInitialState()
        viewModelScope.launch {
            allAppsCache = repository.getInstalledApps(filterSelected = false)
        }
    }

    private fun checkInitialState() {
        if (repository.isSetupCompleted()) {
            _appState.value = AppState.HOME
            loadApps(filterSelected = true)
        } else {
            _appState.value = AppState.SETUP
            loadApps(filterSelected = false)
        }
    }

    fun loadApps(filterSelected: Boolean) {
        viewModelScope.launch {
            val fetched = repository.getInstalledApps(filterSelected = filterSelected)
            _apps.value = fetched
            // Update cache silently
            allAppsCache = repository.getInstalledApps(filterSelected = false)
            updateSearchQuery(_searchQuery.value)
        }
    }

    fun toggleAppSelection(packageName: String) {
        val current = _selectedPackages.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            if (current.size < 10) {
                current.add(packageName)
            }
        }
        _selectedPackages.value = current
    }

    fun completeSetup() {
        if (_selectedPackages.value.isNotEmpty()) {
            repository.saveSelectedAppPackages(_selectedPackages.value)
            repository.setSetupCompleted(true)
            _appState.value = AppState.HOME
            loadApps(filterSelected = true)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
        } else {
            _searchResults.value = allAppsCache.filter {
                it.name.startsWith(query, ignoreCase = true) || 
                it.packageName.split('.').any { segment -> 
                    segment.startsWith(query, ignoreCase = true) 
                }
            }
        }
    }

    fun onAppClicked(appInfo: AppInfo) {
        repository.incrementClickCount(appInfo.packageName)
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        loadApps(filterSelected = true)
    }
}
