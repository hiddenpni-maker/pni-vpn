package com.therealaleph.mhrv

/**
 * Exact JNI binding class for prebuilt `libmhrv_rs.so` (package/class name is baked
 * into Rust `#[no_mangle] Java_com_therealaleph_mhrv_Native_*` symbols).
 */
object Native {

    @Volatile
    private var loaded: Boolean = false

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("mhrv_rs")
            loaded = true
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    @JvmStatic
    external fun setDataDir(path: String)

    @JvmStatic
    external fun startProxy(configJson: String): Long

    @JvmStatic
    external fun stopProxy(handle: Long): Boolean

    @JvmStatic
    external fun exportCa(destPath: String): Boolean

    @JvmStatic
    external fun version(): String

    @JvmStatic
    external fun drainLogs(): String

    @JvmStatic
    external fun testSni(googleIp: String, sni: String): String

    @JvmStatic
    external fun statsJson(handle: Long): String
}
