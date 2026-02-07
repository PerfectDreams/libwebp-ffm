package net.perfectdreams.libwebpffm

import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*
import java.lang.invoke.MethodHandle

/**
 * Panama FFM bindings for libwebp and libwebpdemux.
 *
 * Provides low-level native function handles for decoding WebP images
 * (both static and animated) without any JNI or native C code.
 */
object LibWebP {
    // ABI version constants — must match the installed library versions
    const val WEBP_DECODER_ABI_VERSION = 0x0210
    const val WEBP_DEMUX_ABI_VERSION = 0x0107

    // WEBP_CSP_MODE enum value
    const val MODE_RGBA = 1

    // Keep the arena alive for the lifetime of the library lookups
    private val arena: Arena = Arena.ofAuto()

    private val webpLookup = SymbolLookup.libraryLookup("libwebp.so", arena)
    private val demuxLookup = SymbolLookup.libraryLookup("libwebpdemux.so", arena)

    private val linker = Linker.nativeLinker()

    // ── Struct Layouts ──────────────────────────────────────────────────

    /**
     * WebPBitstreamFeatures (decode.h):
     *   int width, height, has_alpha, has_animation, format;
     *   uint32_t pad[5];
     * Total: 5 ints + 5 uint32 = 40 bytes
     */
    val BITSTREAM_FEATURES_LAYOUT: StructLayout = MemoryLayout.structLayout(
        JAVA_INT.withName("width"),
        JAVA_INT.withName("height"),
        JAVA_INT.withName("has_alpha"),
        JAVA_INT.withName("has_animation"),
        JAVA_INT.withName("format"),
        JAVA_INT.withName("pad0"),
        JAVA_INT.withName("pad1"),
        JAVA_INT.withName("pad2"),
        JAVA_INT.withName("pad3"),
        JAVA_INT.withName("pad4"),
    )

    /**
     * WebPData (mux_types.h):
     *   const uint8_t* bytes;
     *   size_t size;
     * Total: pointer + size_t = 16 bytes on 64-bit
     */
    val WEBP_DATA_LAYOUT: StructLayout = MemoryLayout.structLayout(
        ADDRESS.withName("bytes"),
        JAVA_LONG.withName("size"), // size_t on 64-bit
    )

    /**
     * WebPAnimDecoderOptions (demux.h):
     *   WEBP_CSP_MODE color_mode; // enum = int
     *   int use_threads;
     *   uint32_t padding[7];
     * Total: 2 ints + 7 uint32 = 36 bytes
     */
    val ANIM_DECODER_OPTIONS_LAYOUT: StructLayout = MemoryLayout.structLayout(
        JAVA_INT.withName("color_mode"),
        JAVA_INT.withName("use_threads"),
        JAVA_INT.withName("pad0"),
        JAVA_INT.withName("pad1"),
        JAVA_INT.withName("pad2"),
        JAVA_INT.withName("pad3"),
        JAVA_INT.withName("pad4"),
        JAVA_INT.withName("pad5"),
        JAVA_INT.withName("pad6"),
    )

    /**
     * WebPAnimInfo (demux.h):
     *   uint32_t canvas_width, canvas_height, loop_count, bgcolor, frame_count;
     *   uint32_t pad[4];
     * Total: 5 uint32 + 4 uint32 = 36 bytes
     */
    val ANIM_INFO_LAYOUT: StructLayout = MemoryLayout.structLayout(
        JAVA_INT.withName("canvas_width"),
        JAVA_INT.withName("canvas_height"),
        JAVA_INT.withName("loop_count"),
        JAVA_INT.withName("bgcolor"),
        JAVA_INT.withName("frame_count"),
        JAVA_INT.withName("pad0"),
        JAVA_INT.withName("pad1"),
        JAVA_INT.withName("pad2"),
        JAVA_INT.withName("pad3"),
    )

    // ── Function Handles ────────────────────────────────────────────────

    /**
     * VP8StatusCode WebPGetFeaturesInternal(const uint8_t* data, size_t data_size,
     *                                       WebPBitstreamFeatures* features, int version);
     * Returns 0 (VP8_STATUS_OK) on success.
     */
    val WebPGetFeaturesInternal: MethodHandle = linker.downcallHandle(
        webpLookup.find("WebPGetFeaturesInternal").orElseThrow(),
        FunctionDescriptor.of(
            JAVA_INT,    // return: VP8StatusCode (int)
            ADDRESS,     // data: const uint8_t*
            JAVA_LONG,   // data_size: size_t
            ADDRESS,     // features: WebPBitstreamFeatures*
            JAVA_INT,    // version: int
        )
    )

    /**
     * uint8_t* WebPDecodeRGBA(const uint8_t* data, size_t data_size,
     *                          int* width, int* height);
     * Returns pointer to RGBA pixel data, or NULL on error.
     */
    val WebPDecodeRGBA: MethodHandle = linker.downcallHandle(
        webpLookup.find("WebPDecodeRGBA").orElseThrow(),
        FunctionDescriptor.of(
            ADDRESS,     // return: uint8_t*
            ADDRESS,     // data: const uint8_t*
            JAVA_LONG,   // data_size: size_t
            ADDRESS,     // width: int*
            ADDRESS,     // height: int*
        )
    )

    /**
     * void WebPFree(void* ptr);
     */
    val WebPFree: MethodHandle = linker.downcallHandle(
        webpLookup.find("WebPFree").orElseThrow(),
        FunctionDescriptor.ofVoid(
            ADDRESS,     // ptr: void*
        )
    )

    /**
     * int WebPAnimDecoderOptionsInitInternal(WebPAnimDecoderOptions* options, int version);
     */
    val WebPAnimDecoderOptionsInitInternal: MethodHandle = linker.downcallHandle(
        demuxLookup.find("WebPAnimDecoderOptionsInitInternal").orElseThrow(),
        FunctionDescriptor.of(
            JAVA_INT,    // return: int (success)
            ADDRESS,     // options: WebPAnimDecoderOptions*
            JAVA_INT,    // version: int
        )
    )

    /**
     * WebPAnimDecoder* WebPAnimDecoderNewInternal(const WebPData* data,
     *                                             const WebPAnimDecoderOptions* options, int version);
     */
    val WebPAnimDecoderNewInternal: MethodHandle = linker.downcallHandle(
        demuxLookup.find("WebPAnimDecoderNewInternal").orElseThrow(),
        FunctionDescriptor.of(
            ADDRESS,     // return: WebPAnimDecoder*
            ADDRESS,     // data: const WebPData*
            ADDRESS,     // options: const WebPAnimDecoderOptions*
            JAVA_INT,    // version: int
        )
    )

    /**
     * int WebPAnimDecoderGetInfo(const WebPAnimDecoder* dec, WebPAnimInfo* info);
     */
    val WebPAnimDecoderGetInfo: MethodHandle = linker.downcallHandle(
        demuxLookup.find("WebPAnimDecoderGetInfo").orElseThrow(),
        FunctionDescriptor.of(
            JAVA_INT,    // return: int (success)
            ADDRESS,     // dec: const WebPAnimDecoder*
            ADDRESS,     // info: WebPAnimInfo*
        )
    )

    /**
     * int WebPAnimDecoderGetNext(WebPAnimDecoder* dec, uint8_t** buf, int* timestamp);
     */
    val WebPAnimDecoderGetNext: MethodHandle = linker.downcallHandle(
        demuxLookup.find("WebPAnimDecoderGetNext").orElseThrow(),
        FunctionDescriptor.of(
            JAVA_INT,    // return: int (success)
            ADDRESS,     // dec: WebPAnimDecoder*
            ADDRESS,     // buf: uint8_t**
            ADDRESS,     // timestamp: int*
        )
    )

    /**
     * void WebPAnimDecoderDelete(WebPAnimDecoder* dec);
     */
    val WebPAnimDecoderDelete: MethodHandle = linker.downcallHandle(
        demuxLookup.find("WebPAnimDecoderDelete").orElseThrow(),
        FunctionDescriptor.ofVoid(
            ADDRESS,     // dec: WebPAnimDecoder*
        )
    )
}
