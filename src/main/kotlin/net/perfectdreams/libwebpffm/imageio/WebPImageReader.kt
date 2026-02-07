package net.perfectdreams.libwebpffm.imageio

import net.perfectdreams.libwebpffm.LibWebP
import net.perfectdreams.libwebpffm.WebPDecoder
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT
import javax.imageio.ImageReadParam
import javax.imageio.ImageReader
import javax.imageio.ImageTypeSpecifier
import javax.imageio.spi.ImageReaderSpi
import javax.imageio.stream.ImageInputStream

class WebPImageReader(spi: ImageReaderSpi) : ImageReader(spi) {
    private var cachedBytes: ByteArray? = null
    private var cachedWidth: Int = -1
    private var cachedHeight: Int = -1
    private var cachedImage: BufferedImage? = null

    override fun setInput(input: Any?, seekForwardOnly: Boolean, ignoreMetadata: Boolean) {
        super.setInput(input, seekForwardOnly, ignoreMetadata)
        cachedBytes = null
        cachedWidth = -1
        cachedHeight = -1
        cachedImage = null
    }

    private fun readBytes(): ByteArray {
        cachedBytes?.let { return it }
        val stream = input as ImageInputStream
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            baos.write(buf, 0, n)
        }
        return baos.toByteArray().also { cachedBytes = it }
    }

    private fun readFeatures() {
        if (cachedWidth >= 0) return
        val data = readBytes()
        Arena.ofConfined().use { arena ->
            val nativeData = arena.allocate(data.size.toLong())
            MemorySegment.copy(data, 0, nativeData, JAVA_BYTE, 0, data.size)
            val features = arena.allocate(LibWebP.BITSTREAM_FEATURES_LAYOUT)
            val status = LibWebP.WebPGetFeaturesInternal.invoke(
                nativeData,
                data.size.toLong(),
                features,
                LibWebP.WEBP_DECODER_ABI_VERSION
            ) as Int
            require(status == 0) { "WebPGetFeatures failed with status $status" }
            cachedWidth = features.get(JAVA_INT, 0)
            cachedHeight = features.get(JAVA_INT, 4)
        }
    }

    private fun checkIndex(imageIndex: Int) {
        if (imageIndex != 0) throw IndexOutOfBoundsException("imageIndex must be 0, got $imageIndex")
    }

    override fun getWidth(imageIndex: Int): Int {
        checkIndex(imageIndex)
        readFeatures()
        return cachedWidth
    }

    override fun getHeight(imageIndex: Int): Int {
        checkIndex(imageIndex)
        readFeatures()
        return cachedHeight
    }

    override fun getNumImages(allowSearch: Boolean): Int = 1

    override fun getImageTypes(imageIndex: Int): Iterator<ImageTypeSpecifier> {
        checkIndex(imageIndex)
        return listOf(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB)).iterator()
    }

    override fun getStreamMetadata() = null

    override fun getImageMetadata(imageIndex: Int) = null

    override fun read(imageIndex: Int, param: ImageReadParam?): BufferedImage {
        checkIndex(imageIndex)
        cachedImage?.let { return it }
        val bytes = readBytes()
        return WebPDecoder.decode(bytes).also { cachedImage = it }
    }
}
