/*
 * Kiwix Android
 * Copyright (c) 2026 Kiwix <android.kiwix.org>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.kiwix.kiwixmobile.core.utils

import android.content.Context
import android.os.Build
import android.webkit.WebView
import org.kiwix.kiwixmobile.core.utils.files.Log

/**
 * Detects whether the device has a usable Android System WebView.
 *
 * Some devices ship without a WebView provider (or with a disabled one), in
 * which case instantiating [WebView] throws at runtime and would crash the app.
 * Callers can use this check to degrade gracefully, e.g. by offering to read
 * the content in an external browser instead. See [isWebViewAvailable].
 */
object WebViewAvailability {
  @Volatile
  private var cachedAvailability: Boolean? = null

  /**
   * Returns true if a WebView can be created on this device. The result is
   * cached for the lifetime of the process. Must be called on the main thread
   * (on devices older than Android O it creates a throwaway [WebView]).
   */
  fun isWebViewAvailable(context: Context): Boolean =
    cachedAvailability ?: checkWebView(context).also { cachedAvailability = it }

  /**
   * Records that creating a WebView failed at runtime, so subsequent
   * [isWebViewAvailable] calls report the WebView as unavailable even when the
   * WebView package check succeeded.
   */
  fun markWebViewUnavailable() {
    cachedAvailability = false
  }

  private fun checkWebView(context: Context): Boolean =
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WebView.getCurrentWebViewPackage() != null
      } else {
        // On older devices the only reliable check is to create a WebView;
        // it throws when no WebView provider is installed or enabled.
        WebView(context).destroy()
        true
      }
    }.onFailure {
      Log.e("WebViewAvailability", "This device has no usable WebView. $it")
    }.getOrDefault(false)
}
