package tv.music.player.core.crash

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {

    private const val DIR_NAME = "crash-logs"
    private const val MAX_LOGS = 10

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun logDirectory(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    fun latestLog(context: Context): File? =
        logDirectory(context).listFiles()
            ?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val dir = logDirectory(context)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash-$stamp.txt")
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        file.writeText(
            buildString {
                appendLine("幕音 crash report")
                appendLine("time: $stamp")
                appendLine("thread: ${thread.name}")
                appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("android: ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
                appendLine()
                append(stack)
            },
            Charsets.UTF_8
        )
        dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_LOGS)
            ?.forEach { it.delete() }
    }
}
