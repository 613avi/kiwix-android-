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

  /** Loads [url]; when it fails to load, [fallbackUrl] is loaded instead. */
  fun loadUrl(url: String, fallbackUrl: String?)

  fun goBack()

  fun close()
}

/**
 * Entry point to the optional GeckoView based reader.
 *
 * The Gecko engine is only bundled when the app is built with the `withGecko`
 * Gradle property (see app/build.gradle.kts); in regular builds
 * [IS_GECKO_INCLUDED] is false and [createEmbeddedReader] returns null.
 */
object GeckoSupport {
  const val IS_GECKO_INCLUDED: Boolean = BuildConfig.WITH_GECKO

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
