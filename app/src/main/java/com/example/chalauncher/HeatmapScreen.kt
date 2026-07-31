package com.example.chalauncher

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

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
    val context = LocalContext.current

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
