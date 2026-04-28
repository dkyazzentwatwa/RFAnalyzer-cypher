package com.mantz_it.nativedsp

/**
 * Kotlin wrapper for the native DSP FFT ringbuffer implementation.
 *
 * Usage:
 *  - call init(fftSize, bufferCapacity) once (or call init with a small fftSize then call setFftSize later)
 *  - continuously call addNewSamples(re, im) as SDR samples arrive
 *  - call performWindowedFftAndReturnMag(magOut, hop) on your display/timer thread to get FFT frames
 *  - call setFftSize(N) to change FFT size at runtime without losing samples
 *  - call release() when finished
 *
 * Notes:
 *  - All sizes are in complex samples unless noted.
 *  - The returned magOut uses centered frequency layout (0 frequency in the middle) and contains
 *    logarithmic magnitude in dB (10*log10(magnitude + eps)).
 */
class NativeDsp {

    companion object {
        init {
            System.loadLibrary("nativedsp")
        }
    }

    private var initialized = false
    private var fftSize = 0
    private var bufferCapacity = 0

    /**
     * Initialize native resources.
     * @param fftSizeComplex number of complex samples for FFT (N)
     * @param bufferCapacityComplex capacity of ring buffer in complex samples (must be >= 1)
     *                            If bufferCapacityComplex < fftSizeComplex it will be grown on first setFftSize/use.
     */
    fun init(fftSizeComplex: Int, bufferCapacityComplex: Int = fftSizeComplex * 4): Boolean {
        if (fftSizeComplex <= 0 || bufferCapacityComplex <= 0) return false
        val ok = nativeInit(fftSizeComplex, bufferCapacityComplex)
        if (ok) {
            initialized = true
            fftSize = fftSizeComplex
            bufferCapacity = bufferCapacityComplex
        }
        return ok
    }

    /**
     * Release native resources.
     */
    fun release() {
        if (initialized) {
            nativeRelease()
            initialized = false
            fftSize = 0
            bufferCapacity = 0
        }
    }

    /**
     * Change the FFT size at runtime. Does not discard the ring buffer contents.
     * If the new fftSize is larger than the current ring buffer capacity, the native
     * implementation will grow the ring buffer and preserve samples.
     *
     * Returns true on success.
     */
    fun setFftSize(newFftSize: Int): Boolean {
        if (!initialized) return false
        if (newFftSize <= 0) return false
        val ok = nativeSetFftSize(newFftSize)
        if (ok) {
            fftSize = newFftSize
        }
        return ok
    }

    /**
     * Append new complex samples to the native circular buffer.
     *
     * Overwrites oldest samples when buffer is full.
     */
    fun addNewSamples(interleavedSamples: FloatArray): Int {
        return nativeAddNewSamples(interleavedSamples)
    }

    /**
     * Perform windowed FFT using the current read position of the ring buffer.
     *
     * - magOut must have length == current fftSize (number of bins).
     * - numberOfConsumedSamples is the *upper limit* on how many complex samples to advance.
     *   The native method will advance at most this many samples; if fewer samples are available
     *   it will advance to the end of available samples and return that actual number.
     *
     * Returns:
     *  - >= 0 : actual number of consumed (advanced) complex samples
     *  - < 0  : error code (see nativedsp.cpp error codes)
     */
    fun performWindowedFftAndReturnMag(magOut: FloatArray, numberOfConsumedSamples: Int): Int {
        if (!initialized) return -1
        if (magOut.size != fftSize) return -2
        if (numberOfConsumedSamples <= 0) return -3
        return nativePerformWindowedFftAndReturnMag(magOut, numberOfConsumedSamples)
    }

    // --- native methods ---
    private external fun nativeInit(fftSize: Int, bufferCapacity: Int): Boolean
    private external fun nativeRelease()
    private external fun nativeSetFftSize(newFftSize: Int): Boolean
    private external fun nativeAddNewSamples(interleavedSamples: FloatArray): Int
    private external fun nativePerformWindowedFftAndReturnMag(magOut: FloatArray, numberOfConsumedSamples: Int): Int
}