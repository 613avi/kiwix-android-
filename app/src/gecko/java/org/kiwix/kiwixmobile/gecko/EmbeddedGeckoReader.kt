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

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError

/**
 * Renders the currently opened ZIM file with the bundled Gecko (Firefox)
 * engine, embedded inside the reader screen. The content is served by the
 * in-app localhost kiwix server, so this works on devices that have neither a
 * WebView nor a browser.
 *
 * This class only exists in builds created with the `withGecko` Gradle
 * property; it is instantiated by name via [GeckoSupport.createEmbeddedReader].
 */
@Suppress("Unused") // Instantiated reflectively by GeckoSupport.
class EmbeddedGeckoReader(context: Context) : EmbeddedGeckoReaderHolder {
  private val geckoView = GeckoView(context)
  private val session = GeckoSession()
  private var fallbackUrl: String? = null
  private var triedFallback = false

  override var canGoBack: Boolean = false
    private set

  init {
    session.navigationDelegate = object : GeckoSession.NavigationDelegate {
      override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        this@EmbeddedGeckoReader.canGoBack = canGoBack
      }

      override fun onLoadError(
        session: GeckoSession,
        uri: String?,
        error: WebRequestError
      ): GeckoResult<String>? {
        // E.g. when the direct content URL of the book could not be resolved,
        // fall back to the server root, which lists the served book.
        fallbackUrl?.takeIf { !triedFallback && it != uri }?.let {
          triedFallback = true
          session.loadUri(it)
        }
        return null
      }
    }
    session.open(GeckoRuntimeProvider.getRuntime(context.applicationContext))
    geckoView.setSession(session)
  }

  override val view: View get() = geckoView

  override fun loadUrl(url: String, fallbackUrl: String?) {
    this.fallbackUrl = fallbackUrl
    triedFallback = false
    session.loadUri(url)
  }

  override fun goBack() {
    session.goBack()
  }

  override fun close() {
    geckoView.releaseSession()
    session.close()
  }
}

/**
 * Holds the process wide [GeckoRuntime]. Gecko only allows a single runtime
 * per process, so it is created lazily and reused for every reader instance.
 */
@SuppressLint("StaticFieldLeak") // Holds the application context only.
object GeckoRuntimeProvider {
  private var runtime: GeckoRuntime? = null

  @Synchronized
  fun getRuntime(applicationContext: Context): GeckoRuntime =
    runtime ?: GeckoRuntime.create(applicationContext).also { runtime = it }
}
