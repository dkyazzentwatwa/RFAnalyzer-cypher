package com.mantz_it.rfanalyzer.source

/**
 * RF Analyzer - complex float 32-bit IQ Converter
 *
 * Module:      Float32IQConverter.kt
 * Description: Converts interleaved complex float (CF32) IQ samples directly
 *              into SamplePackets, optionally mixing them to baseband.
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
class Float32IQConverter : IQConverter() {

    // Sin/Cos lookup tables
    private var cosLut: FloatArray? = null
    private var sinLut: FloatArray? = null

    override fun generateLookupTable() {
        // Not needed for float data
    }

    override fun generateMixerLookupTable(mixFrequencyIn: Int) {
        var mixFrequency = mixFrequencyIn
        // If mix frequency is too low, just add the sample rate (sampled spectrum is periodic):
        if (mixFrequency == 0 || (sampleRate / kotlin.math.abs(mixFrequency) > MAX_COSINE_LENGTH)) {
            mixFrequency += sampleRate
        }

        // Only regenerate if needed
        if (cosLut == null || mixFrequency != cosineFrequency) {
            cosineFrequency = mixFrequency
            val bestLength = calcOptimalCosineLength()

            val cosArr = FloatArray(bestLength)
            val sinArr = FloatArray(bestLength)
            val twoPiFOverFs = (2.0 * Math.PI * cosineFrequency) / sampleRate.toDouble()

            var t = 0
            while (t < bestLength) {
                val angle = twoPiFOverFs * t
                cosArr[t] = kotlin.math.cos(angle).toFloat()
                sinArr[t] = kotlin.math.sin(angle).toFloat()
                t++
            }

            cosLut = cosArr
            sinLut = sinArr
            cosineIndex = 0
        }
    }

    override fun fillPacketIntoSamplePacket(packet: ByteArray, samplePacket: SamplePacket): Int {
        val capacity = samplePacket.capacity()
        val startIndex = samplePacket.size()
        if (startIndex >= capacity) return 0

        val re = samplePacket.re()
        val im = samplePacket.im()

        // Interpret the ByteArray as FloatArray without copying
        val floatBuf = java.nio.ByteBuffer.wrap(packet)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()

        val samplesAvailable = floatBuf.remaining() / 2
        val samplesToCopy = minOf(samplesAvailable, capacity - startIndex)

        var i = 0
        var outIdx = startIndex
        while (i < samplesToCopy) {
            re[outIdx] = floatBuf.get() // I
            im[outIdx] = floatBuf.get() // Q
            i++
            outIdx++
        }

        samplePacket.setSize(startIndex + samplesToCopy)
        samplePacket.setSampleRate(sampleRate)
        samplePacket.setFrequency(frequency)
        return samplesToCopy
    }

    override fun fillPacketIntoInterleavedBuffer(packet: ByteArray, interleavedBuffer: FloatArray): Boolean {
        if (interleavedBuffer.size < packet.size / 4) return false
        // Interpret the ByteArray as FloatArray without copying
        java.nio.ByteBuffer.wrap(packet)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
            .get(interleavedBuffer) // then copy into output buffer
        return true
    }

    override fun mixPacketIntoSamplePacket(packet: ByteArray, samplePacket: SamplePacket, channelFrequency: Long): Int {
        val mixFrequency = (frequency - channelFrequency).toInt()
        generateMixerLookupTable(mixFrequency)

        val capacity = samplePacket.capacity()
        val startIndex = samplePacket.size()
        if (startIndex >= capacity) return 0

        val re = samplePacket.re()
        val im = samplePacket.im()
        val cosArr = cosLut!!
        val sinArr = sinLut!!
        val cLen = cosArr.size
        var cIdx = cosineIndex

        val floatBuf = java.nio.ByteBuffer.wrap(packet)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()

        val samplesAvailable = floatBuf.remaining() / 2
        val samplesToCopy = minOf(samplesAvailable, capacity - startIndex)

        var i = 0
        var outIdx = startIndex

        // Mix: (I + jQ) * e^{-jωt} = (I*cos - Q*sin) + j(Q*cos + I*sin)
        while (i < samplesToCopy) {
            val I = floatBuf.get()
            val Q = floatBuf.get()

            val c = cosArr[cIdx]
            val s = sinArr[cIdx]

            re[outIdx] = I * c - Q * s
            im[outIdx] = Q * c + I * s

            cIdx++
            if (cIdx == cLen) cIdx = 0

            i++
            outIdx++
        }

        cosineIndex = cIdx
        samplePacket.setSize(startIndex + samplesToCopy)
        samplePacket.setSampleRate(sampleRate)
        samplePacket.setFrequency(channelFrequency)
        return samplesToCopy
    }
}