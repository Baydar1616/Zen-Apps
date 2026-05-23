package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sin
import kotlin.math.exp
import kotlin.random.Random

class SoundSynthesizer {
    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Volatile settings accessible across threads
    @Volatile var totalVolume = 0.5f
    @Volatile var bubbleVolume = 0.6f
    @Volatile var melodyVolume = 0.4f
    @Volatile var splashVolume = 0.8f

    // Interactive event queue
    private val eventQueue = ConcurrentLinkedQueue<SoundEvent>()

    sealed class SoundEvent {
        class Splash(val intensity: Float) : SoundEvent()
        class BubblePop : SoundEvent()
    }

    private val sampleRate = 22050 // Lightweight and efficient
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(1024)

    fun start() {
        if (synthJob != null) return
        
        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            Log.e("SoundSynthesizer", "Failed to initialize AudioTrack procedural synth", e)
            return
        }

        synthJob = scope.launch {
            generateAudioLoop()
        }
    }

    fun stop() {
        synthJob?.cancel()
        synthJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e("SoundSynthesizer", "Error stopping AudioTrack", e)
        }
        audioTrack = null
    }

    fun triggerSplash() {
        eventQueue.add(SoundEvent.Splash(1.0f))
    }

    fun triggerBubblePop() {
        eventQueue.add(SoundEvent.BubblePop())
    }

    private suspend fun generateAudioLoop() {
        val buffer = ShortArray(bufferSize / 2)
        var globalPhase = 0L

        // Melody states
        var notePhase = 0.0
        var noteFrequency = 0.0
        var noteRemainingSamples = 0
        var noteTotalSamples = 0
        var noteAttackSamples = 0
        var noteDecaySamples = 0

        // Peaceful Pentatonic Scale in C-Major
        val scale = doubleArrayOf(261.63, 293.66, 329.63, 392.00, 440.00, 523.25, 587.33, 659.25)
        var nextNoteTime = 0L

        val activeSplashes = mutableListOf<SplashSynth>()
        val activeBubbles = mutableListOf<BubbleSynth>()
        var nextBubblePopTime = 0L

        while (currentCoroutineContext().isActive) {
            for (i in buffer.indices) {
                val t = globalPhase + i
                var sample = 0.0

                // 1. PROCEDURAL MELODY CHORD GENERATOR
                if (melodyVolume > 0.01f) {
                    if (t >= nextNoteTime && noteRemainingSamples <= 0) {
                        // Queue a soothing note
                        val freq = scale[Random.nextInt(scale.size)]
                        noteFrequency = freq
                        val durationSec = 2.0 + Random.nextDouble() * 3.0
                        noteTotalSamples = (durationSec * sampleRate).toInt()
                        noteRemainingSamples = noteTotalSamples
                        noteAttackSamples = (0.4 * sampleRate).toInt() // Soft peaceful sweep in
                        noteDecaySamples = noteTotalSamples - noteAttackSamples
                        notePhase = 0.0

                        // Schedule the next note gap
                        val gapSec = 1.0 + Random.nextDouble() * 2.5
                        nextNoteTime = t + noteTotalSamples + (gapSec * sampleRate).toLong()
                    }

                    if (noteRemainingSamples > 0) {
                        val envelope = if (notePhase < noteAttackSamples) {
                            notePhase / noteAttackSamples
                        } else {
                            val decayIdx = notePhase - noteAttackSamples
                            (1.0 - (decayIdx / noteDecaySamples)).coerceIn(0.0, 1.0)
                        }

                        // Generate a rich, bell-like harp chime
                        val angle = 2.0 * Math.PI * noteFrequency * (notePhase / sampleRate)
                        var noteSample = sin(angle)
                        noteSample += 0.4 * sin(angle * 2.01) * exp(-2.0 * notePhase / noteTotalSamples)
                        noteSample += 0.2 * sin(angle * 3.02) * exp(-4.0 * notePhase / noteTotalSamples)
                        noteSample += 0.1 * sin(angle * 4.04) * exp(-6.0 * notePhase / noteTotalSamples)

                        sample += noteSample * 0.18 * envelope * melodyVolume

                        notePhase += 1.0
                        noteRemainingSamples--
                    }
                }

                // 2. PROCEDURAL AMBIENT BUBBLES
                if (bubbleVolume > 0.01f) {
                    if (t >= nextBubblePopTime && activeBubbles.size < 6) {
                        activeBubbles.add(BubbleSynth(sampleRate))
                        val interval = 0.2 + Random.nextDouble() * 1.2
                        nextBubblePopTime = t + (interval * sampleRate).toLong()
                    }

                    // Process event queue additions
                    while (eventQueue.isNotEmpty()) {
                        when (val event = eventQueue.poll()) {
                            is SoundEvent.BubblePop -> {
                                if (activeBubbles.size < 10) {
                                    activeBubbles.add(BubbleSynth(sampleRate))
                                }
                            }
                            is SoundEvent.Splash -> {
                                if (activeSplashes.size < 5) {
                                    activeSplashes.add(SplashSynth(sampleRate, event.intensity))
                                }
                            }
                        }
                    }

                    // Sum active bubbling voices
                    val bubbleItr = activeBubbles.iterator()
                    while (bubbleItr.hasNext()) {
                        val bubble = bubbleItr.next()
                        if (bubble.isActive) {
                            sample += bubble.nextSample() * 0.14 * bubbleVolume
                        } else {
                            bubbleItr.remove()
                        }
                    }
                }

                // 3. INTERACTIVE WATER SPLASH EFFECTS
                if (splashVolume > 0.01f && activeSplashes.isNotEmpty()) {
                    val splashItr = activeSplashes.iterator()
                    while (splashItr.hasNext()) {
                        val splash = splashItr.next()
                        if (splash.isActive) {
                            sample += splash.nextSample() * 0.32 * splashVolume
                        } else {
                            splashItr.remove()
                        }
                    }
                }

                // Master Mix Volume configuration safely clamped
                val finalSample = sample * totalVolume
                val clamped = (finalSample * 32767.0).coerceIn(-32768.0, 32767.0)
                buffer[i] = clamped.toInt().toShort()
            }

            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    try {
                        track.write(buffer, 0, buffer.size)
                    } catch (ex: Exception) {
                        // Safe failure check if state mutates
                    }
                }
            }
            globalPhase += buffer.size
            yield()
        }
    }

    private class BubbleSynth(val sampleRate: Int) {
        var isActive = true
        private var currentSample = 0
        private val startFreq = 250.0 + Random.nextDouble() * 180.0
        private val endFreq = 850.0 + Random.nextDouble() * 250.0
        private val durationSamples = (0.045 + Random.nextDouble() * 0.04) * sampleRate
        private var accumPhase = 0.0

        fun nextSample(): Double {
            if (currentSample >= durationSamples) {
                isActive = false
                return 0.0
            }
            val progress = currentSample / durationSamples
            val currentFreq = startFreq + (endFreq - startFreq) * progress
            val envelope = exp(-3.8 * progress) * (1.0 - progress)

            accumPhase += 2.0 * Math.PI * currentFreq / sampleRate
            val sample = sin(accumPhase) * envelope

            currentSample++
            return sample
        }
    }

    private class SplashSynth(val sampleRate: Int, val intensity: Float) {
        var isActive = true
        private var currentSample = 0
        private val durationSamples = (0.25f + Random.nextFloat() * 0.25f) * sampleRate
        private var accumPhase = 0.0

        fun nextSample(): Double {
            if (currentSample >= durationSamples) {
                isActive = false
                return 0.0
            }

            val progress = currentSample.toDouble() / durationSamples
            val noise = Random.nextDouble() * 2.0 - 1.0
            
            // Frequencies represent water sloshing
            val centerFreq = 1100.0 * (1.0 - progress * 0.65) + (noise * 120.0)
            accumPhase += 2.0 * Math.PI * centerFreq / sampleRate

            val envelope = if (progress < 0.12) {
                (progress / 0.12) * intensity
            } else {
                exp(-4.5 * (progress - 0.12)) * (1.0 - progress) * intensity
            }

            val sineWave = sin(accumPhase)
            val sample = (0.65 * sineWave + 0.35 * noise) * envelope

            currentSample++
            return sample
        }
    }
}
