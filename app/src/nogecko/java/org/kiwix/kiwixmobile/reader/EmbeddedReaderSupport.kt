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

/**
 * The regular, WebView based build: there is no embedded engine.
 *
 * This is the `src/nogecko` half of a pair; `src/gecko` holds the counterpart
 * that creates the bundled Gecko engine, and app/build.gradle.kts adds exactly
 * one of the two source sets. That is what keeps a WebView build free of any
 * Gecko code: the engine, its dependency and the factory that would reach for
 * it are simply not compiled in, rather than being disabled by a flag at
 * runtime.
 */
object EmbeddedReaderSupport {
  /** No embedded engine in this build, so the reader always uses the WebView. */
  val IS_AVAILABLE: Boolean = false

  fun createEmbeddedReader(context: Context): EmbeddedReader? = null
}
