package com.bernmc.booxqr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer

/** Pure ZXing decoding, no Android dependencies so it can be unit-tested on the JVM. */
object Decoder {
    private val formats = listOf(
        BarcodeFormat.QR_CODE,
        BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.AZTEC,
        BarcodeFormat.PDF_417,
        BarcodeFormat.EAN_13,
        BarcodeFormat.EAN_8,
        BarcodeFormat.UPC_A,
        BarcodeFormat.UPC_E,
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_39,
    )

    private fun hints(tryHarder: Boolean): Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to formats,
        DecodeHintType.TRY_HARDER to tryHarder,
    )

    private val easyHints = hints(false)
    private val hardHints = hints(true)

    private val reader = MultiFormatReader()

    @Synchronized
    fun decode(source: LuminanceSource, tryHarder: Boolean = false): Result? {
        val h = if (tryHarder) hardHints else easyHints
        return try {
            decodeOnce(source, h)
        } catch (e: NotFoundException) {
            try {
                decodeOnce(source.invert(), h)
            } catch (e2: ReaderException) {
                null
            }
        } catch (e: ReaderException) {
            null
        }
    }

    private fun decodeOnce(source: LuminanceSource, hints: Map<DecodeHintType, Any>): Result {
        try {
            return reader.decode(BinaryBitmap(HybridBinarizer(source)), hints)
        } finally {
            reader.reset()
        }
    }
}
