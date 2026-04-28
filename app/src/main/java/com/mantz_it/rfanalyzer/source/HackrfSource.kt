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
import com.mantz_it.libhackrf.HackrfDevice

class HackrfSource : IQSourceInterface {

    companion object {
        private const val TAG = "HackrfSource"
        private const val ACTION_USB_PERMISSION = "com.mantz_it.rfanalyzer.HACKRF_USB_PERMISSION"

        const val MIN_FREQUENCY = 1L
        const val MAX_FREQUENCY = 7250000000L

        const val MAX_SAMPLERATE = 20000000
        const val MIN_SAMPLERATE = 1000000

        const val MAX_VGA_RX_GAIN = 62
        const val MAX_LNA_GAIN = 40

        const val VGA_RX_GAIN_STEP_SIZE = 2
        const val LNA_GAIN_STEP_SIZE = 8

        val OPTIMAL_SAMPLE_RATES = intArrayOf(4000000, 6000000, 8000000, 10000000, 12500000, 16000000, 20000000)
    }

    private var hackrfDevice: HackrfDevice? = null
    private val converter = Signed8BitIQConverter()

    private var iqSourceCallback: IQSourceInterface.Callback? = null

    private var sampleRate: Int = 0
    private var frequency: Long = 0

    var lnaGain: Int = 0
        set(value) {
            field = value.coerceIn(0, MAX_LNA_GAIN)
            hackrfDevice?.setLnaGain(field)
        }

    var vgaGain: Int = 0
        set(value) {
            field = value.coerceIn(0, MAX_VGA_RX_GAIN)
            hackrfDevice?.setVgaGain(field)
        }

    var ampEnabled: Boolean = false
        set(value) {
            field = value
            hackrfDevice?.setAmpEnable(value)
        }

    var antennaPowerEnabled: Boolean = false
        set(value) {
            field = value
            hackrfDevice?.setAntennaEnable(value)
        }

    var frequencyOffset: Int = 0
        set(value) {
            field = value
            converter.frequency = frequency + value
        }

    override fun getSampleRate(): Int {
        return sampleRate
    }

    override fun setSampleRate(newSampleRate: Int) {
        sampleRate = newSampleRate.coerceIn(MIN_SAMPLERATE, MAX_SAMPLERATE)
        hackrfDevice?.setSampleRate(sampleRate.toDouble())
        hackrfDevice?.setBasebandFilterBandwidth((sampleRate*0.75).toInt())
        converter.setSampleRate(sampleRate)
        hackrfDevice?.flushBufferQueue()
    }

    override fun getFrequency(): Long {
        return frequency + frequencyOffset
    }

    override fun setFrequency(newFrequency: Long) {
        frequency = newFrequency - frequencyOffset
        hackrfDevice?.setFrequency(frequency)
        converter.frequency = newFrequency
        hackrfDevice?.flushBufferQueue()
    }

    override fun open(context: Context, callback: IQSourceInterface.Callback?): Boolean {
        if (hackrfDevice != null) {
            Log.w(TAG, "open: hackrfDevice already open.")
            return false
        }

        if (callback == null) {
            Log.w(TAG, "open: callback is null.")
            return false
        }

        iqSourceCallback = callback

        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        val device = usbManager.deviceList.values.firstOrNull {
            it.vendorId == 0x1d50 && it.productId == 0x6089
        }

        if (device == null) {
            Log.i(TAG, "open: No HackRF device found.")
            return false
        }

        if (usbManager.hasPermission(device)) {
            openHackrfDevice(device, context)
        } else {

            val usbPermissionReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (ACTION_USB_PERMISSION == intent.action) {
                        synchronized(this) {
                            val device =
                                intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)

                            if (intent.getBooleanExtra(
                                    UsbManager.EXTRA_PERMISSION_GRANTED,
                                    false
                                )
                            ) {
                                if (device != null) {
                                    openHackrfDevice(device, context)
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "USB permission denied.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    context.unregisterReceiver(this)
                }
            }

            val filter = IntentFilter(ACTION_USB_PERMISSION)

            ContextCompat.registerReceiver(
                context,
                usbPermissionReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            val innerIntent = Intent(ACTION_USB_PERMISSION)
            innerIntent.setPackage(context.packageName)

            val permissionIntent = android.app.PendingIntent.getBroadcast(
                context,
                0,
                innerIntent,
                android.app.PendingIntent.FLAG_MUTABLE
            )

            usbManager.requestPermission(device, permissionIntent)
        }

        return true
    }

    private fun openHackrfDevice(device: UsbDevice, context: Context): Boolean {

        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        if (!usbManager.hasPermission(device)) {
            iqSourceCallback?.onIQSourceError(this, "No permission to open HackRF")
            return false
        }

        val connection = usbManager.openDevice(device)

        if (connection == null) {
            iqSourceCallback?.onIQSourceError(this, "USB connection failed")
            return false
        }

        val (handle, error) = HackrfDevice.open(connection.fileDescriptor)

        if (handle != null) {
            hackrfDevice = handle
            iqSourceCallback?.onIQSourceReady(this)
            return true
        }

        iqSourceCallback?.onIQSourceError(this, "Failed to open HackRF ($error)")
        Toast.makeText(context, "Failed to open HackRF ($error)", Toast.LENGTH_SHORT).show()

        return false
    }

    override fun isOpen(): Boolean {
        return hackrfDevice != null
    }

    override fun close(): Boolean {
        hackrfDevice?.let {
            return it.close()
        }
        return true
    }

    override fun getName(): String {
        return "HackRF"
    }

    override fun getMaxFrequency(): Long {
        return MAX_FREQUENCY + frequencyOffset
    }

    override fun getMinFrequency(): Long {
        return MIN_FREQUENCY + frequencyOffset
    }

    override fun getSupportedSampleRates(): IntArray {
        return OPTIMAL_SAMPLE_RATES
    }

    override fun getNextHigherOptimalSampleRate(sampleRate: Int): Int {
        return OPTIMAL_SAMPLE_RATES.firstOrNull { it > sampleRate }
            ?: OPTIMAL_SAMPLE_RATES.last()
    }

    override fun getNextLowerOptimalSampleRate(sampleRate: Int): Int {
        return OPTIMAL_SAMPLE_RATES.reversed().firstOrNull { it < sampleRate }
            ?: OPTIMAL_SAMPLE_RATES.first()
    }

    override fun getPacketSize(): Int {
        return HackrfDevice.BUFFER_SIZE
    }

    override fun getBytesPerSample(): Int {
        return 2
    }

    override fun getPacket(timeout: Int): ByteArray? {

        val packet = hackrfDevice?.getSampleBuffer()

        if (packet == null && !(hackrfDevice?.isStreaming() ?: false)) {
            iqSourceCallback?.onIQSourceError(this, "HackRF stopped streaming")
            return null
        }

        return packet
    }

    override fun returnPacket(buffer: ByteArray?) {
        if (buffer == null) return
        hackrfDevice?.returnSampleBuffer(buffer)
    }

    override fun startSampling() {

        this.setSampleRate(sampleRate) // initializes sample rate and other things
        hackrfDevice?.setFrequency(frequency)

        hackrfDevice?.setLnaGain(lnaGain)
        hackrfDevice?.setVgaGain(vgaGain)

        hackrfDevice?.setAmpEnable(ampEnabled)
        hackrfDevice?.setAntennaEnable(antennaPowerEnabled)

        hackrfDevice?.startRX()
    }

    override fun stopSampling() {
        hackrfDevice?.stopRX()
    }

    override fun fillPacketIntoSamplePacket(
        packet: ByteArray?,
        samplePacket: SamplePacket?
    ): Int {

        if (packet == null || samplePacket == null) return 0

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

        if (packet == null || samplePacket == null) return 0

        return converter.mixPacketIntoSamplePacket(
            packet,
            samplePacket,
            channelFrequency
        )
    }
}