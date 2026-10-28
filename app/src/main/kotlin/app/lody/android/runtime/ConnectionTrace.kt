package app.lody.android.runtime

import android.os.SystemClock
import android.util.Log
import app.lody.android.BuildConfig

/** Debug-only timings; fixed stage labels, no URLs, tokens, account IDs or payloads. */
internal object ConnectionTrace {
    fun mark(stage: String, milliseconds: Long = 0) {
        if (BuildConfig.DEBUG && stage in setOf("grant_ok", "grant_failed", "bootstrap", "bootstrap_ok", "network_error", "timeout", "unauthorized", "forbidden", "network_validated", "synced")) {
            Log.d("LodyConnection", "${SystemClock.elapsedRealtime()} $stage ${milliseconds.coerceIn(0, 3_600_000)}ms")
        }
    }
}
