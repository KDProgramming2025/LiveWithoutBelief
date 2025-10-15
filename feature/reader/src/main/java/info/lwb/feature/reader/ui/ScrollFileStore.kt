/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader.ui

import android.content.Context
import info.lwb.core.common.log.Logger
import java.io.File
import org.json.JSONObject

private const val SCROLL_FILE_NAME = "scroll_positions.json"
private const val TMP_SUFFIX = ".tmp"
private const val LOG_TAG = "ReaderWeb"
private const val SCROLL_PREFIX = "Scroll:"
private const val ZERO = 0
private const val MAX_KEY_LOG = 48
private const val INITIAL_CAPACITY = 64
private const val MIN_SAVE_INTERVAL_MS = 500L // time-based debounce only

/**
 * Simple atomic JSON file store for article scroll positions.
 * Entire map is rewritten on each save using a temp file + rename for durability.
 */
internal object ScrollFileStore {
    @Volatile private var loaded = false
    private val map = LinkedHashMap<String, Int>(INITIAL_CAPACITY)
    private val lastSaveTs = HashMap<String, Long>(INITIAL_CAPACITY)

    @Synchronized
    private fun ensureLoaded(ctx: Context) {
        if (loaded) {
            return
        }
        val file = File(ctx.filesDir, SCROLL_FILE_NAME)
        if (!file.exists()) {
            loaded = true
            return
        }
        try {
            val text = file.readText()
            if (text.isNotBlank()) {
                val json = JSONObject(text)
                json.keys().forEach { k -> map[k] = json.optInt(k, ZERO) }
            }
            Logger.d(LOG_TAG) { "$SCROLL_PREFIX file:load entries=${map.size}" }
        } catch (_: Throwable) {
            Logger.d(LOG_TAG) { "$SCROLL_PREFIX file:load error ignored" }
        } finally {
            loaded = true
        }
    }

    @Synchronized
    fun get(ctx: Context, key: String): Int {
        ensureLoaded(ctx)
        return map[key] ?: ZERO
    }

    @Synchronized
    fun saveIfChanged(ctx: Context, key: String, y: Int): Boolean {
        ensureLoaded(ctx)
        val now = System.currentTimeMillis()
        val lastTs = lastSaveTs[key] ?: 0L
        // Save only if sufficient time has elapsed since last save for this key.
        if ((now - lastTs) < MIN_SAVE_INTERVAL_MS) {
            return false
        }
        map[key] = y
        lastSaveTs[key] = now
        flush(ctx)
        return true
    }

    private fun flush(ctx: Context) {
        try {
            val json = JSONObject()
            map.forEach { (k, v) -> json.put(k, v) }
            val file = File(ctx.filesDir, SCROLL_FILE_NAME)
            val tmp = File(ctx.filesDir, SCROLL_FILE_NAME + TMP_SUFFIX)
            tmp.writeText(json.toString())
            if (file.exists() && !file.delete()) {
                Logger.d(LOG_TAG) { "$SCROLL_PREFIX file:delete-fail" }
            }
            if (!tmp.renameTo(file)) {
                Logger.d(LOG_TAG) { "$SCROLL_PREFIX file:rename-fail" }
            } else {
                Logger.d(LOG_TAG) { "$SCROLL_PREFIX file:save size=${map.size}" }
            }
        } catch (_: Throwable) {
            Logger.d(LOG_TAG) { "$SCROLL_PREFIX file:save error" }
        }
    }
}

internal fun shortenKeyForLog(key: String): String = if (key.length <= MAX_KEY_LOG) {
    key
} else {
    "…" + key.takeLast(MAX_KEY_LOG - 1)
}
