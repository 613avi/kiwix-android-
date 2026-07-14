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
import org.kiwix.kiwixmobile.core.utils.files.Log
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
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

  // The scheme://host:port of the localhost server serving the offline book.
  // Only URLs on this origin are shown in Gecko; anything else (the live
  // internet) is handed to an external browser.
  private var serverOrigin: String? = null

  override var canGoBack: Boolean = false
    private set

  override var currentUrl: String? = null
    private set

  override var currentTitle: String? = null
    private set

  override var callback: EmbeddedGeckoReaderCallback? = null

  init {
    // Render ZIM pages with a phone sized viewport even when they lack a mobile
    // <meta viewport>, so desktop-width pages don't overflow off screen.
    session.settings.setViewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
    // Offline ZIM articles are static content; running the page's JavaScript
    // (e.g. MediaWiki's ResourceLoader) only slows loads down and can reach for
    // the live internet. Disabling it makes pages load much faster. Extension
    // content scripts (the layout fixup) are privileged and still run.
    session.settings.setAllowJavascript(false)
    session.navigationDelegate = object : GeckoSession.NavigationDelegate {
      override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        this@EmbeddedGeckoReader.canGoBack = canGoBack
      }

      override fun onLocationChange(
        session: GeckoSession,
        url: String?,
        perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
        hasUserGesture: Boolean
      ) {
        // Track the current page so the reader can keep its URL/bookmark state
        // in sync (there is no WebView to query in the Gecko build).
        this@EmbeddedGeckoReader.currentUrl = url
      }

      override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest
      ): GeckoResult<AllowOrDeny> {
        val uri = request.uri
        val origin = serverOrigin
        // Only the offline book (served from localhost) may load inside Gecko.
        // Everything else is a live-internet link: hand it to an external
        // browser and keep the reader on the offline content.
        return if (uri.startsWith("about:") ||
          (origin != null && uri.startsWith(origin))
        ) {
          GeckoResult.allow()
        } else {
          callback?.onExternalLinkRequested(uri)
          GeckoResult.deny()
        }
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
    session.contentDelegate = object : GeckoSession.ContentDelegate {
      override fun onTitleChange(session: GeckoSession, title: String?) {
        this@EmbeddedGeckoReader.currentTitle = title
      }
    }
    session.progressDelegate = object : GeckoSession.ProgressDelegate {
      override fun onPageStop(session: GeckoSession, success: Boolean) {
        // Report finished loads so the reader can record history and refresh its
        // URL/bookmark state, mirroring the WebView build.
        if (success) {
          callback?.onGeckoPageLoaded(currentUrl, currentTitle)
        }
      }
    }
    session.open(GeckoRuntimeProvider.getRuntime(context.applicationContext))
    geckoView.setSession(session)
  }

  override val view: View get() = geckoView

  override fun loadUrl(url: String, fallbackUrl: String?) {
    this.fallbackUrl = fallbackUrl
    triedFallback = false
    serverOrigin = originOf(url)
    session.loadUri(url)
  }

  // Extracts scheme://host:port from a URL, used to tell offline content
  // (served from localhost) apart from live-internet links.
  private fun originOf(url: String): String? =
    runCatching {
      val uri = java.net.URI(url)
      if (uri.scheme != null && uri.host != null) {
        val portPart = if (uri.port != -1) ":${uri.port}" else ""
        "${uri.scheme}://${uri.host}$portPart"
      } else {
        null
      }
    }.getOrNull()

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
    runtime ?: GeckoRuntime.create(applicationContext).also {
      runtime = it
      installLayoutFixupExtension(it)
    }

  // Installs the bundled "layoutfix" web extension (a content script that adds a
  // mobile viewport and a right-to-left direction to ZIM pages that lack them).
  // Fire-and-forget: a failure here only means pages keep their original layout.
  private fun installLayoutFixupExtension(runtime: GeckoRuntime) {
    runCatching {
      runtime.webExtensionController
        .ensureBuiltIn(LAYOUT_FIXUP_EXTENSION_URL, LAYOUT_FIXUP_EXTENSION_ID)
        .accept(
          { _ -> /* installed */ },
          { error -> Log.e("EmbeddedGeckoReader", "Layout fixup extension failed: $error") }
        )
    }.onFailure {
      Log.e("EmbeddedGeckoReader", "Could not install layout fixup extension. $it")
    }
  }

  private const val LAYOUT_FIXUP_EXTENSION_URL =
    "resource://android/assets/extensions/layoutfix/"
  private const val LAYOUT_FIXUP_EXTENSION_ID = "layoutfix@kiwix.org"
}
