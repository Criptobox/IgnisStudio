package com.ignis.studio

/** Enlace con el motor nativo (libignis_llm.so = llama.cpp + puente JNI). */
object LlamaNative {
    /** false si la biblioteca nativa no está (por ejemplo, en un procesador sin 64 bits). */
    val available: Boolean = try {
        System.loadLibrary("ignis_llm"); true
    } catch (e: Throwable) {
        false
    }

    /** Recibe cada trozo de texto (UTF-8). Devolver false detiene la generación. */
    interface TokenCallback { fun onToken(bytes: ByteArray): Boolean }

    external fun init(nativeLibDir: String)
    /** "" si cargó bien; si no, el motivo. */
    external fun load(path: String, nCtx: Int, nThreads: Int): String
    external fun unload()
    external fun isLoaded(): Boolean
    external fun stop()
    external fun describe(): String
    /** "" si terminó bien; si no, el motivo. */
    external fun generate(roles: Array<ByteArray>, contents: Array<ByteArray>, maxTokens: Int, temp: Float, cb: TokenCallback): String
}
