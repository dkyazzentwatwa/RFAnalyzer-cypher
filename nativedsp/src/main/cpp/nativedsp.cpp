#include <jni.h>
#include <string>
#include <android/log.h>
#include <vector>
#include <mutex>
#include <cmath>
#include <cstring> // memcpy, memset
#include <algorithm>
#include "pffft.h"

#define LOG_TAG "nativedsp"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Error codes returned as negative integers from nativePerformWindowedFftAndReturnMag:
static const int ERR_NOT_INITIALIZED = -1;
static const int ERR_MAG_LENGTH_MISMATCH = -2;
static const int ERR_INVALID_HOP = -3;
static const int ERR_INTERNAL = -4;
static const int ERR_INVALID_INPUT = -5;

static std::mutex g_mutex;

// FFT parameters & pffft buffers:
static int g_fftComplexSize = 0;   // N = number of complex samples for the FFT
static PFFFT_Setup* g_setup = nullptr;
static float* g_scratch = nullptr;  // scratch buffer (aligned)
static float* g_fftInput = nullptr; // interleaved complex input (2*N floats)
static float* g_fftOutput = nullptr;// interleaved complex output (2*N floats)
static float* g_window = nullptr;   // blackman window (N floats)

// Circular buffer (interleaved complex floats)
static float* g_circBuf = nullptr; // size = 2 * g_bufCapComplex floats
static int g_bufCapComplex = 0;    // capacity (complex samples)
static int g_writeIndex = 0;       // next write position (complex index)
static int g_readIndex = 0;        // read position for FFT (complex index)
static int g_samplesAvailable = 0; // number of complex samples currently stored

// small positive epsilon used when computing log to avoid -inf
static constexpr float EPSILON = 1e-12f;

static void freeAll() {
    if (g_setup) {
        pffft_destroy_setup(g_setup);
        g_setup = nullptr;
    }
    if (g_scratch) {
        pffft_aligned_free(g_scratch);
        g_scratch = nullptr;
    }
    if (g_fftInput) {
        pffft_aligned_free(g_fftInput);
        g_fftInput = nullptr;
    }
    if (g_fftOutput) {
        pffft_aligned_free(g_fftOutput);
        g_fftOutput = nullptr;
    }
    if (g_window) {
        free(g_window);
        g_window = nullptr;
    }
    if (g_circBuf) {
        pffft_aligned_free(g_circBuf);
        g_circBuf = nullptr;
    }
    g_fftComplexSize = 0;
    g_bufCapComplex = 0;
    g_writeIndex = g_readIndex = g_samplesAvailable = 0;
}

// Create Blackman window length N
static bool createWindow(int N) {
    if (N <= 0) return false;
    if (g_window) {
        free(g_window);
        g_window = nullptr;
    }
    g_window = (float*) malloc(sizeof(float) * N);
    if (!g_window) return false;
    for (int n = 0; n < N; ++n) {
        double a = 2.0 * M_PI * n / double(N - 1);
        double w = 0.42 - 0.5 * cos(a) + 0.08 * cos(2.0 * a);
        g_window[n] = (float) w;
    }
    return true;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mantz_1it_nativedsp_NativeDsp_nativeInit(JNIEnv* /*env*/, jobject /*this*/,
                                                  jint fftSizeComplex, jint bufferCapacityComplex) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (fftSizeComplex <= 0 || bufferCapacityComplex <= 0) {
        LOGE("nativeInit: invalid sizes fft=%d buf=%d", fftSizeComplex, bufferCapacityComplex);
        return JNI_FALSE;
    }

    // If already init and parameters match, do nothing
    if (g_setup && g_fftComplexSize == fftSizeComplex && g_bufCapComplex == bufferCapacityComplex) {
        return JNI_TRUE;
    }

    // Free any previous
    freeAll();

    // allocate new pffft setup and buffers
    g_fftComplexSize = fftSizeComplex;
    g_bufCapComplex = std::max(bufferCapacityComplex, 1);

    g_setup = pffft_new_setup(g_fftComplexSize, PFFFT_COMPLEX);
    if (!g_setup) {
        LOGE("pffft_new_setup failed");
        freeAll();
        return JNI_FALSE;
    }

    int floatLen = 2 * g_fftComplexSize;
    g_scratch = (float*) pffft_aligned_malloc(sizeof(float) * floatLen);
    g_fftInput = (float*) pffft_aligned_malloc(sizeof(float) * floatLen);
    g_fftOutput = (float*) pffft_aligned_malloc(sizeof(float) * floatLen);

    if (!g_scratch || !g_fftInput || !g_fftOutput) {
        LOGE("FFT buffer allocation failed");
        freeAll();
        return JNI_FALSE;
    }

    // allocate circular buffer
    g_circBuf = (float*) pffft_aligned_malloc(sizeof(float) * 2 * g_bufCapComplex);
    if (!g_circBuf) {
        LOGE("circular buffer allocation failed");
        freeAll();
        return JNI_FALSE;
    }
    memset(g_circBuf, 0, sizeof(float) * 2 * g_bufCapComplex);

    if (!createWindow(g_fftComplexSize)) {
        LOGE("createWindow failed");
        freeAll();
        return JNI_FALSE;
    }

    g_writeIndex = 0;
    g_readIndex = 0;
    g_samplesAvailable = 0;

    LOGI("nativeInit done fft=%d buf=%d", g_fftComplexSize, g_bufCapComplex);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mantz_1it_nativedsp_NativeDsp_nativeRelease(JNIEnv* /*env*/, jobject /*this*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    freeAll();
    LOGI("nativeRelease done");
}

/**
 * Set FFT size at runtime without destroying ring buffer contents.
 * If necessary, the circular buffer will be grown to at least the new fft size (preserving samples).
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_mantz_1it_nativedsp_NativeDsp_nativeSetFftSize(JNIEnv* /*env*/, jobject /*this*/,
                                                        jint newFftSize) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (newFftSize <= 0) {
        LOGE("nativeSetFftSize: invalid newFftSize %d", newFftSize);
        return JNI_FALSE;
    }

    // If not initialized yet, treat this as init request with default buffer capacity = newFftSize*4
    if (!g_setup) {
        int defaultBuf = std::max(newFftSize * 4, newFftSize);
        // Release (safe) then init
        freeAll();
        // call nativeInit logic but inline to preserve mutex and avoid recursion
        g_fftComplexSize = newFftSize;
        g_bufCapComplex = defaultBuf;
        g_setup = pffft_new_setup(g_fftComplexSize, PFFFT_COMPLEX);
        if (!g_setup) { freeAll(); return JNI_FALSE; }
        int floatLen = 2 * g_fftComplexSize;
        g_scratch = (float*) pffft_aligned_malloc(sizeof(float) * floatLen);
        g_fftInput = (float*) pffft_aligned_malloc(sizeof(float) * floatLen);
        g_fftOutput = (float*) pffft_aligned_malloc(sizeof(float) * floatLen);
        if (!g_scratch || !g_fftInput || !g_fftOutput) { freeAll(); return JNI_FALSE; }
        g_circBuf = (float*) pffft_aligned_malloc(sizeof(float) * 2 * g_bufCapComplex);
        if (!g_circBuf) { freeAll(); return JNI_FALSE; }
        memset(g_circBuf, 0, sizeof(float) * 2 * g_bufCapComplex);
        if (!createWindow(g_fftComplexSize)) { freeAll(); return JNI_FALSE; }
        g_writeIndex = g_readIndex = g_samplesAvailable = 0;
        return JNI_TRUE;
    }

    // If unchanged, nothing to do
    if (newFftSize == g_fftComplexSize) return JNI_TRUE;

    // Ensure circular buffer capacity >= newFftSize.
    if (g_bufCapComplex < newFftSize) {
        LOGE("Buffer Capacity < newFftSize: %d < %d", g_bufCapComplex, newFftSize);
        return JNI_FALSE;
    }

    // Recreate pffft setup and fft buffers for the new size while preserving circular buffer
    // Save old setup pointers
    pffft_destroy_setup(g_setup);
    g_setup = nullptr;
    pffft_aligned_free(g_scratch); g_scratch = nullptr;
    pffft_aligned_free(g_fftInput); g_fftInput = nullptr;
    pffft_aligned_free(g_fftOutput); g_fftOutput = nullptr;
    free(g_window); g_window = nullptr;

    g_fftComplexSize = newFftSize;

    g_setup = pffft_new_setup(g_fftComplexSize, PFFFT_COMPLEX);
    if (!g_setup) {
        LOGE("pffft_new_setup failed for new size %d", g_fftComplexSize);
        freeAll();
        return JNI_FALSE;
    }

    int floatLen = 2 * g_fftComplexSize;
    g_scratch = (float*) pffft_aligned_malloc(sizeof(float) * floatLen);
    g_fftInput = (float*) pffft_aligned_malloc(sizeof(float) * floatLen);
    g_fftOutput = (float*) pffft_aligned_malloc(sizeof(float) * floatLen);
    if (!g_scratch || !g_fftInput || !g_fftOutput) {
        LOGE("FFT buffer allocation failed for new size");
        freeAll();
        return JNI_FALSE;
    }

    if (!createWindow(g_fftComplexSize)) {
        LOGE("createWindow failed for new size");
        freeAll();
        return JNI_FALSE;
    }

    LOGI("nativeSetFftSize done newSize=%d bufCap=%d", g_fftComplexSize, g_bufCapComplex);
    return JNI_TRUE;
}

/**
 * Append new complex samples to circular buffer.
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_nativedsp_NativeDsp_nativeAddNewSamples(JNIEnv* env, jobject /*this*/,
                                                           jfloatArray interleavedArray) {
    if (!interleavedArray) return ERR_INVALID_INPUT;

    // 1. Get direct access to the Java array memory
    // GetPrimitiveArrayCritical is faster than GetFloatArrayElements
    // because it prevents the GC from moving the array.
    jsize totalFloats = env->GetArrayLength(interleavedArray);
    jfloat* srcPtr = (jfloat*) env->GetPrimitiveArrayCritical(interleavedArray, nullptr);
    if (!srcPtr) return ERR_INTERNAL;

    int samplesToAppend = (int)(totalFloats / 2); // 2 floats per complex sample

    {
        std::lock_guard<std::mutex> lock(g_mutex);

        if (!g_circBuf || g_bufCapComplex <= 0) {
            env->ReleasePrimitiveArrayCritical(interleavedArray, srcPtr, JNI_ABORT);
            return ERR_NOT_INITIALIZED;
        }

        int written = 0;
        while (written < samplesToAppend) {
            int maxContig = g_bufCapComplex - g_writeIndex;
            int cnt = std::min(maxContig, samplesToAppend - written);

            // 2. Direct memcpy from Java-managed pointer to Ring Buffer
            memcpy(g_circBuf + (g_writeIndex * 2),
                   srcPtr + (written * 2),
                   sizeof(float) * 2 * cnt);

            // Update pointers
            g_writeIndex = (g_writeIndex + cnt) % g_bufCapComplex;

            // Manage samplesAvailable and readIndex (overwriting logic)
            if (g_samplesAvailable + cnt <= g_bufCapComplex) {
                g_samplesAvailable += cnt;
            } else {
                int overwritten = (g_samplesAvailable + cnt) - g_bufCapComplex;
                g_readIndex = (g_readIndex + overwritten) % g_bufCapComplex;
                g_samplesAvailable = g_bufCapComplex;
                LOGD("nativeAddNewSamples: overwritten %d samples!", overwritten);
            }

            written += cnt;
        }
    }

    // 3. Release the pointer so GC can resume.
    // JNI_ABORT is used because we didn't modify the source array.
    env->ReleasePrimitiveArrayCritical(interleavedArray, srcPtr, JNI_ABORT);

    return g_samplesAvailable;
}

/**
 * Perform windowed FFT on N samples starting at read index.
 *
 * Behavior:
 *  - If fewer than N samples are available, copy available samples and zero-pad the rest.
 *  - Apply the precomputed Blackman window.
 *  - Run PFFFT (ordered complex transform).
 *  - Compute magnitude per-bin, log10 -> dB scaled by 10, center frequencies (rotate by N/2).
 *  - Advance read index by actualConsumed = min(numberOfConsumedSamples, g_samplesAvailableBefore).
 *
 * Returns:
 *  - >= 0 number of complex samples advanced (consumed)
 *  - negative error code on failure
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_nativedsp_NativeDsp_nativePerformWindowedFftAndReturnMag(JNIEnv* env, jobject /*this*/,
                                                                            jfloatArray magOutArray, jint numberOfConsumedSamples) {
    if (!magOutArray) return ERR_NOT_INITIALIZED;
    if (numberOfConsumedSamples <= 0) return ERR_INVALID_HOP;

    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_setup || !g_circBuf || !g_window) {
        LOGE("nativePerformWindowedFftAndReturnMag: not initialized");
        return ERR_NOT_INITIALIZED;
    }

    int N = g_fftComplexSize;
    if (N <= 0) return ERR_INTERNAL;

    jsize magLen = env->GetArrayLength(magOutArray);
    if (magLen != N) {
        LOGE("magOut length mismatch: %d != %d", (int)magLen, N);
        return ERR_MAG_LENGTH_MISMATCH;
    }

    // copy N complex samples from ring buffer into g_fftInput (interleaved). if fewer than N available => zero-pad.
    int availableBefore = g_samplesAvailable;
    int toCopy = std::min(availableBefore, N);

    // 1) copy contiguous first block
    if (toCopy > 0) {
        int firstBlock = std::min(toCopy, g_bufCapComplex - g_readIndex);
        if (firstBlock > 0) {
            memcpy(g_fftInput, g_circBuf + (g_readIndex * 2), sizeof(float) * 2 * firstBlock);
        }
        int rest = toCopy - firstBlock;
        if (rest > 0) {
            memcpy(g_fftInput + (2 * firstBlock), g_circBuf, sizeof(float) * 2 * rest);
        }
    }

    // 2) zero-pad remaining samples (if any)
    if (toCopy < N) {
        int zeroOffset = 2 * toCopy;
        int zeroCountFloats = 2 * (N - toCopy);
        memset(g_fftInput + zeroOffset, 0, sizeof(float) * zeroCountFloats);
        LOGD("nativePerformWindowedFftAndReturnMag: zero-padded %d samples", N - toCopy);
    }

    // Apply window in-place
    for (int n = 0; n < N; ++n) {
        float w = g_window[n];
        g_fftInput[2*n]   *= w;
        g_fftInput[2*n+1] *= w;
    }

    // Perform FFT
    pffft_transform_ordered(g_setup, g_fftInput, g_fftOutput, g_scratch, PFFFT_FORWARD);

    // Compute log magnitude (center 0 freq)
    std::vector<float> mag;
    mag.resize(N);
    const float invN = 1.0f / (float)N;
    for (int i = 0; i < N; ++i) {
        float real = g_fftOutput[2*i] * invN;
        float imag = g_fftOutput[2*i + 1] * invN;
        float power = real*real + imag*imag;
        float magnitude = sqrtf(power);
        float db = 10.0f * log10f(magnitude + EPSILON);
        int targetIndex = (i + N/2) % N;
        mag[targetIndex] = db;
    }

    // copy back to Java
    env->SetFloatArrayRegion(magOutArray, 0, N, mag.data());

    // Advance read pointer by actualConsumed = min(numberOfConsumedSamples, availableBefore)
    int actualConsumed = numberOfConsumedSamples;
    if (actualConsumed > availableBefore) actualConsumed = availableBefore;
    // If actualConsumed > 0:
    if (actualConsumed > 0) {
        g_readIndex = (g_readIndex + actualConsumed) % g_bufCapComplex;
        g_samplesAvailable -= actualConsumed;
    }

    return actualConsumed;
}