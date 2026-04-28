package com.mantz_it.rfanalyzer.analyzer

import android.util.Log
import com.mantz_it.nativedsp.NativeDsp
import com.mantz_it.rfanalyzer.database.GlobalPerformanceData
import com.mantz_it.rfanalyzer.source.SamplePacket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * <h1>RF Analyzer - Analyzer Processing Loop</h1>
 *
 * Module:      FftProcessor.kt
 * Description: This Thread will fetch samples from the incoming queue (provided by the scheduler),
 * do the signal processing (fft) and then forward the result to the AnalyzerSurface.
 *
 * @author Dennis Mantz
 *
 * Copyright (C) 2025 Dennis Mantz
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */


// FFT Buffer
class FftProcessorData {
    val lock = ReentrantReadWriteLock()
    @Volatile
    var waterfallBuffer: Array<ByteArray>? = null // Circular buffer for fft samples (with history)
    @Volatile
    var waterfallBufferDirtyMap: Array<Boolean>? = null // Circular buffer which indicates for each row in waterfallBuffer if it must be recalculated
    @Volatile
    var frequencyOrSampleRateChanged = true
    @Volatile
    var writeIndex = 0 // Tracks where new FFT results go
    @Volatile
    var readIndex = 0  // Tracks where the latest FFT results are
    @Volatile
    var frequency: Long? = null
    @Volatile
    var sampleRate: Long? = null
    @Volatile
    var peaks: FloatArray? = null // peak hold values
}

/**
 * FftProcessor orchestrates feeding samples into native ring buffer, calling native FFT,
 * and writing results into fftProcessorData for the UI thread.
 *
 * IMPORTANT CHANGES:
 *  - waterfallBufferSize: fixed at construction time (replaces previous waterfallSpeed)
 *  - fftSize is a setting on this processor (not derived from incoming packet length)
 *  - fftFramesPerSecond: desired maximum FPS (user configurable). Processor will attempt to not exceed this.
 *                       If device can't keep up the processor will run as fast as it can and update actualFftFramesPerSecond.
 */
class FftProcessor(
    initialFftSize: Int,
    initialSampleRate: Long,
    initialFrequency: Long,
    initialFftFramesPerSecond: Float,
    initialChannelFrequencyRange: Pair<Long, Long>,
    private val fftProcessorData: FftProcessorData,
    private val waterfallBufferSize: Int,                 // fixed size for waterfall history (cannot change at runtime)
    var fftPeakHold: Boolean,
    private val onAverageSignalStrengthChanged: (Float) -> Unit,
) : Thread() {
    private var stopRequested = true // Will stop the thread when set to true
    private var nativeDsp: NativeDsp = NativeDsp()

    // User-settable desired maximum frames per second. Must be > 0. Volatile for visibility.
    @Volatile
    var fftFramesPerSecond: Float = initialFftFramesPerSecond

    // Measured effective frames per second (smoothed). Exposed via GlobalPerformanceData.
    @Volatile
    var actualFftFramesPerSecond: Float = 0.0f
        private set

    // current FFT size (complex bins); starts with initialFftSize and can be changed at runtime via setFftSize.
    @Volatile
    var fftSize: Int = initialFftSize

    @Volatile
    var sampleRate: Long = initialSampleRate

    @Volatile
    var frequency: Long = initialFrequency

    @Volatile
    var channelFrequencyRange: Pair<Long, Long> = initialChannelFrequencyRange

    companion object {
        private const val LOGTAG = "FftProcessor"

        // Constants for converting float -> byte in waterfallBuffer
        const val MIN_DB = -90f
        const val MAX_DB = 0f
        const val DB_RANGE = MAX_DB - MIN_DB // 90
        const val BYTE_MAX = 255f
        const val SCALE = BYTE_MAX / DB_RANGE
        const val INV_SCALE = DB_RANGE / BYTE_MAX

        // smoothing factor for actual FPS (EMA)
        private const val FPS_EMA_ALPHA = 0.10f

        // lead-time control loop for hop size
        private const val HOP_KP = 0.35f
        private const val HOP_KI = 0.08f


        // size of the internal ring buffer for input samples:
        const val MAX_FFT_SIZE = 131072
        //const val MAX_FFT_SIZE = 262144
        private const val FFT_BUFFER_CAPACITY = MAX_FFT_SIZE * 10 // 10x of the max fft size
    }

    private val availableSamples = AtomicInteger(0)

    private var nextFrameDeadlineNs = 0L
    private var lastFrameDoneNs = 0L
    private var fpsPeriodEmaNs = 0.0
    private var hopIntegral = 0.0

    init {
        // Initialize nativeDsp with initial fft size.
        if (!nativeDsp.init(fftSize, FFT_BUFFER_CAPACITY)) {
            Log.e(LOGTAG, "Failed to init native DSP.")
        }
    }

    /**
     * Will start the processing loop
     */
    override fun start() {
        this.stopRequested = false
        super.start()
    }

    /**
     * Will set the stopRequested flag so that the processing loop will terminate
     */
    fun stopLoop() {
        this.stopRequested = true
    }

    fun putNewFftSamples(samples: FloatArray) {
        val ret = nativeDsp.addNewSamples(samples)
        if (ret < 0) {
            Log.e(LOGTAG, "putNewFftSamples: error=${availableSamples.get()}")
        } else {
            availableSamples.set(ret)
            //Log.d(LOGTAG, "putNewFftSamples: availableSamples=${availableSamples.get()}")
        }
    }

    private fun updateActualFps(frameDoneNs: Long) {
        if (lastFrameDoneNs != 0L) {
            val periodNs = (frameDoneNs - lastFrameDoneNs).toDouble()
            fpsPeriodEmaNs = if (fpsPeriodEmaNs == 0.0) {
                periodNs
            } else {
                fpsPeriodEmaNs * (1.0 - FPS_EMA_ALPHA) + periodNs * FPS_EMA_ALPHA
            }
            actualFftFramesPerSecond = (1_000_000_000.0 / fpsPeriodEmaNs).toFloat()
        }
        lastFrameDoneNs = frameDoneNs
    }

    private fun computeHop(currentSampleRate: Long, currentFftSize: Int): Int {
        val sr = currentSampleRate.toDouble()
        if (sr <= 0.0) return 1

        val sustainableFps = if (actualFftFramesPerSecond > 1f) actualFftFramesPerSecond else fftFramesPerSecond
        val desiredFps = if (fftFramesPerSecond < sustainableFps) fftFramesPerSecond else sustainableFps
        val nominalHop = sr / desiredFps

        // lead-time target for the input buffer
        val FFT_LEAD_TARGET_SECONDS = if(sr<5_000_000) 0.15f else 0.05f // choose smaller lead time on high sample rates to not overfill the buffer
        val targetLeadSamples = max(
            currentFftSize*2, // hold at least samples for 2 ffts
            (sr * FFT_LEAD_TARGET_SECONDS).roundToInt()
        ).coerceAtMost(FFT_BUFFER_CAPACITY - currentFftSize)

        val available = availableSamples.get().coerceAtLeast(0)
        val leadErrorRatio = ((available - targetLeadSamples).toDouble() / targetLeadSamples.toDouble()).coerceIn(-0.5, 0.5)

        GlobalPerformanceData.updateLoad("FftProcessorBufferLead", available.toFloat() / targetLeadSamples.toFloat())
        GlobalPerformanceData.updateLoad("FftProcessorBufferFill", available.toFloat() / FFT_BUFFER_CAPACITY.toFloat())

        hopIntegral = (hopIntegral + leadErrorRatio).coerceIn(-4.0, 4.0)

        val correction = 1.0 + HOP_KP * leadErrorRatio + HOP_KI * hopIntegral

        val res = (nominalHop * correction)
            .roundToInt()
            .coerceIn(1, FFT_BUFFER_CAPACITY - currentFftSize) // always leave at least samples for one fft in the buffer
        //Log.d(LOGTAG, "computeHop: nominalHop=$nominalHop targetLeadSamples=$targetLeadSamples available=$available leadErrorRatio=$leadErrorRatio hopIntegral=$hopIntegral correction=$correction res=$res")
        return res
    }

    override fun run() {
        this.setName("Thread-FftProcessor-" + System.currentTimeMillis())
        Log.i(LOGTAG, "Processing loop started. (Thread: " + this.name + ")")

        // Preallocate magPacket to current fftSize
        var magPacket = SamplePacket(fftSize)
        magPacket.setSize(fftSize)

        var lastFrequency: Long? = null
        var lastSampleRate: Long? = null

        // performance tracking
        var totalWaitTimeNs = 0L
        var totalWorkTimeNs = 0L

        while (!stopRequested) {
            val currentFftSize = fftSize
            val currentSampleRate = sampleRate
            val currentFrequency = frequency
            if (magPacket.size() != currentFftSize) {
                magPacket = SamplePacket(currentFftSize)
                magPacket.setSize(currentFftSize)
                nativeDsp.setFftSize(currentFftSize)
            }

            val measureStartNs = System.nanoTime()

            if (nextFrameDeadlineNs == 0L) {
                nextFrameDeadlineNs = measureStartNs
            }

            val sleepNs = nextFrameDeadlineNs - measureStartNs
            var waitDurationNs = 0L
            if (sleepNs > 0L) {
                waitDurationNs = sleepNs
                LockSupport.parkNanos(sleepNs)
            }
            if (stopRequested) break

            val hop = computeHop(currentSampleRate, currentFftSize)

            val actualConsumedSamples = nativeDsp.performWindowedFftAndReturnMag(magPacket.re(), hop)
            val frameDoneNs = System.nanoTime()

            if (actualConsumedSamples < 0) {
                Log.e(LOGTAG, "run: nativePerformWindowedFftAndReturnMag returned error code $actualConsumedSamples")
                continue
            } else if (actualConsumedSamples != hop) {
                Log.d(LOGTAG, "run: nativePerformWindowedFftAndReturnMag returned $actualConsumedSamples samples (hop=$hop)")
            }

            availableSamples.addAndGet(-actualConsumedSamples)

            updateActualFps(frameDoneNs)
            GlobalPerformanceData.updateLoad("FftProcessorFPS", actualFftFramesPerSecond / 100)

            // Keep the scheduler on a fixed-time base.
            val targetPeriodNs = (1_000_000_000.0 / fftFramesPerSecond.coerceAtLeast(1f)).toLong()
            if (nextFrameDeadlineNs == 0L) {
                nextFrameDeadlineNs = frameDoneNs + targetPeriodNs
            } else {
                nextFrameDeadlineNs += targetPeriodNs
                // If we fell behind by more than one period, snap back to real time.
                if (frameDoneNs > nextFrameDeadlineNs + targetPeriodNs) {
                    nextFrameDeadlineNs = frameDoneNs + targetPeriodNs
                }
            }

            // Update signal strength in appStateRepository:
            val samplesPerHz = magPacket.size() / currentSampleRate.toFloat()
            val frequencyAtIndexZero = currentFrequency - currentSampleRate / 2
            val (channelStartFrequency, channelEndFrequency) = channelFrequencyRange
            val channelStartIndex =
                ((channelStartFrequency - frequencyAtIndexZero) * samplesPerHz).toInt()
                    .coerceIn(0, magPacket.size())
            val channelEndIndex =
                ((channelEndFrequency - frequencyAtIndexZero) * samplesPerHz).toInt()
                    .coerceIn(0, magPacket.size())
            if (channelEndIndex > channelStartIndex) {
                var sum = 0f
                val mag = magPacket.re()
                for (i in channelStartIndex until channelEndIndex) sum += mag[i]
                val averageSignalStrengh = sum / (channelEndIndex - channelStartIndex)
                onAverageSignalStrengthChanged(averageSignalStrengh)
            }

            // --- Put the results into fftProcessorData
            try {
                fftProcessorData.lock.writeLock().lock()
                fftProcessorData.frequency = currentFrequency
                fftProcessorData.sampleRate = currentSampleRate

                val frequencyChanged = currentFrequency != lastFrequency
                val sampleRateChanged = currentSampleRate != lastSampleRate
                fftProcessorData.frequencyOrSampleRateChanged =
                    frequencyChanged || sampleRateChanged

                val frequencyDiff =
                    if (lastFrequency != null) lastFrequency - currentFrequency else 0L
                val magBuffer = magPacket.re()
                lastFrequency = currentFrequency
                lastSampleRate = currentSampleRate

                // update waterfallBuffer if fftSize changed
                if (fftProcessorData.waterfallBuffer == null || fftProcessorData.waterfallBuffer!![0].size != magBuffer!!.size) {
                    fftProcessorData.waterfallBuffer =
                        Array(waterfallBufferSize) { ByteArray(magBuffer.size) }
                    fftProcessorData.waterfallBufferDirtyMap =
                        Array(waterfallBufferSize) { true }
                    fftProcessorData.writeIndex = 0
                }

                if (frequencyDiff != 0L) {
                    // shift history samples because the source frequency changed
                    val shiftOffset = (frequencyDiff * samplesPerHz).toInt()
                    val shiftLeft = shiftOffset < 0
                    if ((shiftLeft && shiftOffset * -1 < magBuffer.size) || (!shiftLeft && shiftOffset < magBuffer.size)) {
                        fftProcessorData.waterfallBuffer!!.forEach {
                            if (shiftLeft) {
                                System.arraycopy(
                                    it,
                                    shiftOffset * -1,
                                    it,
                                    0,
                                    it.size + shiftOffset
                                ) // Shift left
                                it.fill(0, it.size + shiftOffset, it.size) // Fill right side
                            } else {
                                System.arraycopy(
                                    it,
                                    0,
                                    it,
                                    shiftOffset,
                                    it.size - shiftOffset
                                ) // Shift right
                                it.fill(0, 0, shiftOffset) // Fill left side
                            }
                        }
                    } else {
                        // clear entire history
                        fftProcessorData.waterfallBuffer!!.forEach { it.fill(0) }
                    }
                    fftProcessorData.waterfallBufferDirtyMap!!.fill(true)
                } else if (sampleRateChanged) {
                    // clear entire history
                    fftProcessorData.waterfallBuffer!!.forEach { it.fill(0) }
                    fftProcessorData.waterfallBufferDirtyMap!!.fill(true)
                }

                // copy newest samples into history
                for (i in magBuffer.indices) // convert to float -> byte:
                    fftProcessorData.waterfallBuffer!![fftProcessorData.writeIndex][i] =
                        ((magBuffer[i].coerceIn(MIN_DB, MAX_DB) - MIN_DB) * SCALE).toInt()
                            .toByte()
                fftProcessorData.waterfallBufferDirtyMap!![fftProcessorData.writeIndex] = true

                // debug
                //Log.d(LOGTAG, "run: writeIndex=${fftProcessorData.writeIndex} magBuffer[0]=${magBuffer[0]}  magBuffer[5]=${magBuffer[5]}  magBuffer[10]=${magBuffer[10]}")

                // update the read/write indices
                fftProcessorData.readIndex = fftProcessorData.writeIndex
                fftProcessorData.writeIndex =
                    if (fftProcessorData.writeIndex == 0) fftProcessorData.waterfallBuffer!!.size - 1 else fftProcessorData.writeIndex - 1

                // Update Peak Hold (unchanged)
                if (fftPeakHold) {
                    val arraySize = fftProcessorData.waterfallBuffer!![0].size
                    if (fftProcessorData.peaks == null || fftProcessorData.peaks!!.size != arraySize) {
                        fftProcessorData.peaks = FloatArray(arraySize)
                        for (i in fftProcessorData.peaks!!.indices) fftProcessorData.peaks!![i] =
                            -999999f
                    }
                    if (fftProcessorData.frequencyOrSampleRateChanged)
                        for (i in fftProcessorData.peaks!!.indices) fftProcessorData.peaks!![i] =
                            -999999f
                    for (i in fftProcessorData.waterfallBuffer!![fftProcessorData.readIndex].indices)
                        fftProcessorData.peaks!![i] =
                            max(fftProcessorData.peaks!![i], magBuffer[i])
                } else {
                    fftProcessorData.peaks = null
                }
            } finally {
                fftProcessorData.lock.writeLock().unlock()
            }

            // Update timing metrics
            val fftDurationNs = frameDoneNs - measureStartNs
            totalWorkTimeNs += fftDurationNs
            totalWaitTimeNs += waitDurationNs

            if (totalWorkTimeNs + totalWaitTimeNs > 1_000_000_000L) { // update every second
                val totalCycleTime: Long = totalWaitTimeNs + totalWorkTimeNs
                val currentLoad = totalWorkTimeNs.toDouble() / totalCycleTime
                GlobalPerformanceData.updateLoad("FftProcessor", currentLoad.toFloat())
                totalWorkTimeNs = 0
                totalWaitTimeNs = 0
            }

            //Log.d(LOGTAG, "run: actualFftFramesPerSecond=$actualFftFramesPerSecond")
        }

        // clean up native resources
        nativeDsp.release()

        this.stopRequested = true
        Log.i(LOGTAG, "Processing loop stopped. (Thread: " + this.name + ")")
    }
}