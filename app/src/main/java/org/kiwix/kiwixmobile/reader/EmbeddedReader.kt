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

import android.view.View

/**
 * A renderer that shows the offline book without the Android WebView, served by
 * the in-app localhost kiwix server.
 *
 * The reader talks to it only through this interface, so it knows nothing about
 * the engine behind it. The sole implementation today is the bundled Gecko
 * (Firefox) engine, which exists only in builds made with the `withGecko`
 * Gradle property; see [EmbeddedReaderSupport].
 */
interface EmbeddedReader {
  /** The view rendering the content; shown inside the reader screen. */
  val view: View

  /** Whether the renderer can navigate back in its history. */
  val canGoBack: Boolean

  /**
   * The URL currently shown (the localhost server URL), or null before the first
   * page has loaded. Used to keep the reader's URL/bookmark state in sync
   * without a WebView.
   */
  val currentUrl: String?

  /** The title of the page currently shown, if known. */
  val currentTitle: String?

  /**
   * Notified about navigation events (page loads and attempts to leave the
   * offline content). Set by the reader after the renderer is created.
   */
  var callback: EmbeddedReaderCallback?

  /** Loads [url]; when it fails to load, [fallbackUrl] is loaded instead. */
  fun loadUrl(url: String, fallbackUrl: String?)

  fun goBack()

  fun close()
}

/**
 * Callbacks from the embedded renderer back to the reader fragment, so a build
 * with an embedded engine behaves like the WebView build for history and
 * external links.
 */
interface EmbeddedReaderCallback {
  /** A page finished loading (offline content only). */
  fun onPageLoaded(url: String?, title: String?)

  /**
   * The user followed a link that leaves the offline book (e.g. a live internet
   * link). The renderer does not load it; the app opens it in an external
   * browser instead.
   */
  fun onExternalLinkRequested(url: String)
}
