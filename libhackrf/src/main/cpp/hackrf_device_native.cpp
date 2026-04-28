#include <jni.h>
#include <string>
#include <cstring>
#include <cstdint>
#include <atomic>
#include <mutex>
#include <android/log.h>
#include <libusb.h>
#include "libhackrf/hackrf.h"

/**
 * <h1>RF Analyzer - hackrf device native code</h1>
 *
 * Module:      hackrf_device_native.cpp
 * Description: The native jni code which is used by HackrfDevice.kt
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

#define LOG_TAG "NativeLibHackRF"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ============================================================
// Globals
// ============================================================
static JavaVM *g_vm = nullptr;
static jobject g_hackrfDeviceObj = nullptr;
static jmethodID g_getEmptyBufferMethod = nullptr;
static jmethodID g_onSamplesReadyMethod = nullptr;

// Serializes lifecycle/state transitions for the single native device handle.
static std::mutex g_nativeStateMutex;

// Makes nativeClose() idempotent across threads.
static std::atomic<bool> g_nativeCloseStarted{false};

static inline bool is_close_started() {
    return g_nativeCloseStarted.load(std::memory_order_acquire);
}

static inline void clear_callback_state(JNIEnv* env) {
    if (g_hackrfDeviceObj != nullptr) {
        env->DeleteGlobalRef(g_hackrfDeviceObj);
        g_hackrfDeviceObj = nullptr;
    }
    g_getEmptyBufferMethod = nullptr;
    g_onSamplesReadyMethod = nullptr;
}

// ============================================================
// JNI: Cache JavaVM
// ============================================================
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

// Helper to cast long to hackrf_device*
static inline hackrf_device* get_device_ptr(jlong nativePtr) {
    return reinterpret_cast<hackrf_device*>(nativePtr);
}

// ============================================================
// JNI: get library version string
// Java: public static native String getLibraryVersionString();
// ============================================================
extern "C" JNIEXPORT jstring JNICALL
Java_com_mantz_1it_libhackrf_HackrfDevice_getLibraryVersionString(
        JNIEnv* env,
        jclass) {

    const char* ver = hackrf_library_version();
    const char* rel = hackrf_library_release();

    const libusb_version* usb_version = libusb_get_version();

    char version_str[256];
    snprintf(version_str, sizeof(version_str),
             "HackRF lib: %s (%s) | libusb: %d.%d.%d.%d%s",
             ver ? ver : "unknown",
             rel ? rel : "unknown",
             usb_version->major, usb_version->minor, usb_version->micro, usb_version->nano,
             usb_version->rc ? usb_version->rc : "");
    return env->NewStringUTF(version_str);
}

// ============================================================
// JNI: Open device by FD
// Java: private static native long nativeOpenFd(int fd);
// Returns: jlong pointer on success OR error code (negative) on failure (same pattern as Airspy wrapper)
// ============================================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_mantz_1it_libhackrf_HackrfDevice_nativeOpenFd(
        JNIEnv* env,
        jclass /* clazz */,
        jint fd) {

    std::lock_guard<std::mutex> lock(g_nativeStateMutex);

    hackrf_device* device = nullptr;
    LOGI("Attempting to open HackRF device with fd: %d", fd);

    // Ensure library initialised
    // LibUSB does not support device discovery on android
    libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, NULL);
    int r = hackrf_init();
    if (r != HACKRF_SUCCESS) {
        LOGE("hackrf_init failed: %d", r);
        return static_cast<jlong>(r);
    }

    int result = hackrf_open_fd(fd, &device);
    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_open_fd failed: %d", result);
        return static_cast<jlong>(result);
    }

    if (device == nullptr) {
        LOGE("nativeOpenFd: hackrf_open_fd returned success but device is null");
        return static_cast<jlong>(HACKRF_ERROR_OTHER);
    }

    // Allow a later close for this newly opened device.
    g_nativeCloseStarted.store(false, std::memory_order_release);

    LOGI("HackRF device opened successfully, pointer: %p", device);
    return reinterpret_cast<jlong>(device);
}

// ============================================================
// JNI: Read version string
// Java: private native String nativeVersionStringRead(long nativePtr);
// ============================================================
extern "C" JNIEXPORT jstring JNICALL
Java_com_mantz_1it_libhackrf_HackrfDevice_nativeVersionStringRead(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr) {

    std::lock_guard<std::mutex> lock(g_nativeStateMutex);

    if (is_close_started()) {
        LOGE("nativeVersionStringRead: device is closing/closed");
        return nullptr;
    }

    hackrf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeVersionStringRead: Invalid native pointer");
        return nullptr;
    }

    char version[128];
    int result = hackrf_version_string_read(device, reinterpret_cast<char *>(&version), sizeof(version));
    if (result != HACKRF_SUCCESS) {
        LOGE("Failed to read hackrf version string, error: %d", result);
        return nullptr;
    }
    return env->NewStringUTF(version);
}

// ============================================================
// JNI: Close device
// Java: private native int nativeClose(long nativePtr);
// ============================================================
extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libhackrf_HackrfDevice_nativeClose(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr) {

    std::lock_guard<std::mutex> lock(g_nativeStateMutex);

    if (is_close_started()) {
        LOGI("nativeClose: close already in progress or already done, skipping");
        return HACKRF_SUCCESS;
    }

    hackrf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeClose: Invalid native pointer or device already closed");
        return HACKRF_ERROR_INVALID_PARAM;
    }

    g_nativeCloseStarted.store(true, std::memory_order_release);

    // Drop Java callback references before closing so stop/close cannot race with callback cleanup.
    clear_callback_state(env);

    LOGI("Closing HackRF device, pointer: %p", device);
    int result = hackrf_close(device);
    if (result != HACKRF_SUCCESS) {
        LOGE("Failed to close HackRF device, error: %d", result);
    }
    // Optionally call hackrf_exit() elsewhere when cleaning up resources
    return result;
}

// ============================================================
// JNI: Is streaming
// Java: private native boolean nativeIsStreaming(long nativePtr);
// ============================================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_mantz_1it_libhackrf_HackrfDevice_nativeIsStreaming(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr) {

    std::lock_guard<std::mutex> lock(g_nativeStateMutex);

    if (is_close_started()) {
        LOGE("nativeIsStreaming: device is closing/closed");
        return JNI_FALSE;
    }

    hackrf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeIsStreaming: Invalid native pointer");
        return JNI_FALSE;
    }

    int streaming_status = hackrf_is_streaming(device);
    return (streaming_status == HACKRF_TRUE) ? JNI_TRUE : JNI_FALSE;
}

// ============================================================
// JNI: Set frequency (Hz)
// Java: private native int nativeSetFrequency(long nativePtr, long freqHz);
// ============================================================
extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libhackrf_HackrfDevice_nativeSetFrequency(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr,
        jlong freq_hz) {

    std::lock_guard<std::mutex> lock(g_nativeStateMutex);

    if (is_close_started()) {
        LOGE("nativeSetFrequency: device is closing/closed");
        return HACKRF_ERROR_INVALID_PARAM;
    }

    hackrf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeSetFrequency: Invalid native pointer");
        return HACKRF_ERROR_INVALID_PARAM;
    }
    uint64_t freq = static_cast<uint64_t>(freq_hz);
    LOGI("Setting HackRF frequency to %llu Hz for device %p", (unsigned long long)freq, device);
    int result = hackrf_set_freq(device, freq);
    if (result != HACKRF_SUCCESS) {
        LOGE("Failed to set frequency, error: %d", result);
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libhackrf_HackrfDevice_nativeSetBasebandFilterBandwidth(
        JNIEnv* env,
        jobject thiz,
        jlong device_ptr,
        jint bandwidth)
{
    std::lock_guard<std::mutex> lock(g_nativeStateMutex);

    if (is_close_started()) {
        LOGE("nativeSetBasebandFilterBandwidth: device is closing/closed");
        return HACKRF_ERROR_INVALID_PARAM;
    }

    hackrf_device* device = reinterpret_cast<hackrf_device*>(device_ptr);

    if(device == nullptr)
        return HACKRF_ERROR_INVALID_PARAM;

    uint32_t bw = hackrf_compute_baseband_filter_bw_round_down_lt((uint32_t)bandwidth);

    return hackrf_set_baseband_filter_bandwidth(device, bw);
}

// ============================================================
// JNI: Set sample rate (double)
// Java: private native int nativeSetSampleRate(long nativePtr, double sampleRate);
// ============================================================
extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libhackrf_HackrfDevice_nativeSetSampleRate(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr,
        jdouble samplerate) {

    std::lock_guard<std::mutex> lock(g_nativeStateMutex);

    if (is_close_started()) {
        LOGE("nativeSetSampleRate: device is closing/closed");
        return HACKRF_ERROR_INVALID_PARAM;
    }

    hackrf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeSetSampleRate: Invalid native pointer");
        return HACKRF_ERROR_INVALID_PARAM;
    }
    double rate = static_cast<double>(samplerate);
    LOGI("Setting HackRF sample rate to %f for device %p", rate, device);
    int result = hackrf_set_sample_rate(device, rate);
    if (result != HACKRF_SUCCESS) {
        LOGE("Failed to set sample rate, error: %d", result);
    }
    return result;
}

// ============================================================
// JNI: Set LNA/VGA gains
// Java: private native int nativeSetLnaGain(long nativePtr, int gain);
// Java: private native int nativeSetVgaGain(long nativePtr, int gain);
// ============================================================
extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libhackrf_HackrfDevice_nativeSetLnaGain(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr,
        jint value) {

    std::lock_guard<std::mutex> lock(g_nativeStateMutex);

    if (is_close_started()) {
        LOGE("nativeSetLnaGain: device is closing/closed");
        return HACKRF_ERROR_INVALID_PARAM;
    }

    hackrf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeSetLnaGain: Invalid native pointer");
        return HACKRF_ERROR_INVALID_PARAM;
    }
    uint32_t gain_value = static_cast<uint32_t>(value);
    LOGI("Setting HackRF LNA gain to %u for device %p", gain_value, device);
    int result = hackrf_set_lna_gain(device, gain_value);
    if (result != HACKRF_SUCCESS) {
        LOGE("Failed to set LNA gain, error: %d", result);
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libhackrf_HackrfDevice_nativeSetVgaGain(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr,
        jint value) {

    std::lock_guard<std::mutex> lock(g_nativeStateMutex);

    if (is_close_started()) {
        LOGE("nativeSetVgaGain: device is closing/closed");
        return HACKRF_ERROR_INVALID_PARAM;
    }

    hackrf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeSetVgaGain: Invalid native pointer");
        return HACKRF_ERROR_INVALID_PARAM;
    }
    uint32_t gain_value = static_cast<uint32_t>(value);
    LOGI("Setting HackRF VGA gain to %u for device %p", gain_value, device);
    int result = hackrf_set_vga_gain(device, gain_value);
    if (result != HACKRF_SUCCESS) {
        LOGE("Failed to set VGA gain, error: %d", result);
    }
    return result;
}

// ============================================================
// JNI: Enable / Disable RF Amplifier
// Java: private native int nativeSetAmpEnable(long nativePtr, boolean enable);
// ============================================================
extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libhackrf_HackrfDevice_nativeSetAmpEnable(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr,
        jboolean value) {

    std::lock_guard<std::mutex> lock(g_nativeStateMutex);

    if (is_close_started()) {
        LOGE("nativeSetAmpEnable: device is closing/closed");
        return HACKRF_ERROR_INVALID_PARAM;
    }

    hackrf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeSetAmpEnable: Invalid native pointer");
        return HACKRF_ERROR_INVALID_PARAM;
    }

    uint8_t enable = (value == JNI_TRUE) ? 1 : 0;
    LOGI("Setting HackRF amplifier to %d for device %p", enable, device);

    int result = hackrf_set_amp_enable(device, enable);
    if (result != HACKRF_SUCCESS) {
        LOGE("Failed to set amplifier state, error: %d", result);
    }

    return result;
}

// ============================================================
// JNI: Enable / Disable Antenna Port Power (Bias-T)
// Java: private native int nativeSetAntennaEnable(long nativePtr, boolean enable);
// ============================================================
extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libhackrf_HackrfDevice_nativeSetAntennaEnable(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr,
        jboolean value) {

    std::lock_guard<std::mutex> lock(g_nativeStateMutex);

    if (is_close_started()) {
        LOGE("nativeSetAntennaEnable: device is closing/closed");
        return HACKRF_ERROR_INVALID_PARAM;
    }

    hackrf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeSetAntennaEnable: Invalid native pointer");
        return HACKRF_ERROR_INVALID_PARAM;
    }

    uint8_t enable = (value == JNI_TRUE) ? 1 : 0;
    LOGI("Setting HackRF antenna bias power to %d for device %p", enable, device);

    int result = hackrf_set_antenna_enable(device, enable);
    if (result != HACKRF_SUCCESS) {
        LOGE("Failed to set antenna power state, error: %d", result);
    }

    return result;
}

// ============================================================
// HackRF RX callback (runs on hackrf/libusb thread)
// callback signature: int (*hackrf_sample_block_cb_fn)(hackrf_transfer* transfer)
// ============================================================
static int hackrf_callback(hackrf_transfer* transfer) {
    JNIEnv *env = nullptr;

    if (g_vm == nullptr) {
        return 0;
    }

    if (g_vm->AttachCurrentThread(&env, nullptr) != 0) {
        LOGE("hackrf_callback: Failed to attach thread to JVM");
        return 0;
    }

    // NO LOCKING HERE - take a snapshot instead
    if (is_close_started()) {
        return 0;
    }

    jobject obj = g_hackrfDeviceObj;
    jmethodID getEmptyMethod = g_getEmptyBufferMethod;
    jmethodID onSamplesMethod = g_onSamplesReadyMethod;

    if (obj == nullptr || getEmptyMethod == nullptr || onSamplesMethod == nullptr) {
        return 0;
    }

    // Create local ref from global ref
    jobject localHackrfObj = env->NewLocalRef(obj);
    if (localHackrfObj == nullptr) {
        return 0;
    }

    // Call Kotlin to get an empty buffer
    jbyteArray buffer = (jbyteArray) env->CallObjectMethod(localHackrfObj, getEmptyMethod);
    if (buffer == nullptr) {
        LOGE("hackrf_callback: getEmptyBuffer returned null");
        env->DeleteLocalRef(localHackrfObj);
        return 0;
    }

    // Determine how many bytes we can copy
    jsize buf_len = env->GetArrayLength(buffer);
    size_t to_copy = transfer->valid_length;
    if ((size_t)buf_len < to_copy) {
        // Avoid overflow, copy only what fits
        to_copy = static_cast<size_t>(buf_len);
    }

    // Copy transfer->buffer into Java byte array
    env->SetByteArrayRegion(buffer, 0, static_cast<jsize>(to_copy), reinterpret_cast<const jbyte*>(transfer->buffer));

    // Notify Kotlin that samples are ready
    env->CallVoidMethod(localHackrfObj, onSamplesMethod, buffer);

    // Release local refs
    env->DeleteLocalRef(buffer);
    env->DeleteLocalRef(localHackrfObj);

    return 0;
}
// ============================================================
// JNI: Start RX
// Java: private native int nativeStartRX(long nativePtr);
// ============================================================
extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libhackrf_HackrfDevice_nativeStartRX(
        JNIEnv* env,
        jobject thiz,
        jlong nativePtr) {

    std::lock_guard<std::mutex> lock(g_nativeStateMutex);

    if (is_close_started()) {
        LOGE("nativeStartRX: device is closing/closed");
        return HACKRF_ERROR_INVALID_PARAM;
    }

    hackrf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeStartRX: Invalid native pointer");
        return HACKRF_ERROR_INVALID_PARAM;
    }

    // If a previous callback object exists, clear it before creating a new one.
    if (g_hackrfDeviceObj != nullptr) {
        env->DeleteGlobalRef(g_hackrfDeviceObj);
        g_hackrfDeviceObj = nullptr;
    }

    // Keep global reference to HackrfDevice instance
    g_hackrfDeviceObj = env->NewGlobalRef(thiz);
    if (g_hackrfDeviceObj == nullptr) {
        LOGE("nativeStartRX: Failed to create global ref");
        return HACKRF_ERROR_OTHER;
    }

    // Resolve Java methods for our callback
    jclass cls = env->GetObjectClass(thiz);
    if (cls == nullptr) {
        LOGE("nativeStartRX: Failed to get object class");
        clear_callback_state(env);
        return HACKRF_ERROR_OTHER;
    }

    g_getEmptyBufferMethod = env->GetMethodID(cls, "getEmptyBuffer", "()[B");
    g_onSamplesReadyMethod = env->GetMethodID(cls, "onSamplesReady", "([B)V");
    env->DeleteLocalRef(cls);

    if (g_getEmptyBufferMethod == nullptr || g_onSamplesReadyMethod == nullptr) {
        LOGE("nativeStartRX: Failed to resolve callback methods");
        clear_callback_state(env);
        return HACKRF_ERROR_OTHER;
    }

    // Start streaming with callback
    int result = hackrf_start_rx(device, hackrf_callback, nullptr);
    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_start_rx() failed: %d", result);

        // Cleanup callback state if start fails.
        clear_callback_state(env);

        // close on failure to avoid resource leak (consistent with Airspy style)
        hackrf_close(device);
        return result;
    }

    LOGI("nativeStartRX: HackRF streaming started");
    return result;
}

// ============================================================
// JNI: Stop RX
// Java: private native int nativeStopRX(long nativePtr);
// ============================================================
extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libhackrf_HackrfDevice_nativeStopRX(
        JNIEnv* env,
        jobject /* thiz */,
        jlong nativePtr) {

    std::lock_guard<std::mutex> lock(g_nativeStateMutex);

    if (is_close_started()) {
        LOGI("nativeStopRX: device is closing/closed");
        return HACKRF_SUCCESS;
    }

    hackrf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeStopRX: Invalid native pointer");
        return HACKRF_ERROR_INVALID_PARAM;
    }

    // Stop streaming
    int result = hackrf_stop_rx(device);

    // Free global ref / callback state
    clear_callback_state(env);

    LOGI("nativeStopRX: HackRF streaming stopped");
    return (result == HACKRF_SUCCESS) ? HACKRF_SUCCESS : result;
}