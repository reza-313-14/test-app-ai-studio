package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.AppDatabase
import com.example.data.database.AlarmConfig
import com.example.data.database.AlarmLog
import com.example.data.repository.AlarmRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AlarmViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val db by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy { AlarmRepository(db.alarmDao) }
    private val viewModel: AlarmViewModel by lazy {
        AlarmViewModel.Factory(repository).create(AlarmViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val configState = viewModel.configState.collectAsStateWithLifecycle().value
            val isDark = when {
                configState == null || configState.useSystemTheme -> androidx.compose.foundation.isSystemInDarkTheme()
                else -> configState.isDarkMode
            }

            MyApplicationTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IntervalAlarmAppContents(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalAlarmAppContents(viewModel: AlarmViewModel) {
    val context = LocalContext.current
    val config = viewModel.configState.collectAsStateWithLifecycle().value ?: AlarmConfig()
    val logs = viewModel.logsState.collectAsStateWithLifecycle().value

    var showSettingsMenu by remember { mutableStateOf(false) }
    var customInputMinutes by remember { mutableStateOf(config.intervalMinutes.toString()) }

    // Synchronize local input state with database state if changed
    LaunchedEffect(config.intervalMinutes) {
        customInputMinutes = config.intervalMinutes.toString()
    }

    // Permission launcher for POST_NOTIFICATIONS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleAlarm(context)
            Toast.makeText(context, "Notification permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notification permission is required to trigger intervals.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("scaffold"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Intervals",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        letterSpacing = (-0.5).sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Navigation drawer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsMenu = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings Menu",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // PULSING TICKER STATUS PANEL
            StatusCard(config = config)

            // POWER CONTROLLER (Toggle On/Off)
            Button(
                onClick = {
                    if (!config.isEnabled) {
                        // Turning ON: Check permission on Android 13+
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val status = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                            if (status != PackageManager.PERMISSION_GRANTED) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.toggleAlarm(context)
                            }
                        } else {
                            viewModel.toggleAlarm(context)
                        }
                    } else {
                        // Turning OFF: Just toggle off
                        viewModel.toggleAlarm(context)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("toggle_alarm_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (config.isEnabled) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                    contentColor = if (config.isEnabled) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(28.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (config.isEnabled) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = "Control icon",
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (config.isEnabled) "Stop Alarm" else "Start Alarm",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // INTERVAL SELECTORS
            IntervalControlCard(
                config = config,
                customInputMinutes = customInputMinutes,
                onPresetSelected = { preset ->
                    viewModel.updateInterval(preset)
                },
                onManualInputChanged = { text ->
                    customInputMinutes = text.filter { it.isDigit() }
                    val parsed = customInputMinutes.toIntOrNull()
                    if (parsed != null && parsed > 0) {
                        viewModel.updateInterval(parsed)
                    }
                },
                onIncrement = {
                    val current = config.intervalMinutes
                    val next = current + 1
                    viewModel.updateInterval(next)
                },
                onDecrement = {
                    val current = config.intervalMinutes
                    if (current > 1) {
                        val next = current - 1
                        viewModel.updateInterval(next)
                    }
                }
            )

            // HISTORICAL DATABASE LOG FEED
            HistoricalLogsSection(
                logs = logs,
                onClearHistory = { viewModel.clearHistory() }
            )
        }
    }

    // SETTINGS OVERLAY SHEET
    if (showSettingsMenu) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            SettingsOverlayPanel(
                config = config,
                onToggleSound = { viewModel.toggleSound(it) },
                onToggleVibration = { viewModel.toggleVibration(it) },
                onToggleThemeMode = { useSys, isDark -> viewModel.toggleDarkMode(useSys, isDark) },
                onClose = { showSettingsMenu = false }
            )
        }
    }
}

@Composable
fun StatusCard(config: AlarmConfig) {
    var timeLeftUi by remember { mutableStateOf("Inactive") }
    var progressFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(config.isEnabled, config.nextTriggeredTime, config.intervalMinutes) {
        if (config.isEnabled && config.nextTriggeredTime > System.currentTimeMillis()) {
            while (true) {
                val now = System.currentTimeMillis()
                val totalIntervalMs = config.intervalMinutes * 60 * 1000L
                val elapsedMs = config.nextTriggeredTime - now
                if (elapsedMs <= 0) {
                    timeLeftUi = "Completed!"
                    progressFraction = 1f
                    break
                } else {
                    val totalMinutes = elapsedMs / 60000
                    val seconds = (elapsedMs % 60000) / 1000
                    timeLeftUi = String.format("%02d:%02d left", totalMinutes, seconds)
                    progressFraction = (elapsedMs.toFloat() / totalIntervalMs).coerceIn(0f, 1f)
                }
                kotlinx.coroutines.delay(1000L)
            }
        } else {
            timeLeftUi = "Inactive"
            progressFraction = 0f
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("status_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (config.isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = if (config.isEnabled) "ACTIVE LOOP" else "PAUSED LOOP",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (config.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Every ${config.intervalMinutes}m",
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp,
                        color = if (config.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (config.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (config.isEnabled) Icons.Default.Notifications else Icons.Default.PlayArrow,
                        contentDescription = "Status indicator icon",
                        tint = if (config.isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Progress slide line matching
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            color = if (config.isEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                            },
                            shape = RoundedCornerShape(3.dp)
                        )
                ) {
                    val animateFraction by animateFloatAsState(
                        targetValue = progressFraction,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "progress"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = if (config.isEnabled) animateFraction else 0f)
                            .background(
                                color = if (config.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (config.isEnabled) "Loop running continuously" else "Configure settings and click start",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (config.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = if (config.isEnabled) timeLeftUi else "Inactive",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (config.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("next_alarm_time")
                    )
                }
            }
        }
    }
}

@Composable
fun IntervalControlCard(
    config: AlarmConfig,
    customInputMinutes: String,
    onPresetSelected: (Int) -> Unit,
    onManualInputChanged: (String) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    var isCustomSelected by remember { mutableStateOf(false) }
    
    // Auto-detect if custom is active (meaning not one of the presets)
    val isPreset15 = config.intervalMinutes == 15
    val isPreset30 = config.intervalMinutes == 30
    val isPreset60 = config.intervalMinutes == 60
    val isAnyPreset = isPreset15 || isPreset30 || isPreset60

    LaunchedEffect(config.intervalMinutes) {
        if (!isAnyPreset) {
            isCustomSelected = true
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("interval_control_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Set New Interval",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // 2x2 grid structure exactly matching Tailwind layout:
            // "grid grid-cols-2 gap-3"
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Item 1: 15 min button
                    PresetGridItem(
                        minutes = 15,
                        label = "15 min",
                        isSelected = isPreset15 && !isCustomSelected,
                        onClick = {
                            isCustomSelected = false
                            onPresetSelected(15)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Item 2: 30 min button
                    PresetGridItem(
                        minutes = 30,
                        label = "30 min",
                        isSelected = isPreset30 && !isCustomSelected,
                        onClick = {
                            isCustomSelected = false
                            onPresetSelected(30)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Item 3: 1 hour button
                    PresetGridItem(
                        minutes = 60,
                        label = "1 hour",
                        isSelected = isPreset60 && !isCustomSelected,
                        onClick = {
                            isCustomSelected = false
                            onPresetSelected(60)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Item 4: Custom button
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clickable {
                                isCustomSelected = true
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCustomSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit symbol",
                                tint = if (isCustomSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Custom",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (isCustomSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Animated manual input block if custom option is selected
            AnimatedVisibility(
                visible = isCustomSelected,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    // MINUS ACTION BUTTON
                    IconButton(
                        onClick = onDecrement,
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            .testTag("decrement_button")
                    ) {
                        Text(
                            text = "–",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    // DIGIT OUTLINED FIELD
                    OutlinedTextField(
                        value = customInputMinutes,
                        onValueChange = onManualInputChanged,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("interval_input_field"),
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        label = { Text("Minutes") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // PLUS ACTION BUTTON
                    IconButton(
                        onClick = onIncrement,
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            .testTag("increment_button")
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PresetGridItem(
    minutes: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(56.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun getRelativeTimeString(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60000L -> "Just now"
        diff < 3600000L -> "${diff / 60000L}m ago"
        diff < 86400000L -> "${diff / 3600000L}h ago"
        else -> SimpleDateFormat("MM/dd hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
fun ColumnScope.HistoricalLogsSection(
    logs: List<AlarmLog>,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("logs_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Sessions",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (logs.isNotEmpty()) {
                Text(
                    text = "Clear All",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clickable { onClearHistory() }
                        .padding(4.dp)
                        .testTag("clear_logs_text")
                )
            }
        }

        if (logs.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Empty logs icon",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No sessions recorded yet.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Start the interval loop to record logs in the database.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(logs) { log ->
                    val relativeTime = remember(log.timestamp) {
                        getRelativeTimeString(log.timestamp)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circular icon container
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (log.isSuccessful) Icons.Default.Check else Icons.Default.Refresh,
                                    contentDescription = "Trigger status",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Interval Event Triggered",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Every ${log.intervalMinutes} mins • $relativeTime",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Arrow details",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsOverlayPanel(
    config: AlarmConfig,
    onToggleSound: (Boolean) -> Unit,
    onToggleVibration: (Boolean) -> Unit,
    onToggleThemeMode: (useSystem: Boolean, isDark: Boolean) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Preferences",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close settings"
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Notification Sound & Vibration",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notification Sound",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Play standard ring alert on completion",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.soundEnabled,
                        onCheckedChange = onToggleSound,
                        modifier = Modifier.testTag("sound_toggle")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Physical Haptic Vibration",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Vibrate device when triggered",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.vibrationEnabled,
                        onCheckedChange = onToggleVibration,
                        modifier = Modifier.testTag("vibration_toggle")
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Visual Appearance Theme",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Follow System Setting",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Match device dark / light preference",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.useSystemTheme,
                        onCheckedChange = { useSys ->
                            onToggleThemeMode(useSys, config.isDarkMode)
                        },
                        modifier = Modifier.testTag("system_theme_toggle")
                    )
                }

                if (!config.useSystemTheme) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Force Dark Mode",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Turn on dark appearance permanently",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.isDarkMode,
                            onCheckedChange = { isDark ->
                                onToggleThemeMode(false, isDark)
                            },
                            modifier = Modifier.testTag("dark_mode_toggle")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
