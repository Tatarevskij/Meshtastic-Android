package com.geeksville.mta.util

import android.os.RemoteException
import android.util.Log
import android.view.View
import com.geeksville.mta.android.Logging
import com.google.android.material.snackbar.Snackbar


object Exceptions : Logging {
    /// Set in Application.onCreate
    var reporter: ((Throwable, String?, String?) -> Unit)? = null

    /**
     * Report an exception to our analytics provider (if installed - otherwise just log)
     *
     * After reporting return
     */
    fun report(exception: Throwable, tag: String? = null, message: String? = null) {
        errormsg(
            "Exceptions.report: $tag $message",
            exception
        ) // print the message to the log _before_ telling the crash reporter
        reporter?.let { r ->
            r(exception, tag, message)
        }
    }
}

/**
 * This wraps (and discards) exceptions, but first it reports them to our bug tracking system and prints
 * a message to the log.
 */
fun exceptionReporter(inner: () -> Unit) {
    try {
        inner()
    } catch (ex: Throwable) {
        // DO NOT THROW users expect we have fully handled/discarded the exception
        Exceptions.report(ex, "exceptionReporter", "Uncaught Exception")
    }
}

/**
 * If an exception occurs, show the message in a snackbar and continue
 */
fun exceptionToSnackbar(view: View, inner: () -> Unit) {
    try {
        inner()
    } catch (ex: Throwable) {
        Snackbar.make(view, ex.message ?: "An exception occurred", Snackbar.LENGTH_LONG).show()
    }
}


/**
 * This wraps (and discards) exceptions, but it does output a log message
 */
fun ignoreException(silent: Boolean = false, inner: () -> Unit) {
    try {
        inner()
    } catch (ex: Throwable) {
        // DO NOT THROW users expect we have fully handled/discarded the exception
        if(!silent)
            Exceptions.errormsg("ignoring exception", ex)
    }
}

/// Convert any exceptions in this service call into a RemoteException that the client can
/// then handle
fun <T> toRemoteExceptions(inner: () -> T): T = try {
    inner()
} catch (ex: Throwable) {
    Log.e("toRemoteExceptions", "Uncaught exception, returning to remote client", ex)
    when(ex) { // don't double wrap remote exceptions
        is RemoteException -> throw ex
        else -> throw RemoteException(ex.message)
    }
}

