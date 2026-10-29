package com.bernmc.booxqr

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Note: AGP's unit-test compile classpath is android.jar, which hides java.awt, so
 * BufferedImage/MatrixToImageWriter (zxing javase) cannot be referenced from Kotlin here.
 * The QR is rendered from the BitMatrix into an ARGB pixel array instead.
 */
class DecoderTest {
    private val text = "https://example.com/boox"
    private val size = 300
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private fun qrPixels(invert: Boolean = false): IntArray {
        val m = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val px = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            val dark = m.get(x, y) != invert
            px[y * size + x] = if (dark) black else white
        }
        return px
    }

    @Test
    fun decodesQr() {
        val result = Decoder.decode(RGBLuminanceSource(size, size, qrPixels()))
        assertNotNull(result)
        assertEquals(text, result!!.text)
    }

    @Test
    fun decodesInvertedQr() {
        val result = Decoder.decode(RGBLuminanceSource(size, size, qrPixels(invert = true)))
        assertNotNull(result)
        assertEquals(text, result!!.text)
    }

    @Test
    fun blankImageReturnsNull() {
        val px = IntArray(size * size) { white }
        assertNull(Decoder.decode(RGBLuminanceSource(size, size, px)))
    }
}
