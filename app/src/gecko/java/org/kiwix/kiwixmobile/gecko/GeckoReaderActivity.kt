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
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * A minimal reader that renders the currently opened ZIM file with the bundled
 * Gecko (Firefox) engine. The content is served by the in-app localhost kiwix
 * server, so this works on devices that have neither a WebView nor a browser.
 *
 * This activity only exists in builds created with the `withGecko` Gradle
 * property; it is launched by name via [GeckoSupport.openInGeckoReader].
 */
class GeckoReaderActivity : AppCompatActivity() {
  private var geckoView: GeckoView? = null
  private var session: GeckoSession? = null
  private var canGoBack = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val url = intent.getStringExtra(GECKO_READER_URL_EXTRA)
    if (url == null) {
      finish()
      return
    }
    val view = GeckoView(this).also { geckoView = it }
    setContentView(view)
    val geckoSession = GeckoSession().also { session = it }
    geckoSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
      override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        this@GeckoReaderActivity.canGoBack = canGoBack
      }
    }
    geckoSession.open(GeckoRuntimeProvider.getRuntime(applicationContext))
    view.setSession(geckoSession)
    geckoSession.loadUri(url)
    onBackPressedDispatcher.addCallback(this) {
      if (canGoBack) {
        session?.goBack()
      } else {
        finish()
      }
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    intent.getStringExtra(GECKO_READER_URL_EXTRA)?.let { session?.loadUri(it) }
  }

  override fun onDestroy() {
    geckoView?.releaseSession()
    session?.close()
    session = null
    super.onDestroy()
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
  fun getRuntime(applicationContext: android.content.Context): GeckoRuntime =
    runtime ?: GeckoRuntime.create(applicationContext).also { runtime = it }
}
