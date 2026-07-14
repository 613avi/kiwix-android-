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

package org.kiwix.kiwixmobile.reader

import android.content.Context
import org.kiwix.kiwixmobile.gecko.EmbeddedGeckoReader

/**
 * The Gecko build (`-PwithGecko`): render books with the bundled Gecko engine.
 *
 * The counterpart in `src/nogecko` reports no engine and is the one compiled
 * into a regular build; app/build.gradle.kts adds exactly one of the two source
 * sets. Because the reference to [EmbeddedGeckoReader] lives here and not in
 * `src/main`, a WebView build contains no Gecko code at all.
 */
object EmbeddedReaderSupport {
  val IS_AVAILABLE: Boolean = true

  fun createEmbeddedReader(context: Context): EmbeddedReader? = EmbeddedGeckoReader(context)
}
