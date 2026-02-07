package net.perfectdreams.libwebpffm.imageio

import javax.imageio.spi.ImageReaderSpi
import javax.imageio.stream.ImageInputStream

class WebPImageReaderSpi : ImageReaderSpi(
    "PerfectDreams",
    "1.0",
    arrayOf("webp", "WEBP", "WebP"),
    arrayOf("webp"),
    arrayOf("image/webp"),
    "net.perfectdreams.javawebp.imageio.WebPImageReader",
    arrayOf(ImageInputStream::class.java),
    null,
    false,
    null,
    null,
    null,
    null,
    false,
    null,
    null,
    null,
    null,
) {
    override fun canDecodeInput(source: Any): Boolean {
        if (source !is ImageInputStream) return false
        source.mark()
        try {
            val header = ByteArray(12)
            val bytesRead = source.read(header)
            if (bytesRead < 12) return false
            // RIFF at bytes 0-3 and WEBP at bytes 8-11
            return header[0] == 'R'.code.toByte() &&
                    header[1] == 'I'.code.toByte() &&
                    header[2] == 'F'.code.toByte() &&
                    header[3] == 'F'.code.toByte() &&
                    header[8] == 'W'.code.toByte() &&
                    header[9] == 'E'.code.toByte() &&
                    header[10] == 'B'.code.toByte() &&
                    header[11] == 'P'.code.toByte()
        } finally {
            source.reset()
        }
    }

    override fun createReaderInstance(extension: Any?): WebPImageReader = WebPImageReader(this)

    override fun getDescription(locale: java.util.Locale?): String = "WebP Image Reader (libwebp via Panama FFM)"
}
