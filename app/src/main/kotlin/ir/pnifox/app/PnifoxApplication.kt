package ir.pnifox.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

@HiltAndroidApp
class PnifoxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashRecorder()
        initGoSeqContext()
    }

    private fun initGoSeqContext() {
        runCatching {
            val seqCls = Class.forName("go.Seq")
            seqCls.getMethod("setContext", android.content.Context::class.java).invoke(null, this)
        }
    }

    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                PrintWriter(sw).use { pw ->
                    pw.println("pnifox crash at ${Date()} on thread '${thread.name}':")
                    throwable.printStackTrace(pw)
                }
                File(filesDir, "last_crash.txt").writeText(sw.toString().take(12_000))
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
