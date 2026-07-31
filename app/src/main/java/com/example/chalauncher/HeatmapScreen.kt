package com.example.chalauncher

import android.content.Intent
import android.provider.Settings
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.example.chalauncher.data.WeatherState
import com.example.chalauncher.ThemeMode
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.annotation.SuppressLint
import kotlinx.coroutines.delay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeatmapScreen(viewModel: MainViewModel) {
    val apps by viewModel.apps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val allApps by viewModel.allApps.collectAsState()
    var showAllApps by remember { mutableStateOf(false) }
    var appToRemove by remember { mutableStateOf<AppInfo?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showPinApps by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val weatherState by viewModel.weatherState.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    
    var hasLocationPermission by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) 
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasLocationPermission = isGranted
        }
    )

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            while (true) {
                fetchLocationAndWeather(context, viewModel)
                delay(15 * 60 * 1000L) // 15 minutes
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    if (showAllApps) {
        AllAppsOverlay(
            allApps = allApps,
            onClose = { showAllApps = false },
            onAppClick = { app ->
                app.launchIntent?.let {
                    viewModel.onAppClicked(app)
                    context.startActivity(it)
                }
                showAllApps = false
            }
        )
        return
    }

    if (showPinApps) {
        val allAppsList by viewModel.allApps.collectAsState()
        PinAppsOverlay(
            allApps = allAppsList,
            selectedPackages = apps.map { it.packageName }.toSet(),
            onClose = { showPinApps = false },
            onTogglePin = { app -> viewModel.toggleAppPin(app.packageName) }
        )
        return
    }

    if (appToRemove != null) {
        AlertDialog(
            onDismissRequest = { appToRemove = null },
            title = { Text("히트맵에서 제거") },
            text = { Text("${appToRemove?.name} 앱을 히트맵에서 제거하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    appToRemove?.let { viewModel.removeApp(it.packageName) }
                    appToRemove = null
                }) {
                    Text("제거")
                }
            },
            dismissButton = {
                TextButton(onClick = { appToRemove = null }) {
                    Text("취소")
                }
            }
        )
    }

    if (apps.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading apps...")
        }
        return
    }

    val maxClicks = apps.maxOfOrNull { it.clickCount } ?: 1

    Column(modifier = Modifier.fillMaxSize()) {
        DateWeatherHeader(weatherState)
        
        Box(modifier = Modifier.weight(1f)) {
            // Background Treemap
            TreemapLayout(modifier = Modifier.fillMaxSize(), items = apps) { app ->
                val intensity = (app.clickCount.toFloat() / maxClicks).coerceIn(0.2f, 1f)
                val backgroundColor = Color(0xFF4CAF50).copy(alpha = intensity)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .border(1.dp, Color.Black)
                        .combinedClickable(
                            onClick = {
                                app.launchIntent?.let {
                                    viewModel.onAppClicked(app)
                                    context.startActivity(it)
                                }
                            },
                            onLongClick = {
                                appToRemove = app
                            }
                        )
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        app.icon.toBitmap(width = 120, height = 120)?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = app.name,
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(4.dp)
                            )
                        }
                        if (app.clickCount > 4) {
                            Text(
                                text = app.name,
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }

            // Search Overlay (when querying)
            if (searchQuery.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(searchResults) { app ->
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clickable {
                                        app.launchIntent?.let {
                                            viewModel.onAppClicked(app)
                                            context.startActivity(it)
                                        }
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                app.icon.toBitmap(width = 120, height = 120)?.let { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = app.name,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = app.name,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

        }

        // Search Bar Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search apps...") },
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Text(
                            text = "☰",
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("전체 앱") },
                            onClick = {
                                menuExpanded = false
                                showAllApps = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("앱 추가/제거 (고정)") },
                            onClick = {
                                menuExpanded = false
                                showPinApps = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("기본 런처 설정") },
                            onClick = {
                                menuExpanded = false
                                val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                                context.startActivity(intent)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("초기화") },
                            onClick = {
                                menuExpanded = false
                                viewModel.resetSetup()
                            }
                        )
                        DropdownMenuItem(
                            text = { 
                                val themeText = when (themeMode) {
                                    ThemeMode.SYSTEM -> "테마: 시스템"
                                    ThemeMode.LIGHT -> "테마: 라이트"
                                    ThemeMode.DARK -> "테마: 다크"
                                }
                                Text(themeText)
                            },
                            onClick = {
                                viewModel.toggleThemeMode()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllAppsOverlay(allApps: List<AppInfo>, onClose: () -> Unit, onAppClick: (AppInfo) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("전체 앱") },
                navigationIcon = {
                    Text(
                        text = "←",
                        modifier = Modifier
                            .padding(16.dp)
                            .clickable { onClose() },
                        fontSize = 24.sp
                    )
                }
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = paddingValues,
            modifier = Modifier.fillMaxSize()
        ) {
            items(allApps) { app ->
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { onAppClick(app) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    app.icon.toBitmap(width = 120, height = 120)?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = app.name,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = app.name,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinAppsOverlay(
    allApps: List<AppInfo>, 
    selectedPackages: Set<String>, 
    onClose: () -> Unit, 
    onTogglePin: (AppInfo) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("앱 추가/제거 (고정)") },
                navigationIcon = {
                    Text(
                        text = "←",
                        modifier = Modifier
                            .padding(16.dp)
                            .clickable { onClose() },
                        fontSize = 24.sp
                    )
                }
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = paddingValues,
            modifier = Modifier.fillMaxSize()
        ) {
            items(allApps) { app ->
                val isSelected = selectedPackages.contains(app.packageName)
                val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(backgroundColor)
                        .clickable { onTogglePin(app) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        app.icon.toBitmap(width = 120, height = 120)?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = app.name,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        if (isSelected) {
                            Text(
                                text = "✓",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .offset(x = 8.dp, y = (-8).dp)
                                    .background(Color.White, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = app.name,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun DateWeatherHeader(weatherState: WeatherState) {
    val currentDate = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = currentDate,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "좋은 하루 되세요!",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (weatherState) {
                is WeatherState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                is WeatherState.Success -> {
                    // map WMO weather code to emoji
                    val emoji = when (weatherState.weatherCode) {
                        0 -> "☀️" // Clear
                        1, 2, 3 -> "⛅" // Partly cloudy
                        45, 48 -> "🌫️" // Fog
                        51, 53, 55, 56, 57 -> "🌧️" // Drizzle
                        61, 63, 65, 66, 67 -> "🌧️" // Rain
                        71, 73, 75, 77 -> "❄️" // Snow
                        80, 81, 82 -> "🌧️" // Rain showers
                        85, 86 -> "❄️" // Snow showers
                        95, 96, 99 -> "⛈️" // Thunderstorm
                        else -> "☁️"
                    }
                    Text(
                        text = emoji,
                        fontSize = 24.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "${weatherState.temperature}°C",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                is WeatherState.Error -> {
                    Text(
                        text = "날씨 오류",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    Text(
                        text = "날씨 정보 없음",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
fun fetchLocationAndWeather(context: Context, viewModel: MainViewModel) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            viewModel.fetchWeather(location.latitude, location.longitude)
        } else {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { currentLoc ->
                    if (currentLoc != null) {
                        viewModel.fetchWeather(currentLoc.latitude, currentLoc.longitude)
                    }
                }
        }
    }
}
