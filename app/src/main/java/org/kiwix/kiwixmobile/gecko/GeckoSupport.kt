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

package org.kiwix.kiwixmobile.gecko

import android.content.Context
import android.view.View
import org.kiwix.kiwixmobile.BuildConfig
import org.kiwix.kiwixmobile.core.utils.files.Log

private const val EMBEDDED_GECKO_READER = "org.kiwix.kiwixmobile.gecko.EmbeddedGeckoReader"

/**
 * The embedded Gecko based reader as seen by code that must also compile in
 * builds without the Gecko engine. Implemented by EmbeddedGeckoReader in the
 * gecko source set (see app/build.gradle.kts).
 */
interface EmbeddedGeckoReaderHolder {
  /** The view rendering the content; shown inside the reader screen. */
  val view: View

  /** Whether the Gecko session can navigate back in its history. */
  val canGoBack: Boolean

  /**
   * The URL currently shown by the Gecko session (the localhost server URL), or
   * null before the first page has loaded. Used to keep the reader's URL/
   * bookmark state in sync without a WebView.
   */
  val currentUrl: String?

  /** The title of the page currently shown by the Gecko session, if known. */
  val currentTitle: String?

  /**
   * Notified about navigation events (page loads and attempts to leave the
   * offline content). Set by the reader after the holder is created.
   */
  var callback: EmbeddedGeckoReaderCallback?

  /** Loads [url]; when it fails to load, [fallbackUrl] is loaded instead. */
  fun loadUrl(url: String, fallbackUrl: String?)

  fun goBack()

  fun close()
}

/**
 * Callbacks from the embedded Gecko reader back to the reader fragment, so the
 * Gecko build behaves like the WebView build for history and external links.
 */
interface EmbeddedGeckoReaderCallback {
  /** A page finished loading in the Gecko reader (offline content only). */
  fun onGeckoPageLoaded(url: String?, title: String?)

  /**
   * The user followed a link that leaves the offline book (e.g. a live internet
   * link). Gecko does not load it; the app should open it in an external
   * browser instead.
   */
  fun onExternalLinkRequested(url: String)
}

/**
 * Entry point to the optional GeckoView based reader.
 *
 * The Gecko engine is only bundled when the app is built with the `withGecko`
 * Gradle property (see app/build.gradle.kts); in regular builds
 * [IS_GECKO_INCLUDED] is false and [createEmbeddedReader] returns null.
 */
object GeckoSupport {
  // Deliberately not a `const val`: a const is inlined into every call site at
  // compile time, so switching between a `-PwithGecko` build and a regular one
  // can leave stale classes with the old value baked in. The app then takes the
  // Gecko path without the engine being present and the reader renders nothing.
  // Reading it at runtime keeps every call site honest.
  @JvmField
  val IS_GECKO_INCLUDED: Boolean = BuildConfig.WITH_GECKO

  /**
   * Creates an embedded Gecko reader, or returns null in builds without the
   * Gecko engine. The implementation class is referenced by name so this
   * class compiles in both build modes.
   */
  fun createEmbeddedReader(context: Context): EmbeddedGeckoReaderHolder? {
    if (!IS_GECKO_INCLUDED) return null
    return runCatching {
      Class.forName(EMBEDDED_GECKO_READER)
        .getConstructor(Context::class.java)
        .newInstance(context) as EmbeddedGeckoReaderHolder
    }.onFailure {
      Log.e("GeckoSupport", "Could not create the embedded Gecko reader. $it")
    }.getOrNull()
  }
}
