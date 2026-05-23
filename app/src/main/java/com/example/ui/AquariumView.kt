package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.Reminder
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import kotlin.random.Random

// Live interactive state classes for animation elements
data class LiveFish(
    val reminderId: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var targetX: Float? = null,
    var targetY: Float? = null,
    var wigglingPhase: Float = Random.nextFloat() * 100f,
    var scale: Float = 1.0f,
    var happinessBubbleCooldown: Int = 0,
    var joySpinCount: Float = 0.0f, // If completed, spins and jumps
    var lastTouchSecond: Int = -1 // Trace surface contact
)

data class LiveBubble(
    var x: Float,
    var y: Float,
    val speed: Float,
    val size: Float,
    val swayAmount: Float,
    val phase: Float
)

data class LiveFood(
    val id: Long,
    var x: Float,
    var y: Float,
    val speed: Float,
    val phase: Float,
    val targetReminderId: Int? = null, // Which fish gets to eat it
    var isEaten: Boolean = false
)

data class LiveTurtle(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var paddlePhase: Float = Random.nextFloat() * 100f
)

data class HeartParticle(
    var x: Float,
    var y: Float,
    var alpha: Float = 1.0f,
    val speedY: Float = 1.5f + Random.nextFloat() * 2f,
    val size: Float = 6f + Random.nextFloat() * 6f
)

data class FoodDropEvent(
    val id: Long,
    val reminderId: Int,
    val xFraction: Float
)

@Composable
fun AquariumView(
    reminders: List<Reminder>,
    onReminderSelected: (Reminder) -> Unit,
    selectedReminder: Reminder?,
    onFoodEaten: (Reminder) -> Unit,
    onWaterTapped: () -> Unit,
    foodDrops: List<FoodDropEvent>,
    onFoodDropProcessed: (Long) -> Unit,
    onBubblePopSFX: () -> Unit,
    themePreset: com.example.ui.theme.ThemePreset = com.example.ui.theme.ThemePreset.CLASSIC_ABYSSAL,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableStateOf(1000f) }
    var containerHeightPx by remember { mutableStateOf(600f) }

    // Stateful visual collections
    val liveFishMap = remember { mutableStateMapOf<Int, LiveFish>() }
    val liveBubbles = remember { mutableStateListOf<LiveBubble>() }
    val liveFoods = remember { mutableStateListOf<LiveFood>() }
    val heartParticles = remember { mutableStateListOf<HeartParticle>() }
    val liveTurtles = remember { mutableStateListOf<LiveTurtle>() }

    var timeTick by remember { mutableStateOf(0L) }

    // Sync database reminders to active fish states
    LaunchedEffect(reminders, containerWidthPx, containerHeightPx) {
        if (containerWidthPx <= 10f || containerHeightPx <= 10f) return@LaunchedEffect

        // Add missing fish
        reminders.forEach { reminder ->
            if (!liveFishMap.containsKey(reminder.id)) {
                val startX = Random.nextFloat() * (containerWidthPx - 100f) + 50f
                val startY = Random.nextFloat() * (containerHeightPx - 110f) + 60f
                val initialVx = (if (Random.nextBoolean()) 1f else -1f) * (1.2f + Random.nextFloat() * 1.5f)
                val initialVy = (Random.nextFloat() * 2f - 1f) * 0.4f
                liveFishMap[reminder.id] = LiveFish(
                    reminderId = reminder.id,
                    x = startX,
                    y = startY,
                    vx = initialVx,
                    vy = initialVy
                )
            }
        }

        // Remove stale fish
        val reminderIds = reminders.map { it.id }.toSet()
        val staleIds = liveFishMap.keys.filter { !reminderIds.contains(it) }
        staleIds.forEach { liveFishMap.remove(it) }

        // Core ambient turtles population
        if (liveTurtles.isEmpty() && containerWidthPx > 50f) {
            liveTurtles.add(
                LiveTurtle(
                    id = 1,
                    x = containerWidthPx * 0.25f,
                    y = containerHeightPx - 45f,
                    vx = 0.35f,
                    vy = 0.04f
                )
            )
            liveTurtles.add(
                LiveTurtle(
                    id = 2,
                    x = containerWidthPx * 0.70f,
                    y = containerHeightPx - 35f,
                    vx = -0.3f,
                    vy = -0.04f
                )
            )
        }
    }

    // Process drag food requests from swaped tabs
    LaunchedEffect(foodDrops, containerWidthPx, containerHeightPx) {
        if (containerWidthPx > 50f) {
            val waterLineY = containerHeightPx * 0.22f
            foodDrops.forEach { drop ->
                val alreadyExists = liveFoods.any { it.id == drop.id }
                if (!alreadyExists) {
                    liveFoods.add(
                        LiveFood(
                            id = drop.id,
                            x = (drop.xFraction * containerWidthPx).coerceIn(40f, containerWidthPx - 40f),
                            y = waterLineY,
                            speed = 1.3f + Random.nextFloat() * 0.8f,
                            phase = Random.nextFloat() * 100f,
                            targetReminderId = drop.reminderId
                        )
                    )
                    onFoodDropProcessed(drop.id)
                }
            }
        }
    }

    // Main animation loop ticking at roughly 60 FPS
    LaunchedEffect(Unit) {
        val bubbleRnd = Random
        while (true) {
            timeTick++

            // Populate initial ambient bubbles if needed
            if (liveBubbles.size < 20 && containerWidthPx > 10f) {
                repeat(20 - liveBubbles.size) {
                    liveBubbles.add(
                        LiveBubble(
                            x = bubbleRnd.nextFloat() * containerWidthPx,
                            y = containerHeightPx + bubbleRnd.nextFloat() * 100f,
                            speed = 1.0f + bubbleRnd.nextFloat() * 2.0f,
                            size = 4f + bubbleRnd.nextFloat() * 10f,
                            swayAmount = 1.0f + bubbleRnd.nextFloat() * 3f,
                            phase = bubbleRnd.nextFloat() * 100f
                        )
                    )
                }
            }

            val waterLineY = containerHeightPx * 0.22f

            // Update Ambient Bubbles
            for (i in liveBubbles.indices.reversed()) {
                val b = liveBubbles[i]
                b.y -= b.speed
                b.x += sin(timeTick * 0.05f + b.phase) * (b.swayAmount * 0.1f)
                if (b.y < waterLineY) {
                    b.y = containerHeightPx + 20f
                    b.x = bubbleRnd.nextFloat() * containerWidthPx
                }
            }

            // Update Sinking Food Flakes
            for (i in liveFoods.indices.reversed()) {
                val food = liveFoods[i]
                if (food.isEaten) {
                    liveFoods.removeAt(i)
                    continue
                }
                food.y += food.speed
                food.x += sin(timeTick * 0.06f + food.phase) * 0.4f
                
                // Dissolve food if it reaches sandy sea floor
                if (food.y > containerHeightPx - 15f) {
                    liveFoods.removeAt(i)
                }
            }

            // Update Heart/Happiness Particles
            for (i in heartParticles.indices.reversed()) {
                val heart = heartParticles[i]
                heart.y -= heart.speedY
                heart.alpha -= 0.02f
                if (heart.alpha <= 0.02f) {
                    heartParticles.removeAt(i)
                }
            }

            // Update Ambient Turtles (slow relaxing paddle)
            for (turtle in liveTurtles) {
                turtle.paddlePhase += 0.05f
                turtle.x += turtle.vx
                turtle.y += turtle.vy

                // vertical glide wave
                turtle.vy = sin(timeTick * 0.015f + turtle.id) * 0.12f

                // clamp horizontal limits
                val tPaddingX = 50f
                if (turtle.x < tPaddingX) {
                    turtle.x = tPaddingX
                    turtle.vx = abs(turtle.vx)
                } else if (turtle.x > containerWidthPx - tPaddingX) {
                    turtle.x = containerWidthPx - tPaddingX
                    turtle.vx = -abs(turtle.vx)
                }

                // clamp to sea floor depths
                val minTurtleY = containerHeightPx - 80f
                val maxTurtleY = containerHeightPx - 20f
                if (turtle.y < minTurtleY) {
                    turtle.y = minTurtleY
                } else if (turtle.y > maxTurtleY) {
                    turtle.y = maxTurtleY
                }
            }

            // Update Physics and Behaviors of Each Swimming Fish
            val reminderLookup = reminders.associateBy { it.id }
            liveFishMap.forEach { (id, fish) ->
                val r = reminderLookup[id] ?: return@forEach

                // 1. Check for custom food targeted specifically to this fish, or general food items
                val targetFood = liveFoods.firstOrNull { 
                    !it.isEaten && (it.targetReminderId == fish.reminderId || it.targetReminderId == null) 
                }

                if (targetFood != null) {
                    fish.targetX = targetFood.x
                    fish.targetY = targetFood.y
                } else {
                    fish.targetX = null
                    fish.targetY = null
                }

                // 2. Handle targeted swimming towards food
                val tx = fish.targetX
                val ty = fish.targetY

                if (tx != null && ty != null) {
                    fish.wigglingPhase += 0.22f
                    val dx = tx - fish.x
                    val dy = ty - fish.y
                    val dist = sqrt(dx * dx + dy * dy)
                    
                    if (dist < 450f) {
                        // Quick attraction steering
                        val forceX = (dx / dist) * 2.2f
                        val forceY = (dy / dist) * 1.8f
                        fish.vx = (fish.vx * 0.90f) + (forceX * 0.10f)
                        fish.vy = (fish.vy * 0.88f) + (forceY * 0.12f)

                        // If fish reaches food, consume it!
                        if (dist < 32f) {
                            targetFood?.isEaten = true
                            fish.scale = 1.4f
                            fish.happinessBubbleCooldown = 15
                            onFoodEaten(r)

                            // Spawn beautiful feedback heart particles
                            repeat(3) {
                                heartParticles.add(
                                    HeartParticle(
                                        x = fish.x + (Random.nextFloat() * 24f - 12f),
                                        y = fish.y - 12f
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // Regular ambient behaviors, heavily mapped to incomplete-deadline states
                    if (!r.isCompleted) {
                        val now = System.currentTimeMillis()
                        val timeLeftSec = (r.dueDate - now) / 1000f

                        if (timeLeftSec > 120f) {
                            // Stage A: Still near the seabed (Far: > 2 minutes)
                            fish.wigglingPhase += 0.03f // very minor tail sway
                            fish.vx = 0f
                            fish.vy = 0f
                            
                            val targetBaseY = containerHeightPx - 35f - (id % 3) * 6f
                            val targetBaseX = containerWidthPx * 0.2f + (id % 5) * (containerWidthPx * 0.15f)
                            
                            // Slowly align/lerp to still position
                            fish.x += (targetBaseX - fish.x) * 0.04f
                            fish.y += (targetBaseY - fish.y) * 0.04f
                        } else if (timeLeftSec in 45f..120f) {
                            // Stage B: Underway randomly near the bed (Close-ish: 45s to 2 mins)
                            fish.wigglingPhase += 0.08f
                            val pace = if (fish.vx > 0) 0.6f else -0.6f
                            fish.vx = (fish.vx * 0.95f) + (pace * 0.05f)
                            val targetVy = sin(timeTick * 0.02f + id) * 0.2f
                            fish.vy = (fish.vy * 0.95f) + (targetVy * 0.05f)
                            
                            fish.x += fish.vx
                            fish.y += fish.vy

                            // Lock to seabed vertically
                            val minY = containerHeightPx - 60f
                            val maxY = containerHeightPx - 25f
                            if (fish.y < minY) { fish.y = minY; fish.vy = abs(fish.vy) }
                            else if (fish.y > maxY) { fish.y = maxY; fish.vy = -abs(fish.vy) }
                        } else if (timeLeftSec in 15f..45f) {
                            // Stage C: Swimming higher up & down (Near: 15s to 45s)
                            fish.wigglingPhase += 0.14f
                            val urgencyMult = if (r.priority == "HIGH") 1.5f else 1.0f
                            val pace = (if (fish.vx > 0) 1.2f else -1.2f) * urgencyMult
                            fish.vx = (fish.vx * 0.94f) + (pace * 0.06f)
                            val targetVy = sin(timeTick * 0.035f + id) * 0.45f
                            fish.vy = (fish.vy * 0.93f) + (targetVy * 0.07f)

                            fish.x += fish.vx
                            fish.y += fish.vy

                            // Restrict y bounds to mid/bed layers
                            val minY = waterLineY + 20f
                            val maxY = containerHeightPx - 30f
                            if (fish.y < minY) { fish.y = minY; fish.vy = abs(fish.vy) }
                            else if (fish.y > maxY) { fish.y = maxY; fish.vy = -abs(fish.vy) }
                        } else {
                            // Stage D: Touching water surface (Critical: < 15s or past due)
                            fish.wigglingPhase += 0.21f
                            val currentSecond = (System.currentTimeMillis() / 1000) % 60
                            // 3 breaches per minute: intervals seconds (5..7), (25..27), (45..47)
                            val isSurfaceBreach = (currentSecond % 20) in 5..7

                            if (isSurfaceBreach) {
                                // Dart up quickly to water level
                                val surfaceY = waterLineY + 8f
                                fish.vy = (fish.vy * 0.82f) + ((surfaceY - fish.y) * 0.08f)
                                val pace = if (fish.vx > 0) 1.6f else -1.6f
                                fish.vx = (fish.vx * 0.95f) + (pace * 0.05f)

                                fish.x += fish.vx
                                fish.y += fish.vy

                                // Trigger bubble sound
                                if (fish.y < waterLineY + 22f) {
                                    val secVal = currentSecond.toInt()
                                    if (fish.lastTouchSecond != secVal) {
                                        fish.lastTouchSecond = secVal
                                        onBubblePopSFX() // Pop trigger

                                        // Spawn bubble rings on the surface
                                        repeat(2) {
                                            liveBubbles.add(
                                                LiveBubble(
                                                    x = fish.x + (Random.nextFloat() * 20f - 10f),
                                                    y = fish.y - 12f,
                                                    speed = 1.0f + Random.nextFloat() * 1.5f,
                                                    size = 5f + Random.nextFloat() * 6f,
                                                    swayAmount = 1.4f,
                                                    phase = Random.nextFloat() * 100f
                                                )
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Swim up and down in middle/top layers
                                val pace = if (fish.vx > 0) 1.3f else -1.3f
                                fish.vx = (fish.vx * 0.95f) + (pace * 0.05f)
                                val targetVy = sin(timeTick * 0.04f + id) * 0.55f
                                fish.vy = (fish.vy * 0.95f) + (targetVy * 0.05f)

                                fish.x += fish.vx
                                fish.y += fish.vy

                                val minY = waterLineY + 20f
                                val maxY = containerHeightPx * 0.75f
                                if (fish.y < minY) { fish.y = minY; fish.vy = abs(fish.vy) }
                                else if (fish.y > maxY) { fish.y = maxY; fish.vy = -abs(fish.vy) }
                            }
                        }
                    } else {
                        // Safe colorful completed parameters (regular free swimming)
                        fish.joySpinCount += 0.04f
                        fish.wigglingPhase += 0.16f
                        val pace = if (fish.vx > 0) 1.4f else -1.4f
                        fish.vx = (fish.vx * 0.96f) + (pace * 0.04f)
                        val targetVy = sin(timeTick * 0.03f + id) * 0.4f
                        fish.vy = (fish.vy * 0.95f) + (targetVy * 0.05f)

                        // doing joyful loops occasionally
                        val radiusVal = 1.8f
                        fish.x += fish.vx + cos(fish.joySpinCount * 2f * Math.PI.toFloat()) * radiusVal
                        fish.y += fish.vy + sin(fish.joySpinCount * 2f * Math.PI.toFloat()) * radiusVal
                    }
                }

                // Smooth scale restoration
                if (fish.scale > 1.0f) {
                    fish.scale -= 0.02f
                }

                // Wall boundary checks with bounce mechanics
                val paddingX = 80f
                val paddingYTop = waterLineY + 12f
                val paddingYBottom = containerHeightPx - 24f
                if (fish.x < paddingX) {
                    fish.x = paddingX
                    fish.vx = abs(fish.vx)
                } else if (fish.x > containerWidthPx - paddingX) {
                    fish.x = containerWidthPx - paddingX
                    fish.vx = -abs(fish.vx)
                }

                if (fish.y < paddingYTop) {
                    fish.y = paddingYTop
                    fish.vy = abs(fish.vy)
                } else if (fish.y > paddingYBottom) {
                    fish.y = paddingYBottom
                    fish.vy = -abs(fish.vy)
                }
            }

            delay(16) // ~60fps interval pacing
        }
    }

    val waterLineYPx = with(density) { 280.dp.toPx() * 0.22f }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0A0C16))
            .pointerInput(themePreset) {
                detectTapGestures { offset ->
                    if (offset.y >= waterLineYPx) {
                        onWaterTapped()
                        // Add beautiful falling food flakes starting at water surface
                        liveFoods.add(
                            LiveFood(
                                id = System.nanoTime(),
                                x = offset.x,
                                y = waterLineYPx,
                                speed = 1.5f + Random.nextFloat() * 1.5f,
                                phase = Random.nextFloat() * 100f
                            )
                        )
                    }
                }
            }
    ) {
        // Capture exact pixel bounds of the aquarium container dynamically
        val pxWidth = with(density) { maxWidth.toPx() }
        val pxHeight = with(density) { maxHeight.toPx() }
        LaunchedEffect(pxWidth, pxHeight) {
            containerWidthPx = pxWidth
            containerHeightPx = pxHeight
        }

        // Draw animated beautiful underwater assets in Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalWidth = size.width
            val totalHeight = size.height
            val waterLineY = totalHeight * 0.22f

            // 1. Draw above-water Sky with vertical gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = themePreset.skyColors,
                    startY = 0f,
                    endY = waterLineY
                ),
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(totalWidth, waterLineY)
            )

            // 2. Draw Sun / Crescent Moon
            val sunX = totalWidth * 0.72f
            val sunY = waterLineY * 0.45f
            val sunRadius = 18f
            if (themePreset.isNight) {
                // Draw glowing moon layer
                drawCircle(
                    color = themePreset.sunMoonColor.copy(alpha = 0.15f),
                    radius = sunRadius * 1.6f,
                    center = Offset(sunX, sunY)
                )
                drawCircle(
                    color = themePreset.sunMoonColor,
                    radius = sunRadius,
                    center = Offset(sunX, sunY)
                )
                // Offset circular overlay to cut the moon into a crescent
                drawCircle(
                    color = themePreset.skyColors[0],
                    radius = sunRadius,
                    center = Offset(sunX - 7f, sunY - 4f)
                )
            } else {
                // Vibrant glowing sun layers
                drawCircle(
                    color = themePreset.sunMoonColor.copy(alpha = 0.12f),
                    radius = sunRadius * 2.5f,
                    center = Offset(sunX, sunY)
                )
                drawCircle(
                    color = themePreset.sunMoonColor.copy(alpha = 0.35f),
                    radius = sunRadius * 1.6f,
                    center = Offset(sunX, sunY)
                )
                drawCircle(
                    color = themePreset.sunMoonColor,
                    radius = sunRadius,
                    center = Offset(sunX, sunY)
                )
            }

            // 3. Draw Beautiful Rolling Hills/Mountains with depth
            val distantHillPath = Path().apply {
                moveTo(0f, waterLineY)
                quadraticTo(totalWidth * 0.22f, waterLineY - 32f, totalWidth * 0.45f, waterLineY - 14f)
                quadraticTo(totalWidth * 0.68f, waterLineY - 42f, totalWidth * 0.85f, waterLineY - 8f)
                quadraticTo(totalWidth * 0.94f, waterLineY - 22f, totalWidth, waterLineY)
                lineTo(totalWidth, waterLineY)
                lineTo(0f, waterLineY)
                close()
            }
            drawPath(
                path = distantHillPath,
                color = themePreset.hillColor1.copy(alpha = 0.65f)
            )

            val closeHillPath = Path().apply {
                moveTo(0f, waterLineY)
                quadraticTo(totalWidth * 0.16f, waterLineY - 18f, totalWidth * 0.32f, waterLineY - 6f)
                quadraticTo(totalWidth * 0.52f, waterLineY - 25f, totalWidth * 0.74f, waterLineY - 10f)
                quadraticTo(totalWidth * 0.88f, waterLineY - 16f, totalWidth, waterLineY)
                lineTo(totalWidth, waterLineY)
                lineTo(0f, waterLineY)
                close()
            }
            drawPath(
                path = closeHillPath,
                color = themePreset.hillColor2
            )

            // 4. Draw Distant Silhouette Birds soaring
            val birdTime = timeTick * 0.015f
            val birdYOffset = sin(birdTime * 2.2f) * 3f
            val wingFlap = sin(timeTick * 0.14f) * 2.5f

            val b1x = totalWidth * 0.44f + sin(birdTime * 0.6f) * 12f
            val b1y = waterLineY * 0.38f + birdYOffset
            drawPath(
                path = Path().apply {
                    moveTo(b1x - 5f, b1y - wingFlap)
                    quadraticTo(b1x - 2.5f, b1y, b1x, b1y)
                    quadraticTo(b1x + 2.5f, b1y, b1x + 5f, b1y - wingFlap)
                    lineTo(b1x + 3.5f, b1y)
                    quadraticTo(b1x, b1y + 1.2f, b1x - 3.5f, b1y)
                    close()
                },
                color = themePreset.hillColor2.copy(alpha = 0.75f)
            )

            val b2x = totalWidth * 0.53f + sin(birdTime * 0.6f + 1f) * 10f
            val b2y = waterLineY * 0.30f + cos(birdTime * 0.7f) * 2f
            val wingFlap2 = cos(timeTick * 0.14f) * 2.5f
            drawPath(
                path = Path().apply {
                    moveTo(b2x - 4f, b2y - wingFlap2)
                    quadraticTo(b2x - 2f, b2y, b2x, b2y)
                    quadraticTo(b2x + 2f, b2y, b2x + 4f, b2y - wingFlap2)
                    lineTo(b2x + 3f, b2y)
                    quadraticTo(b2x, b2y + 1f, b2x - 3f, b2y)
                    close()
                },
                color = themePreset.hillColor2.copy(alpha = 0.68f)
            )

            // 5. Draw Wooden Fishing Pier and Cozy Cottage Hut
            val pierWidth = totalWidth * 0.15f
            val pierHeight = 6f
            drawRect(
                color = Color(0xFF3A2420), // sturdy wood
                topLeft = Offset(0f, waterLineY - 3f),
                size = androidx.compose.ui.geometry.Size(pierWidth, pierHeight)
            )
            // Wooden support piles in water
            drawRect(
                color = Color(0xFF201311),
                topLeft = Offset(pierWidth * 0.28f, waterLineY + 3f),
                size = androidx.compose.ui.geometry.Size(5f, 26f)
            )
            drawRect(
                color = Color(0xFF201311),
                topLeft = Offset(pierWidth * 0.78f, waterLineY + 3f),
                size = androidx.compose.ui.geometry.Size(5f, 22f)
            )

            // Cozy Ghibli cottage house
            val cotLeft = pierWidth * 0.2f
            val cotWidth = 24f
            val cotHeight = 18f
            val cotTop = waterLineY - cotHeight - 3f

            drawRect(
                color = Color(0xFF422E2B),
                topLeft = Offset(cotLeft, cotTop),
                size = androidx.compose.ui.geometry.Size(cotWidth, cotHeight)
            )
            val roofPath = Path().apply {
                moveTo(cotLeft - 4f, cotTop)
                lineTo(cotLeft + cotWidth / 2f, cotTop - 10f)
                lineTo(cotLeft + cotWidth + 4f, cotTop)
                close()
            }
            drawPath(roofPath, color = Color(0xFF721F1F)) // rustic clay tile red roof

            // Glowing yellow cottage windows for ultimate warmth
            val glowCol = if (themePreset.isNight) Color(0xFFFFCC00) else Color(0xFFFFD54F)
            drawRect(
                color = glowCol,
                topLeft = Offset(cotLeft + 5f, cotTop + 5f),
                size = androidx.compose.ui.geometry.Size(5f, 6f)
            )

            // 6. Draw Underwater background body
            drawRect(
                brush = Brush.verticalGradient(
                    colors = themePreset.waterColors,
                    startY = waterLineY,
                    endY = totalHeight
                ),
                topLeft = Offset(0f, waterLineY),
                size = androidx.compose.ui.geometry.Size(totalWidth, totalHeight - waterLineY)
            )

            // 7. Shimmering water surface boundary line
            drawLine(
                color = if (themePreset == com.example.ui.theme.ThemePreset.GHIBLI_SUNSET) Color(0xFFFFE599).copy(alpha = 0.65f)
                        else if (themePreset == com.example.ui.theme.ThemePreset.ASTRAL_MIDNIGHT) Color(0xFF9EFAFF).copy(alpha = 0.55f)
                        else Color.White.copy(alpha = 0.38f),
                start = Offset(0f, waterLineY),
                end = Offset(totalWidth, waterLineY),
                strokeWidth = 1.5f.dp.toPx()
            )

            // 8. Draw Submerged Filtering Sunbeams (God Rays)
            val rayPath1 = Path().apply {
                moveTo(totalWidth * 0.12f, waterLineY)
                lineTo(totalWidth * 0.38f, waterLineY)
                lineTo(totalWidth * 0.60f + sin(timeTick * 0.01f) * 45f, totalHeight)
                lineTo(totalWidth * 0.18f + sin(timeTick * 0.01f) * 45f, totalHeight)
                close()
            }
            drawPath(
                path = rayPath1,
                brush = Brush.verticalGradient(
                    colors = listOf(themePreset.rayColors[0].copy(alpha = 0.15f), Color.Transparent),
                    startY = waterLineY,
                    endY = totalHeight
                )
            )

            val rayPath2 = Path().apply {
                moveTo(totalWidth * 0.68f, waterLineY)
                lineTo(totalWidth * 0.88f, waterLineY)
                lineTo(totalWidth * 0.82f + cos(timeTick * 0.012f) * 40f, totalHeight)
                lineTo(totalWidth * 0.48f + cos(timeTick * 0.012f) * 40f, totalHeight)
                close()
            }
            drawPath(
                path = rayPath2,
                brush = Brush.verticalGradient(
                    colors = listOf(themePreset.rayColors[0].copy(alpha = 0.11f), Color.Transparent),
                    startY = waterLineY,
                    endY = totalHeight
                )
            )

            // 9. Draw Swaying Coral / Kelp plants on ocean bed
            drawSeaweed(this, totalWidth * 0.08f, 130f, timeTick * 0.024f)
            drawCoral(this, totalWidth * 0.22f, 60f, Color(0xFFFF5D8F))
            drawSeaweed(this, totalWidth * 0.43f, 160f, timeTick * 0.019f + 2f)
            drawCoral(this, totalWidth * 0.65f, 75f, Color(0xFFE07A5F))
            drawSeaweed(this, totalWidth * 0.80f, 110f, timeTick * 0.028f + 4f)
            drawCoral(this, totalWidth * 0.90f, 50f, Color(0xFF9F5DE3))

            // 10. Draw A Submerged Cozy Japanese Pagoda Stone Lantern (Toro)
            val lX = totalWidth * 0.86f
            val lY = totalHeight - 16f
            // Base Pedestal
            drawRect(
                color = themePreset.sandColor.copy(alpha = 0.92f),
                topLeft = Offset(lX - 10f, lY - 6f),
                size = androidx.compose.ui.geometry.Size(20f, 6f)
            )
            // Column
            drawRect(
                color = themePreset.sandColor.copy(alpha = 0.82f),
                topLeft = Offset(lX - 3f, lY - 18f),
                size = androidx.compose.ui.geometry.Size(6f, 12f)
            )
            // Platform
            drawRect(
                color = themePreset.sandColor.copy(alpha = 0.92f),
                topLeft = Offset(lX - 12f, lY - 22f),
                size = androidx.compose.ui.geometry.Size(24f, 4f)
            )
            // Light Chamber
            drawRect(
                color = themePreset.sandColor.copy(alpha = 0.85f),
                topLeft = Offset(lX - 7f, lY - 36f),
                size = androidx.compose.ui.geometry.Size(14f, 14f)
            )
            // Cozy glowing soul fire inside pagoda
            val toroGlow = if (themePreset.isNight) Color(0xFF4DFEEA) else Color(0xFFFFB300)
            drawCircle(
                color = toroGlow.copy(alpha = 0.35f),
                radius = 8f,
                center = Offset(lX, lY - 29f)
            )
            drawCircle(
                color = toroGlow,
                radius = 3.5f,
                center = Offset(lX, lY - 29f)
            )
            // Peaked Pagoda roof
            val toroRoof = Path().apply {
                moveTo(lX - 15f, lY - 36f)
                quadraticTo(lX, lY - 42f, lX + 15f, lY - 36f)
                lineTo(lX + 10f, lY - 41f)
                lineTo(lX - 10f, lY - 41f)
                close()
            }
            drawPath(path = toroRoof, color = themePreset.sandColor.copy(alpha = 0.98f))
            // Finial peak ball
            drawCircle(
                color = themePreset.sandColor.copy(alpha = 0.98f),
                radius = 2.5f,
                center = Offset(lX, lY - 43f)
            )

            // 11. Draw sandy groundbed layers
            drawRect(
                color = themePreset.sandColor,
                topLeft = Offset(0f, totalHeight - 16f),
                size = androidx.compose.ui.geometry.Size(totalWidth, 16f)
            )
            drawOval(
                color = themePreset.sandColor.copy(alpha = 0.72f),
                topLeft = Offset(totalWidth * 0.15f, totalHeight - 24f),
                size = androidx.compose.ui.geometry.Size(46f, 18f)
            )
            drawOval(
                color = themePreset.sandColor.copy(alpha = 0.72f),
                topLeft = Offset(totalWidth * 0.70f, totalHeight - 22f),
                size = androidx.compose.ui.geometry.Size(60f, 15f)
            )

            // 12. Draw Rising Bubbles
            liveBubbles.forEach { bubble ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.28f),
                    radius = bubble.size,
                    center = Offset(bubble.x, bubble.y),
                    style = Stroke(width = 1.dp.toPx())
                )
                // Draw a small glimmer highlight inside elements
                drawCircle(
                    color = Color.White.copy(alpha = 0.4f),
                    radius = bubble.size * 0.3f,
                    center = Offset(bubble.x - bubble.size * 0.3f, bubble.y - bubble.size * 0.3f)
                )
            }

            // 5. Draw Heart Particles
            heartParticles.forEach { hp ->
                val heartPath = Path().apply {
                    val scaleFactor = hp.size / 10f
                    val hx = hp.x
                    val hy = hp.y
                    moveTo(hx, hy + 2f * scaleFactor)
                    cubicTo(hx - 5f * scaleFactor, hy - 5f * scaleFactor, hx - 10f * scaleFactor, hy + 2f * scaleFactor, hx, hy + 8f * scaleFactor)
                    cubicTo(hx + 10f * scaleFactor, hy + 2f * scaleFactor, hx + 5f * scaleFactor, hy - 5f * scaleFactor, hx, hy + 2f * scaleFactor)
                }
                drawPath(
                    path = heartPath,
                    color = Color(0xFFFF4D6D).copy(alpha = hp.alpha)
                )
            }

            // 6. Draw Falling Food Flakes
            liveFoods.forEach { food ->
                drawRect(
                    color = Color(0xFFFFB703),
                    topLeft = Offset(food.x, food.y),
                    size = androidx.compose.ui.geometry.Size(8f, 8f)
                )
                drawRect(
                    color = Color(0xFFD62828),
                    topLeft = Offset(food.x + 3f, food.y - 3f),
                    size = androidx.compose.ui.geometry.Size(5f, 5f)
                )
            }

            // 7. Draw Ambient Turtles paddling lazily near the bed
            liveTurtles.forEach { turtle ->
                val scale = 0.85f
                val bodyColor = Color(0xFF4C6D3B) // Sage Green
                val shellColor = Color(0xFF2E4522) // Emerald forest shadow
                val patternColor = Color(0xFF5A7E4B)

                val isFacingRight = turtle.vx > 0
                val dirSig = if (isFacingRight) 1f else -1f
                val paddleSin = sin(turtle.paddlePhase) * 16f * dirSig

                // Flippers (paddles)
                drawOval(
                    color = bodyColor,
                    topLeft = Offset(turtle.x - (8f * dirSig) - 6f, turtle.y - 14f + paddleSin),
                    size = androidx.compose.ui.geometry.Size(12f, 18f)
                )
                drawOval(
                    color = bodyColor,
                    topLeft = Offset(turtle.x - (8f * dirSig) - 6f, turtle.y + 14f - paddleSin),
                    size = androidx.compose.ui.geometry.Size(12f, 18f)
                )

                // Back small flippers
                drawOval(
                    color = bodyColor,
                    topLeft = Offset(turtle.x - (16f * dirSig) - 4f, turtle.y - 10f),
                    size = androidx.compose.ui.geometry.Size(8f, 10f)
                )
                drawOval(
                    color = bodyColor,
                    topLeft = Offset(turtle.x - (16f * dirSig) - 4f, turtle.y + 6f),
                    size = androidx.compose.ui.geometry.Size(8f, 10f)
                )

                // Head
                drawCircle(
                    color = bodyColor,
                    radius = 7f,
                    center = Offset(turtle.x + (18f * dirSig), turtle.y)
                )
                // Head eyes
                drawCircle(
                    color = Color.Black,
                    radius = 1.5f,
                    center = Offset(turtle.x + (20f * dirSig), turtle.y - 2f)
                )

                // Shell (Oval dome)
                drawOval(
                    color = shellColor,
                    topLeft = Offset(turtle.x - 16f, turtle.y - 12f),
                    size = androidx.compose.ui.geometry.Size(32f, 24f)
                )

                // Shell hexagonal crest detailing
                drawOval(
                    color = patternColor,
                    topLeft = Offset(turtle.x - 10f, turtle.y - 7f),
                    size = androidx.compose.ui.geometry.Size(20f, 14f)
                )
            }

            // 8. Draw Swimming Koi Fish (and wiggling structures)
            val reminderLookup = reminders.associateBy { it.id }
            liveFishMap.forEach { (id, fish) ->
                val r = reminderLookup[id] ?: return@forEach
                val scale = fish.scale
                val isFacingRight = fish.vx >= 0 // standard flow orientation

                // Load Koi colored vs monochrome layouts!
                val colors = if (r.isCompleted) {
                    getFishColorPalette(r.fishType)
                } else {
                    // Monochrome Koi fish stays white & dark charcoal until solved
                    FishPalette(
                        bodyColor = Color(0xFFF5F5F5), // pearl white body
                        finColor = Color(0xFFD6D6D6).copy(alpha = 0.85f),  // gray/silver fins
                        stripeColor = Color(0xFF262626) // charcoal black stripes
                    )
                }

                // Render Tail Fin (waving dynamically using sine trig functions based on speed)
                val isSelected = selectedReminder?.id == r.id
                val wagSpeed = if (r.priority == "HIGH") 0.22f else 0.14f
                val finWashing = sin(timeTick * wagSpeed + fish.wigglingPhase) * 12f * scale
                val tailXOffset = if (isFacingRight) -22f else 22f
                val tailPath = Path().apply {
                    moveTo(fish.x + (tailXOffset * scale), fish.y)
                    lineTo(fish.x + ((tailXOffset * 2.2f) * scale), fish.y - (14f * scale) + finWashing)
                    lineTo(fish.x + ((tailXOffset * 2.2f) * scale), fish.y + (14f * scale) + finWashing)
                    close()
                }
                drawPath(path = tailPath, color = colors.finColor)

                // Render Dorsal / Upper Fin (Long flowing style)
                val dorsalPath = Path().apply {
                    moveTo(fish.x - (8f * scale), fish.y - (10f * scale))
                    quadraticTo(
                        fish.x - (18f * scale), fish.y - (24f * scale),
                        fish.x - (28f * scale), fish.y - (8f * scale)
                    )
                }
                drawPath(path = dorsalPath, color = colors.finColor)

                // Render Pectoral Fins (animated wings)
                val pectXOffset = if (isFacingRight) -12f else 12f
                drawOval(
                    color = colors.finColor.copy(alpha = 0.9f),
                    topLeft = Offset(fish.x + pectXOffset * scale - 4f * scale, fish.y + 6f * scale),
                    size = androidx.compose.ui.geometry.Size(14f * scale, 8f * scale)
                )

                // Render typical Koi mouth whisker barbels
                val mouthX = if (isFacingRight) fish.x + 22f * scale else fish.x - 22f * scale
                val mouthY = fish.y + 2f * scale
                val whiskerDir = if (isFacingRight) 5f * scale else -5f * scale
                drawLine(
                    color = colors.finColor,
                    start = Offset(mouthX, mouthY),
                    end = Offset(mouthX + whiskerDir, mouthY + 5f * scale),
                    strokeWidth = 1.5f * scale
                )
                drawLine(
                    color = colors.finColor,
                    start = Offset(mouthX, mouthY - 4f * scale),
                    end = Offset(mouthX + whiskerDir, mouthY - 8f * scale),
                    strokeWidth = 1.5f * scale
                )

                // Render main fish body layout (Oval/Capsule shape)
                drawOval(
                    color = colors.bodyColor,
                    topLeft = Offset(fish.x - (24f * scale), fish.y - (11f * scale)),
                    size = androidx.compose.ui.geometry.Size(48f * scale, 22f * scale)
                )

                // Render highlight side decoration streak or Koi patches overlay
                if (r.isCompleted) {
                    val stripePath = Path().apply {
                        moveTo(fish.x - (10f * scale), fish.y)
                        quadraticTo(
                            fish.x, fish.y + (3f * scale * (if (isFacingRight) 1f else -1f)),
                            fish.x + (10f * scale), fish.y
                        )
                    }
                    drawPath(
                        path = stripePath,
                        color = colors.stripeColor,
                        style = Stroke(width = 3f * scale)
                    )
                    // Beautiful goldfish-orange or ruby scarlet patches
                    drawCircle(
                        color = Color(0xFFE63946),
                        radius = 4.5f * scale,
                        center = Offset(fish.x - 5f * scale, fish.y - 3f * scale)
                    )
                    drawCircle(
                        color = Color(0xFFFFCA3A),
                        radius = 5.5f * scale,
                        center = Offset(fish.x + 6f * scale, fish.y - 4f * scale)
                    )
                } else {
                    // Monochrome black/charcoal patches for unsolved reminders
                    drawCircle(
                        color = Color(0xFF262626),
                        radius = 4f * scale,
                        center = Offset(fish.x - 6f * scale, fish.y - 2f * scale)
                    )
                    drawCircle(
                        color = Color(0xFF404040),
                        radius = 5f * scale,
                        center = Offset(fish.x + 5f * scale, fish.y - 3f * scale)
                    )
                }

                // Render Eye and Pupil depending on swim direction
                val eyeXOffset = if (isFacingRight) 13f else -17f
                drawCircle(
                    color = Color.White,
                    radius = 4f * scale,
                    center = Offset(fish.x + (eyeXOffset * scale), fish.y - (3f * scale))
                )
                drawCircle(
                    color = Color.Black,
                    radius = 1.8f * scale,
                    center = Offset(fish.x + ((eyeXOffset + (if (isFacingRight) 1f else -1f)) * scale), fish.y - (3f * scale))
                )

                // Ring selector when the fish is currently tapped as primary reminder
                if (isSelected) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.45f),
                        radius = 35f * scale,
                        center = Offset(fish.x, fish.y),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        // Overlay Interactive, Accessible Compose Labels directly positioned at (x,y)
        val reminderLookup = reminders.associateBy { it.id }
        liveFishMap.forEach { (id, fish) ->
            val r = reminderLookup[id] ?: return@forEach
            val indicatorColor = getPriorityColor(r.priority)

            // Convert raw pixel positions to Dp equivalents
            val labelX = with(density) { (fish.x).toDp() }
            val labelY = with(density) { (fish.y).toDp() }

            // Ensure the floating label coordinates are shifted upward to sit perfectly above fish body
            Box(
                modifier = Modifier
                    .offset(x = labelX - 60.dp, y = labelY - 48.dp)
                    .width(130.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xE61E233D))
                    .clickable { onReminderSelected(r) }
                    .padding(vertical = 4.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Priority dot indicator
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(indicatorColor)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = r.title,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${r.progress}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 8.sp,
                                color = Color(0xFFA2A9CE),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    // Task completion status visual bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color(0xFF3B405E))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = r.progress / 100f)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF2400FF),
                                            getFishColorPalette(r.fishType).bodyColor
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

// Seaweed kelp draw instructions
private fun drawSeaweed(drawScope: DrawScope, startX: Float, height: Float, wavePhase: Float) {
    val path = Path()
    val totalHeight = drawScope.size.height
    path.moveTo(startX, totalHeight)

    val segments = 8
    val segmentHeight = height / segments
    for (i in 1..segments) {
        val y = totalHeight - i * segmentHeight
        val o = sin(wavePhase + i * 0.5f) * 12f
        val x = startX + o
        path.lineTo(x, y)
    }
    // Widen seabed footprint slightly and trace downward to make kelp stems authentic
    for (i in segments downTo 0) {
        val y = totalHeight - i * segmentHeight
        val o = sin(wavePhase + i * 0.5f) * 12f
        val x = startX + 16f + o
        path.lineTo(x, y)
    }
    path.close()
    drawScope.drawPath(
        path = path,
        color = Color(0xFF208B60).copy(alpha = 0.72f)
    )
}

// Coral draw helper
private fun drawCoral(drawScope: DrawScope, startX: Float, height: Float, branchColor: Color) {
    val path = Path()
    val totalHeight = drawScope.size.height
    
    path.moveTo(startX, totalHeight)
    // Left branch quadratic curves
    path.quadraticTo(startX - 15f, totalHeight - height * 0.40f, startX - 25f, totalHeight - height * 0.70f)
    path.quadraticTo(startX - 32f, totalHeight - height * 0.82f, startX - 28f, totalHeight - height * 0.90f)
    path.quadraticTo(startX - 22f, totalHeight - height * 0.80f, startX - 18f, totalHeight - height * 0.60f)
    
    // Main middle branch
    path.quadraticTo(startX, totalHeight - height * 0.50f, startX + 4f, totalHeight - height * 0.95f)
    path.quadraticTo(startX + 14f, totalHeight - height * 0.90f, startX + 10f, totalHeight - height * 0.65f)
    
    // Right branch
    path.quadraticTo(startX + 28f, totalHeight - height * 0.45f, startX + 38f, totalHeight - height * 0.78f)
    path.quadraticTo(startX + 44f, totalHeight - height * 0.85f, startX + 40f, totalHeight - height * 0.55f)
    path.quadraticTo(startX + 20f, totalHeight - height * 0.20f, startX, totalHeight)
    path.close()

    drawScope.drawPath(
        path = path,
        color = branchColor.copy(alpha = 0.84f)
    )
}

// Color palettes representing unique saltwater/pond fish types
data class FishPalette(val bodyColor: Color, val finColor: Color, val stripeColor: Color)

fun getFishColorPalette(index: Int): FishPalette {
    return when (index) {
        0 -> FishPalette(
            bodyColor = Color(0xFFFF9F1C), // Goldfish orange
            finColor = Color(0xFFFF5964),  // Warm sunset red fins
            stripeColor = Color(0xFFFFE169) // Glimmer gold stripes
        )
        1 -> FishPalette(
            bodyColor = Color(0xFFE63946), // Betta ruby crimson
            finColor = Color(0xFF8338EC),  // Mystical purple flow fins
            stripeColor = Color(0xFFFF007F) // Intense hot pink stripe
        )
        2 -> FishPalette(
            bodyColor = Color(0xFF00B4D8), // Neon Tetra cyber cyan
            finColor = Color(0xFFFF1493),  // Neon pink tail
            stripeColor = Color(0xFFFFFFFF) // Neon silver light
        )
        3 -> FishPalette(
            bodyColor = Color(0xFF3A86C8), // Blue Tang rich sapphire
            finColor = Color(0xFFFFD166),  // Neon sulfur yellow fin
            stripeColor = Color(0xFF03045E) // Midnight outline
        )
        else -> FishPalette(
            bodyColor = Color(0xFF9B5DE5), // Plum Damsel deep violet
            finColor = Color(0xFF00F5D4),  // Electric seafoam teal fins
            stripeColor = Color(0xFFF15BB5) // Sweet purple stripes
        )
    }
}

private fun getPriorityColor(priority: String): Color {
    return when (priority) {
        "HIGH" -> Color(0xFFFF4D4D)   // High = scarlet
        "MEDIUM" -> Color(0xFFFFAD33) // Medium = amber orange
        else -> Color(0xFF2EC4B6)     // Low = calm teal
    }
}
