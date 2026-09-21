package com.example.snfetchplayer.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    interface LogListener {
        fun onLogAdded(logLine: String)
    }

    private var listener: LogListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val logHistory = mutableListOf<String>()

    fun setLogListener(logListener: LogListener?) {
        this.listener = logListener
        if (logListener != null) {
            val historyCopy: List<String>
            synchronized(logHistory) {
                historyCopy = ArrayList(logHistory)
            }
            historyCopy.forEach { logLine ->
                logListener.onLogAdded(logLine)
            }
        }
    }

    fun getLogHistory(): String {
        synchronized(logHistory) {
            return logHistory.joinToString("\n")
        }
    }

    fun d(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val formattedLine = "[$timestamp] [$tag] $message"
        Log.d(tag, message)
        appendLog(formattedLine)
    }

    fun w(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val formattedLine = "[$timestamp] [WARN] [$tag] $message"
        Log.w(tag, message)
        appendLog(formattedLine)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val errMsg = if (throwable != null) "$message (${throwable.javaClass.simpleName}: ${throwable.message})" else message
        val formattedLine = "[$timestamp] [ERROR] [$tag] $errMsg"
        Log.e(tag, message, throwable)
        appendLog(formattedLine)
    }

    private fun appendLog(logLine: String) {
        synchronized(logHistory) {
            logHistory.add(logLine)
            if (logHistory.size > 200) {
                logHistory.removeAt(0)
            }
        }
        mainHandler.post {
            listener?.onLogAdded(logLine)
        }
    }
}
