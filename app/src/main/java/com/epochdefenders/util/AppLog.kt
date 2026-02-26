package com.epochdefenders.util

import android.util.Log
import com.epochdefenders.BuildConfig

/**
 * Production-safe logging utility.
 * All log calls are no-ops in release builds.
 * R8 will additionally strip android.util.Log.v/d/i via proguard-rules.pro.
 */
object AppLog {
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.i(tag, msg)
    }

    fun w(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.w(tag, msg)
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.e(tag, msg, throwable) else Log.e(tag, msg)
        }
    }
}
