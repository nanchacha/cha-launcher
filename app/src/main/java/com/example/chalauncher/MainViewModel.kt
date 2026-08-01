package com.example.chalauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.chalauncher.data.WeatherRepository
import com.example.chalauncher.data.WeatherState

enum class AppState {
    LOADING, SETUP, HOME
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)
    private val weatherRepository = WeatherRepository()

    private val _appState = MutableStateFlow(AppState.LOADING)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _themeMode = MutableStateFlow(repository.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()
    
    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()

    internal var allAppsCache: List<AppInfo> = emptyList()

    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<AppInfo>>(emptyList())
    val searchResults: StateFlow<List<AppInfo>> = _searchResults.asStateFlow()

    private val _weatherState = MutableStateFlow<WeatherState>(WeatherState.Initial)
    val weatherState: StateFlow<WeatherState> = _weatherState.asStateFlow()

    private val _selectedCategory = MutableStateFlow<Int?>(null)
    val selectedCategory: StateFlow<Int?> = _selectedCategory.asStateFlow()

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
            val currentCategory = _selectedCategory.value
            val actualFilterSelected = filterSelected && currentCategory == null
            val fetched = repository.getInstalledApps(filterSelected = actualFilterSelected)
            
            _apps.value = if (currentCategory != null) {
                fetched.filter { it.appCategory == currentCategory }
            } else {
                fetched
            }
            // Update cache silently
            val allAppsList = repository.getInstalledApps(filterSelected = false)
            allAppsCache = allAppsList
            _allApps.value = allAppsList.sortedBy { it.name.lowercase() }
            updateSearchQuery(_searchQuery.value)
        }
    }

    fun setCategoryFilter(category: Int?) {
        _selectedCategory.value = category
        loadApps(filterSelected = _appState.value == AppState.HOME)
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

    fun toggleAppPin(packageName: String) {
        if (repository.getSelectedAppPackages().contains(packageName)) {
            repository.removeAppFromSelected(packageName)
        } else {
            repository.addAppToSelected(packageName)
        }
        loadApps(filterSelected = true)
    }

    fun completeSetup() {
        if (_selectedPackages.value.isNotEmpty()) {
            repository.saveSelectedAppPackages(_selectedPackages.value)
            repository.setSetupCompleted(true)
            _searchQuery.value = ""
            _searchResults.value = emptyList()
            _appState.value = AppState.HOME
            loadApps(filterSelected = true)
        }
    }

    fun resetSetup() {
        repository.resetSetupState()
        _selectedPackages.value = emptySet()
        repository.saveSelectedAppPackages(emptySet())
        _appState.value = AppState.SETUP
        loadApps(filterSelected = false)
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

    fun onAppClicked(app: AppInfo) {
        repository.addAppToSelected(app.packageName)
        repository.incrementClickCount(app.packageName)
        loadApps(filterSelected = true)
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    fun removeApp(packageName: String) {
        repository.removeAppFromSelected(packageName)
        loadApps(filterSelected = true)
    }

    fun fetchWeather(lat: Double, lon: Double) {
        _weatherState.value = WeatherState.Loading
        viewModelScope.launch {
            _weatherState.value = weatherRepository.fetchWeather(lat, lon)
        }
    }

    fun toggleThemeMode() {
        val current = _themeMode.value
        val next = when (current) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        _themeMode.value = next
        repository.setThemeMode(next)
    }
}
