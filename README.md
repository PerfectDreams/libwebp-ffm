# libwebp-ffm

> [!CAUTION]
> This is a "vibe coded" library (Claude Code / Opus 4.6) that I made because I needed to load webp images in [Loritta](https://github.com/LorittaBot/Loritta) because Discord doesn't transcode webp images to gif when requested via the media proxy.
> 
> It does work, but if you want something battle tested, look somewhere else!

libwebp-ffm is a library that allows you to read webp images via libwebp.

```kotlin
fun main() {
    val inputFile = File("a_451570c96039f873ed5bce9b7d967a8b.webp") // You can also pass a ByteArray
    val image = WebPDecoder.decode(inputFile) // This is a BufferedImage!
}
```

You can also read webp images via `ImageIO.read`.

```kotlin
fun main() {
    val formats = ImageIO.getReaderFormatNames()
    println("Registered ImageIO reader formats: ${formats.toSortedSet()}")
    require("webp" in formats) { "WebP format not registered!" }

    val inputFile = File("a_451570c96039f873ed5bce9b7d967a8b.webp")
    val image = ImageIO.read(inputFile)
}
```

For animated frames, only the first frame is decoded.