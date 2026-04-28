package com.mantz_it.rfanalyzer.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.mantz_it.libairspyhf.AirspyHFDevice

/**
 * <h1>RF Analyzer - AirspyHF Source</h1>
 *
 * Module:      AirspyHfSource.kt
 * Description: Source Class representing an AirspyHF Device in RF Analyzer
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
class AirspyHfSource : IQSourceInterface {

    companion object {
        private const val TAG = "AirspyHFSource"
        private const val ACTION_USB_PERMISSION = "com.mantz_it.rfanalyzer.AIRSPYHF_USB_PERMISSION"

        const val MIN_FREQUENCY = 500000L
        const val MAX_FREQUENCY = 260000000L
        const val MIN_ATTENUATION = 0
        const val MAX_ATTENUATION = 8
        const val ATTENUATION_STEP = 6 // dB
    }

    private var airspyHFDevice: AirspyHFDevice? = null
    private val converter = Float32IQConverter()
    private var iqSourceCallback: IQSourceInterface.Callback? = null
    private var sampleRate: Int = 0
    private var frequency: Long = 0

    override fun getSampleRate(): Int {
        return sampleRate
    }

    override fun setSampleRate(newSampleRate: Int) {
        sampleRate = newSampleRate
        airspyHFDevice?.setSampleRate(newSampleRate)
        converter.setSampleRate(newSampleRate)
        airspyHFDevice?.flushBufferQueue()
    }

    override fun getFrequency(): Long {
        return frequency + frequencyOffset
    }

    override fun setFrequency(newFrequency: Long) {
        frequency = newFrequency - frequencyOffset
        airspyHFDevice?.setFrequency(frequency.toInt())
        converter.frequency = newFrequency
        airspyHFDevice?.flushBufferQueue()
    }

    var agcEnabled: Boolean = false
        set(value) {
            field = value
            airspyHFDevice?.setHfAgcEnabled(value)
            if(value) {
                airspyHFDevice?.setHfAgcThreshold(agcThreshold)
            } else {
                airspyHFDevice?.setHfAttenuation(attenuation)
            }
        }

    var agcThreshold: Boolean = false
        set(value) {
            field = value
            if (agcEnabled)
                airspyHFDevice?.setHfAgcThreshold(value)
        }

    var attenuation: Int = 0
        set(value) {
            field = value.coerceIn(MIN_ATTENUATION, MAX_ATTENUATION)
            if (!agcEnabled)
                airspyHFDevice?.setHfAttenuation(value.coerceIn(MIN_ATTENUATION, MAX_ATTENUATION))
        }

    var lnaEnabled: Boolean = false
        set(value) {
            field = value
            airspyHFDevice?.setHfLnaEnabled(value)
        }

    var frequencyOffset: Int = 0
        set(value) {
            field = value
            converter.frequency = frequency + value
        }

    override fun open(
        context: Context,
        callback: IQSourceInterface.Callback?
    ): Boolean {
        if (airspyHFDevice != null) {
            Log.w(TAG, "open: airspyHFDevice is already open (not null).")
            return false
        }
        if (callback == null) {
            Log.w(TAG, "open: callback is null.")
            return false
        }
        iqSourceCallback = callback

        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList.values.firstOrNull {
            it.vendorId == 0x03EB && it.productId == 0x800C
        }
        if (device == null) {
            Log.i(TAG, "open: No AirspyHF device found.")
            usbManager.deviceList.values.forEach {
                Log.i(TAG, "open: Unknown USB device: ${it.deviceName} (VendorId: ${it.vendorId}, ProductId: ${it.productId})")
            }
            return false
        }
        Log.i(TAG, "open: device=$device (vendorid: ${device.vendorId} productId: ${device.productId})")
        if (usbManager.hasPermission(device)) {
            openAirspyHFDevice(device, context)
        } else {
            // Register broadcast receiver BEFORE requesting permission
            val usbPermissionReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (ACTION_USB_PERMISSION == intent.action) {
                        synchronized(this) {
                            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                                if (device != null) {
                                    Log.i(TAG, "USB permission granted for device: ${device.productName}")
                                    openAirspyHFDevice(device, context)
                                }
                            } else {
                                Log.w(TAG, "USB permission denied for device: ${device?.productName}")
                                Toast.makeText(context, "USB permission denied.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    context.unregisterReceiver(this)
                }
            }
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            ContextCompat.registerReceiver(context, usbPermissionReceiver, filter,ContextCompat.RECEIVER_NOT_EXPORTED)

            // Request permission
            // Note: setting the package name of the inner intent makes it explicit
            // From Android 14 it is required that mutable PendingIntents have explicit inner intents!
            val innerIntent = Intent(ACTION_USB_PERMISSION)
            innerIntent.setPackage(context.packageName)
            val permissionIntent = android.app.PendingIntent.getBroadcast(context, 0, innerIntent, android.app.PendingIntent.FLAG_MUTABLE)
            Log.i(TAG, "open: requesting permission for device: $device")
            usbManager.requestPermission(device, permissionIntent)
        }
        return true
    }

    private fun openAirspyHFDevice(device: UsbDevice, context: Context): Boolean {
        var errorMsg: String? = null
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (usbManager.hasPermission(device)) {
            val connection = usbManager.openDevice(device)
            if (connection != null) {
                val (deviceHandle, airspyError) = AirspyHFDevice.open(connection.fileDescriptor)
                if (deviceHandle != null) {
                    airspyHFDevice = deviceHandle
                    Log.i(TAG,"openAirspyHFDevice: AirspyHF Board Version String: ${deviceHandle.getVersionString() ?: "<unknown>"}")
                    iqSourceCallback?.onIQSourceReady(this)
                    return true
                } else if (airspyError != null) {
                    Log.w(TAG, "openAirspyHFDevice: Error on AirspyHFDevice.open: $airspyError")
                    errorMsg = airspyError.toString()
                }
            } else {
                Log.w(TAG, "openAirspyHFDevice: Error opening USB connection.")
                errorMsg = "Error opening USB connection."
            }
        } else {
            Log.w(TAG, "openAirspyHFDevice: No permission to open AirspyHF device.")
            errorMsg = "No permission"
        }
        iqSourceCallback?.onIQSourceError(this, "Failed to open AirspyHF device ($errorMsg)")
        Toast.makeText(context, "Failed to open AirspyHF device ($errorMsg).", Toast.LENGTH_SHORT).show()
        return false
    }

    override fun isOpen(): Boolean {
        airspyHFDevice?.let {
            val versionString = it.getVersionString()
            if (versionString != null)
                return true
        }
        return false
    }

    override fun close(): Boolean {
        airspyHFDevice?.let {
            return it.close()
        }
        return true
    }

    override fun getName(): String? {
        airspyHFDevice?.let {
            val versionString = it.getVersionString()
            if (versionString != null)
                return "AirspyHF ($versionString)"
        }
        return "AirspyHF"
    }

    override fun getMaxFrequency(): Long {
        return MAX_FREQUENCY + frequencyOffset
    }

    override fun getMinFrequency(): Long {
        return MIN_FREQUENCY + frequencyOffset
    }

    override fun getNextHigherOptimalSampleRate(sampleRate: Int): Int {
        val supportedRates = getSupportedSampleRates() ?: return sampleRate
        return supportedRates.firstOrNull { it > sampleRate } ?: supportedRates.lastOrNull() ?: sampleRate
    }

    override fun getNextLowerOptimalSampleRate(sampleRate: Int): Int {
        val supportedRates = getSupportedSampleRates() ?: return sampleRate
        return supportedRates.reversed().firstOrNull { it < sampleRate } ?: supportedRates.firstOrNull() ?: sampleRate
    }

    override fun getSupportedSampleRates(): IntArray? {
        val supportedSampleRates = airspyHFDevice?.getSupportedSampleRates()
        return if (supportedSampleRates != null && supportedSampleRates.isNotEmpty()) {
            supportedSampleRates.sorted().toIntArray()
        } else {
            listOf(0).toIntArray()
        }
    }

    override fun getPacketSize(): Int {
        return AirspyHFDevice.BUFFER_SIZE
    }

    override fun getBytesPerSample(): Int {
        return 8
    }

    override fun getPacket(timeout: Int): ByteArray? {
        if (airspyHFDevice == null)
            Log.w(TAG, "getPacket: airspyHFDevice is null.")
        val packet = airspyHFDevice?.getSampleBuffer(timeout)
        if (packet == null && !(airspyHFDevice?.isStreaming() ?: false)) {
            Log.w(TAG, "getPacket: airspyHFDevice did not return a packet and is not streaming. report source error..")
            iqSourceCallback?.onIQSourceError(this, "AirspyHF stopped streaming")
            return null
        }
        return packet
    }

    override fun returnPacket(buffer: ByteArray?) {
        if (buffer == null)
            return
        if (airspyHFDevice == null)
            Log.w(TAG, "returnPacket: airspyHFDevice is null.")
        airspyHFDevice?.returnSampleBuffer(buffer)
    }

    override fun startSampling() {
        if (airspyHFDevice == null)
            Log.w(TAG, "startSampling: airspyHFDevice is null.")
        airspyHFDevice?.setSampleRate(sampleRate)
        airspyHFDevice?.setFrequency(frequency.toInt())
        airspyHFDevice?.setHfAgcEnabled(agcEnabled)
        if (agcEnabled) {
            airspyHFDevice?.setHfAgcThreshold(agcThreshold)
        } else {
            airspyHFDevice?.setHfAttenuation(attenuation)
        }
        airspyHFDevice?.setHfLnaEnabled(lnaEnabled)
        airspyHFDevice?.startRX()
        airspyHFDevice?.setFrequency(frequency.toInt())
        Thread.sleep(1000) // Wait for RX to start (AirspyHF seems a bit slower than other sources; takes at least 1 sec to deliver samples)
    }

    override fun stopSampling() {
        if (airspyHFDevice == null)
            Log.w(TAG, "stopSampling: airspyHFDevice is null.")
        airspyHFDevice?.stopRX()
    }

    override fun fillPacketIntoSamplePacket(
        packet: ByteArray?,
        samplePacket: SamplePacket?
    ): Int {
        if (packet == null || samplePacket == null) {
            Log.w(TAG, "fillPacketIntoSamplePacket: packet or samplePacket is null.")
            return 0
        }
        return converter.fillPacketIntoSamplePacket(packet, samplePacket)
    }

    override fun fillPacketIntoInterleavedBuffer(packet: ByteArray?, interleavedBuffer: FloatArray?): Boolean {
        if (packet == null || interleavedBuffer == null) {
            Log.w(TAG, "fillPacketIntoInterleavedBuffer: packet or interleavedBuffer is null.")
            return false
        }
        return converter.fillPacketIntoInterleavedBuffer(packet, interleavedBuffer)
    }

    override fun mixPacketIntoSamplePacket(
        packet: ByteArray?,
        samplePacket: SamplePacket?,
        channelFrequency: Long
    ): Int {
        if (packet == null || samplePacket == null) {
            Log.w(TAG, "mixPacketIntoSamplePacket: packet or samplePacket is null.")
            return 0
        }
        return converter.mixPacketIntoSamplePacket(packet, samplePacket, channelFrequency)
    }

}




