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
import android.content.Intent
import org.kiwix.kiwixmobile.BuildConfig
import org.kiwix.kiwixmobile.core.utils.files.Log

const val GECKO_READER_URL_EXTRA = "geckoReaderUrl"
private const val GECKO_READER_ACTIVITY = "org.kiwix.kiwixmobile.gecko.GeckoReaderActivity"

/**
 * Entry point to the optional GeckoView based reader.
 *
 * The Gecko engine is only bundled when the app is built with the `withGecko`
 * Gradle property (see app/build.gradle.kts); in regular builds
 * [isGeckoIncluded] is false and [openInGeckoReader] does nothing. The reader
 * activity is referenced by name so this class compiles in both build modes.
 */
object GeckoSupport {
  const val isGeckoIncluded: Boolean = BuildConfig.WITH_GECKO

  /**
   * Opens the given URL (typically the localhost kiwix server serving the
   * current book) in the bundled Gecko based reader.
   *
   * @return true if the reader was started, false if Gecko is not included in
   *         this build or the activity could not be started.
   */
  fun openInGeckoReader(context: Context, url: String): Boolean {
    if (!isGeckoIncluded) return false
    return runCatching {
      context.startActivity(
        Intent()
          .setClassName(context, GECKO_READER_ACTIVITY)
          .putExtra(GECKO_READER_URL_EXTRA, url)
      )
    }.onFailure {
      Log.e("GeckoSupport", "Could not start the Gecko reader. $it")
    }.isSuccess
  }
}
