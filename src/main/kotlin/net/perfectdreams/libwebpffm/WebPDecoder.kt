package net.perfectdreams.libwebpffm

import java.awt.image.BufferedImage
import java.io.File
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.*

/**
 * High-level WebP decoder that produces [BufferedImage] instances.
 *
 * Handles both static and animated WebP files. For animated files,
 * only the first frame is decoded.
 */
object WebPDecoder {
    fun decode(file: File): BufferedImage = decode(file.readBytes())

    fun decode(data: ByteArray): BufferedImage {
        Arena.ofConfined().use { arena ->
            // Copy input bytes into native memory
            val nativeData = arena.allocate(data.size.toLong())
            MemorySegment.copy(data, 0, nativeData, JAVA_BYTE, 0, data.size)

            // Check if the image is animated
            val features = arena.allocate(LibWebP.BITSTREAM_FEATURES_LAYOUT)
            val status = LibWebP.WebPGetFeaturesInternal.invoke(
                nativeData,
                data.size.toLong(),
                features,
                LibWebP.WEBP_DECODER_ABI_VERSION
            ) as Int
            require(status == 0) { "WebPGetFeatures failed with status $status" }

            val hasAnimation = features.get(JAVA_INT, 12) != 0 // offset of has_animation: 3 * 4 = 12

            return if (hasAnimation) {
                decodeAnimated(arena, nativeData, data.size.toLong())
            } else {
                decodeStatic(arena, nativeData, data.size.toLong())
            }
        }
    }

    private fun decodeStatic(arena: Arena, nativeData: MemorySegment, dataSize: Long): BufferedImage {
        val widthPtr = arena.allocate(JAVA_INT)
        val heightPtr = arena.allocate(JAVA_INT)

        val pixelPtr = LibWebP.WebPDecodeRGBA.invoke(nativeData, dataSize, widthPtr, heightPtr) as MemorySegment

        require(pixelPtr != MemorySegment.NULL) { "WebPDecodeRGBA returned NULL" }

        try {
            val width = widthPtr.get(JAVA_INT, 0)
            val height = heightPtr.get(JAVA_INT, 0)
            return rgbaToBufferedImage(pixelPtr, width, height)
        } finally {
            LibWebP.WebPFree.invoke(pixelPtr)
        }
    }

    private fun decodeAnimated(arena: Arena, nativeData: MemorySegment, dataSize: Long): BufferedImage {
        // Set up WebPData struct
        val webpData = arena.allocate(LibWebP.WEBP_DATA_LAYOUT)
        webpData.set(ADDRESS, 0, nativeData)       // bytes pointer
        webpData.set(JAVA_LONG, 8, dataSize)        // size

        // Initialize decoder options
        val options = arena.allocate(LibWebP.ANIM_DECODER_OPTIONS_LAYOUT)
        val initResult = LibWebP.WebPAnimDecoderOptionsInitInternal.invoke(
            options, LibWebP.WEBP_DEMUX_ABI_VERSION
        ) as Int
        require(initResult != 0) { "WebPAnimDecoderOptionsInit failed" }

        // Set color mode to RGBA
        options.set(JAVA_INT, 0, LibWebP.MODE_RGBA)

        // Create decoder
        val decoder = LibWebP.WebPAnimDecoderNewInternal.invoke(
            webpData, options, LibWebP.WEBP_DEMUX_ABI_VERSION
        ) as MemorySegment
        require(decoder != MemorySegment.NULL) { "WebPAnimDecoderNew returned NULL" }

        try {
            // Get animation info (canvas dimensions)
            val info = arena.allocate(LibWebP.ANIM_INFO_LAYOUT)
            val infoResult = LibWebP.WebPAnimDecoderGetInfo.invoke(decoder, info) as Int
            require(infoResult != 0) { "WebPAnimDecoderGetInfo failed" }

            val canvasWidth = info.get(JAVA_INT, 0)
            val canvasHeight = info.get(JAVA_INT, 4)

            // Decode first frame
            val bufPtr = arena.allocate(ADDRESS)    // uint8_t** — output pointer
            val timestampPtr = arena.allocate(JAVA_INT)

            val nextResult = LibWebP.WebPAnimDecoderGetNext.invoke(
                decoder, bufPtr, timestampPtr
            ) as Int
            require(nextResult != 0) { "WebPAnimDecoderGetNext failed" }

            // The buf pointer points to decoder-owned memory (RGBA pixels)
            val pixelPtr = bufPtr.get(ADDRESS, 0)
            return rgbaToBufferedImage(pixelPtr, canvasWidth, canvasHeight)
        } finally {
            LibWebP.WebPAnimDecoderDelete.invoke(decoder)
        }
    }

    private fun rgbaToBufferedImage(pixelPtr: MemorySegment, width: Int, height: Int): BufferedImage {
        val totalPixels = width * height
        val totalBytes = totalPixels * 4L

        // Bulk-copy native RGBA bytes to a Java array
        val rgbaBytes = pixelPtr.reinterpret(totalBytes).toArray(JAVA_BYTE)

        // Convert RGBA byte array to ARGB int array for BufferedImage
        val argbPixels = IntArray(totalPixels)
        for (i in 0 until totalPixels) {
            val off = i * 4
            val r = rgbaBytes[off].toInt() and 0xFF
            val g = rgbaBytes[off + 1].toInt() and 0xFF
            val b = rgbaBytes[off + 2].toInt() and 0xFF
            val a = rgbaBytes[off + 3].toInt() and 0xFF
            argbPixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, width, height, argbPixels, 0, width)
        return image
    }
}
