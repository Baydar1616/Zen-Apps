package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.database.Reminder
import com.example.viewmodel.ReminderViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AquariumMainScreen(
    viewModel: ReminderViewModel = viewModel()
) {
    val reminders by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    var selectedReminderId by remember { mutableStateOf<Int?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val foodDrops = remember { mutableStateListOf<FoodDropEvent>() }

    // Sound volumes state (connected to SoundSynthesizer)
    var masterVolume by remember { mutableStateOf(viewModel.synth.totalVolume) }
    var bubbleVolume by remember { mutableStateOf(viewModel.synth.bubbleVolume) }
    var melodyVolume by remember { mutableStateOf(viewModel.synth.melodyVolume) }
    var splashVolume by remember { mutableStateOf(viewModel.synth.splashVolume) }
    var isMuted by remember { mutableStateOf(false) }

    val selectedReminder = reminders.find { it.id == selectedReminderId }

    // Update synthesis engines in real time on state adjustments
    LaunchedEffect(masterVolume, bubbleVolume, melodyVolume, splashVolume, isMuted) {
        viewModel.synth.totalVolume = if (isMuted) 0f else masterVolume
        viewModel.synth.bubbleVolume = bubbleVolume
        viewModel.synth.melodyVolume = melodyVolume
        viewModel.synth.splashVolume = splashVolume
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F111E))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Zen Fish Pond",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Swipe down a tab into the water to feed & solve",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFA1A9CA),
                                fontSize = 11.sp
                            )
                        )
                    }

                    // Button to add reflection reminders
                    IconButton(
                        onClick = { showCreateDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF0077B6))
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Reflection Reminder", tint = Color.White)
                    }
                }
            }
        },
        containerColor = Color(0xFF0A0C16) // Deep Dark Oceanic backing color
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            
            // Section 1: Swipable Ocean-Colored Tab Stack (Side-to-Side above water surface)
            Text(
                text = "Reminder Tabs (Swipe down to drop food 🍲)",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = selectedTheme.primaryTextColor
                ),
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )

            if (reminders.none { !it.isCompleted }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(Color(0xFF131525), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎉 All tasks fed to the Koi fish! Add a task 🐠",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFA5ABC7)),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(reminders.filter { !it.isCompleted }, key = { it.id }) { reminder ->
                        var dragY by remember { mutableStateOf(0f) }
                        val animatedDragY by animateFloatAsState(
                            targetValue = dragY,
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "dragY_spring"
                        )

                        val palette = getFishColorPalette(reminder.fishType)
                        val formattedDate = remember(reminder.dueDate) {
                            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                            sdf.format(Date(reminder.dueDate))
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .width(190.dp)
                                .height(110.dp)
                                .graphicsLayer {
                                    translationY = animatedDragY
                                }
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFF0F2633), Color(0xFF1B3D55))
                                    ),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable { selectedReminderId = reminder.id }
                                .draggable(
                                    orientation = Orientation.Vertical,
                                    state = rememberDraggableState { delta ->
                                        dragY = (dragY + delta).coerceAtLeast(0f)
                                    },
                                    onDragStopped = {
                                        if (dragY > 150f) {
                                            // Dropped into the water! Drop a custom food drop event!
                                            val indexInGroup = reminders.indexOf(reminder)
                                            val xFraction = 0.2f + (indexInGroup.toFloat() / reminders.size.coerceAtLeast(1).toFloat()) * 0.6f
                                            
                                            foodDrops.add(
                                                FoodDropEvent(
                                                    id = System.nanoTime(),
                                                    reminderId = reminder.id,
                                                    xFraction = xFraction.coerceIn(0.1f, 0.9f)
                                                )
                                            )
                                            // Play beautiful splash audio cues
                                            viewModel.synth.triggerSplash()
                                        }
                                        dragY = 0f
                                    }
                                )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = reminder.title,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = getFishIconText(reminder.fishType),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontSize = 18.sp
                                    )
                                }

                                Text(
                                    text = reminder.description.ifEmpty { "Drag down to drop feed! 🍲" },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFA2A9CE),
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(getPriorityBgColor(reminder.priority))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = reminder.priority,
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                    }

                                    Text(
                                        text = "Due $formattedDate",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFFD0DBE8),
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Realistic interactive side-view fish pond viewport (Water body is right below tabs)
            AquariumView(
                reminders = reminders,
                selectedReminder = selectedReminder,
                onReminderSelected = { r -> selectedReminderId = r.id },
                onFoodEaten = { reminder ->
                    // Set reminder progress and complete status when food is eaten
                    viewModel.toggleComplete(reminder)
                },
                onWaterTapped = { viewModel.splashPond() },
                foodDrops = foodDrops,
                onFoodDropProcessed = { dropId ->
                    foodDrops.removeAll { it.id == dropId }
                },
                onBubblePopSFX = {
                    viewModel.synth.triggerBubblePop()
                },
                themePreset = selectedTheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f)
            )

            // Section 3: Interactive details drawer of currently tapped/selected Koi fish
            AnimatedVisibility(
                visible = selectedReminder != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (selectedReminder != null) {
                    val palette = getFishColorPalette(selectedReminder.fishType)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D213C)),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = selectedReminder.title,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(getPriorityBgColor(selectedReminder.priority))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = selectedReminder.priority,
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = selectedReminder.description.ifEmpty { "No extra details." },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFFA5ABC7)
                                        )
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteReminder(selectedReminder)
                                            selectedReminderId = null
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete Reminder", tint = Color(0xFFEF5350))
                                    }
                                    TextButton(
                                        onClick = { selectedReminderId = null },
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("Close", color = Color(0xFFA5ABC7))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Interactive sliding progress indicator!
                            Text(
                                "Koi Development: ${selectedReminder.progress}%",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = palette.bodyColor
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Slider(
                                    value = selectedReminder.progress.toFloat(),
                                    onValueChange = { newValue ->
                                        viewModel.updateProgress(selectedReminder, newValue.toInt())
                                    },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = palette.bodyColor,
                                        activeTrackColor = palette.bodyColor,
                                        inactiveTrackColor = Color(0xFF323659)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                Button(
                                    onClick = { viewModel.toggleComplete(selectedReminder) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedReminder.isCompleted) Color(0xFF2D8F60) else Color(0xFF0077B6)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(if (selectedReminder.isCompleted) "Grown 🎨" else "Solve Task", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: Quick-Actions and Volume Sound Mixers panel
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16192C)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF212543))
                                    .clickable { isMuted = !isMuted },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isMuted) "🔇" else "🔊",
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ambient Environment Soundscape",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            )
                        }

                        // Feed button
                        Button(
                            onClick = {
                                viewModel.feedFish()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F1C)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                "Feed Flakes 🍲",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row of sub-volume sliders (Bubbling, Nature Bells, Slosh)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Bubbling water sound slider
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Bubbles 💧",
                                style = TextStyle(fontSize = 10.sp, color = Color(0xFFA2A9CE), fontWeight = FontWeight.Bold)
                            )
                            Slider(
                                value = bubbleVolume,
                                onValueChange = { bubbleVolume = it },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00B4D8),
                                    activeTrackColor = Color(0xFF00B4D8),
                                    inactiveTrackColor = Color(0xFF252A4A)
                                )
                            )
                        }

                        // Calming Pentatonic Bells melody slider
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Nature Melody 🍃",
                                style = TextStyle(fontSize = 10.sp, color = Color(0xFFA2A9CE), fontWeight = FontWeight.Bold)
                            )
                            Slider(
                                value = melodyVolume,
                                onValueChange = { melodyVolume = it },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF9B5DE5),
                                    activeTrackColor = Color(0xFF9B5DE5),
                                    inactiveTrackColor = Color(0xFF252A4A)
                                )
                            )
                        }

                        // Splash sound effect slider
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Splashing 🐟",
                                style = TextStyle(fontSize = 10.sp, color = Color(0xFFFF5964), fontWeight = FontWeight.Bold)
                            )
                            Slider(
                                value = splashVolume,
                                onValueChange = { splashVolume = it },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFF5964),
                                    activeTrackColor = Color(0xFFFF5964),
                                    inactiveTrackColor = Color(0xFF252A4A)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222749)))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Zen Visual Themes (Studio Ghibli & Cinematic Indie)",
                        style = TextStyle(fontSize = 11.sp, color = Color(0xFFA2A9CE), fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        com.example.ui.theme.ThemePreset.values().forEach { themeItem ->
                            val isSelected = selectedTheme == themeItem
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.selectTheme(themeItem) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFF2C314E) else Color(0xFF1B1E36)
                                ),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, themeItem.primaryTextColor) else null
                            ) {
                                Column(
                                    modifier = Modifier.padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = when(themeItem) {
                                            com.example.ui.theme.ThemePreset.CLASSIC_ABYSSAL -> "🌊"
                                            com.example.ui.theme.ThemePreset.GHIBLI_SUNSET -> "🌅"
                                            com.example.ui.theme.ThemePreset.MOSSY_EMERALD -> "🍃"
                                            com.example.ui.theme.ThemePreset.ASTRAL_MIDNIGHT -> "🌌"
                                        },
                                        fontSize = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = themeItem.displayName,
                                        style = TextStyle(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else Color(0xFFA5ABC7)
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet dialog to add reflections & reminders
    if (showCreateDialog) {
        CreateReminderDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, desc, priority, fishType, hoursOffset ->
                val targetMillis = System.currentTimeMillis() + (hoursOffset * 60 * 60 * 1000L).toLong()
                viewModel.addReminder(title, desc, priority, fishType, targetMillis)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateReminderDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, desc: String, priority: String, fishType: Int, hoursOffset: Float) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("MEDIUM") }
    var fishType by remember { mutableStateOf(0) }
    var timerPresetIndex by remember { mutableStateOf(1) } // Default 1 hour offset

    val presets = listOf(
        Pair("30 Min", 0.5f),
        Pair("1 Hour", 1.0f),
        Pair("4 Hours", 4.0f),
        Pair("Today (8h)", 8.0f),
        Pair("Tomorrow (24h)", 24.0f)
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16192C)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Nurture New Reminder",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Task Title (e.g. Meditate, Study)", color = Color(0xFF888FA9)) },
                    maxLines = 1,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF212543),
                        unfocusedContainerColor = Color(0xFF212543),
                        focusedIndicatorColor = Color(0xFF0077B6),
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                TextField(
                    value = desc,
                    onValueChange = { desc = it },
                    placeholder = { Text("Short description...", color = Color(0xFF888FA9)) },
                    maxLines = 3,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF212543),
                        unfocusedContainerColor = Color(0xFF212543),
                        focusedIndicatorColor = Color(0xFF0077B6),
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Selector 1: Priorities
                Text("Task Urgency", color = Color(0xFFA5ABC7), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("LOW", "MEDIUM", "HIGH").forEach { pr ->
                        val isSelected = priority == pr
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) getPriorityColorValue(pr) else Color(0xFF212543))
                                .clickable { priority = pr }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pr,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFFA5ABC7)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector 2: Fish Style / Coloring
                Text("Select Fish Representation", color = Color(0xFFA5ABC7), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    (0..4).forEach { idx ->
                        val isSelected = fishType == idx
                        val col = getFishColorPalette(idx).bodyColor
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) col.copy(alpha = 0.35f) else Color(0xFF212543))
                                .clickable { fishType = idx },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = getFishIconText(idx),
                                style = TextStyle(fontSize = 18.sp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector 3: Time Deadline Offset Presets
                Text("Target Deadline", color = Color(0xFFA5ABC7), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEachIndexed { idx, pair ->
                        val isSelected = timerPresetIndex == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF0077B6) else Color(0xFF212543))
                                .clickable { timerPresetIndex = idx }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pair.first,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFFA5ABC7)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions Cancel/Trigger Dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFFA5ABC7))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onConfirm(title, desc, priority, fishType, presets[timerPresetIndex].second)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0077B6)),
                        shape = RoundedCornerShape(10.dp),
                        enabled = title.isNotBlank()
                    ) {
                        Text("Release Fish 🌊", color = Color.White)
                    }
                }
            }
        }
    }
}

// Simple CSS types for cleaner styling
typealias TextStyle = androidx.compose.ui.text.TextStyle

fun getFishIconText(index: Int): String {
    return when (index) {
        0 -> "🐠" // Goldfish
        1 -> "🐟" // Betta
        2 -> "🐡" // Neon Tetra
        3 -> "🦈" // Coral Tang
        else -> "🐬" // Purple Damsel
    }
}

private fun getPriorityBgColor(priority: String): Color {
    return when (priority) {
        "HIGH" -> Color(0xFFE53935)
        "MEDIUM" -> Color(0xFFFB8C00)
        else -> Color(0xFF00897B)
    }
}

private fun getPriorityColorValue(priority: String): Color {
    return when (priority) {
        "HIGH" -> Color(0xFFD32F2F)
        "MEDIUM" -> Color(0xFFF57C00)
        else -> Color(0xFF388E3C)
    }
}
