/*
 * Kiwix Android
 * Copyright (c) 2020 Kiwix <android.kiwix.org>
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

package org.kiwix.kiwixmobile.nav.destination.reader

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.kiwix.kiwixmobile.cachedComponent
import org.kiwix.kiwixmobile.core.R.string
import org.kiwix.kiwixmobile.core.base.BaseActivity
import org.kiwix.kiwixmobile.core.base.FragmentActivityExtensions.Super
import org.kiwix.kiwixmobile.core.base.FragmentActivityExtensions.Super.ShouldCall
import org.kiwix.kiwixmobile.core.utils.ZERO
import org.kiwix.kiwixmobile.core.extensions.ActivityExtensions.safelyConsumeObservable
import org.kiwix.kiwixmobile.core.extensions.ActivityExtensions.getObservableNavigationResult
import org.kiwix.kiwixmobile.core.extensions.isFileExist
import org.kiwix.kiwixmobile.core.extensions.snack
import org.kiwix.kiwixmobile.core.extensions.toast
import org.kiwix.kiwixmobile.core.extensions.update
import org.kiwix.kiwixmobile.core.main.CoreMainActivity
import org.kiwix.kiwixmobile.core.main.PAGE_URL_KEY
import org.kiwix.kiwixmobile.core.main.ZIM_FILE_URI_KEY
import org.kiwix.kiwixmobile.core.main.reader.CoreReaderFragment
import org.kiwix.kiwixmobile.core.main.reader.OPEN_HOME_SCREEN_DELAY
import org.kiwix.kiwixmobile.core.main.reader.RestoreOrigin
import org.kiwix.kiwixmobile.core.main.reader.RestoreOrigin.FromExternalLaunch
import org.kiwix.kiwixmobile.core.main.reader.RestoreOrigin.FromSearchScreen
import org.kiwix.kiwixmobile.core.main.reader.SEARCH_ITEM_TITLE_KEY
import org.kiwix.kiwixmobile.core.page.history.models.WebViewHistoryItem
import org.kiwix.kiwixmobile.core.reader.ZimReaderSource
import org.kiwix.kiwixmobile.core.reader.ZimReaderSource.Companion.fromDatabaseValue
import org.kiwix.kiwixmobile.core.utils.TAG_KIWIX
import org.kiwix.kiwixmobile.core.utils.files.FileUtils
import org.kiwix.kiwixmobile.core.utils.files.Log
import org.kiwix.kiwixmobile.core.reader.ZimFileReader.Companion.CONTENT_PREFIX
import org.kiwix.kiwixmobile.reader.EmbeddedReader
import org.kiwix.kiwixmobile.reader.EmbeddedReaderCallback
import org.kiwix.kiwixmobile.reader.EmbeddedReaderSupport
import org.kiwix.kiwixmobile.main.KiwixMainActivity
import org.kiwix.kiwixmobile.ui.KiwixDestination
import java.io.File

class KiwixReaderFragment : CoreReaderFragment() {
  private var isFullScreenVideo: Boolean = false
  private var embeddedReader: EmbeddedReader? = null

  // Bridges the embedded renderer back to the reader so a build with an embedded
  // engine records history and hands external links to a browser like the
  // WebView build.
  private val embeddedReaderCallback = object : EmbeddedReaderCallback {
    override fun onPageLoaded(url: String?, title: String?) {
      onAlternativeReaderPageLoaded()
    }

    override fun onExternalLinkRequested(url: String) {
      // Live-internet links are not part of the offline book: open them in an
      // external browser instead of loading them inside the reader.
      openExternalUrl(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
  }

  // Cached value of KiwixDataStore.preferGeckoRenderer, refreshed whenever the
  // reader decides how to open a book. Only meaningful when an embedded engine
  // is present; a WebView build has none.
  private var preferEmbeddedReader = false

  override fun inject(baseActivity: BaseActivity) {
    baseActivity.cachedComponent.inject(this)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val activity = activity as CoreMainActivity
    readerScreenState.update {
      copy(onOpenLibraryButtonClicked = {
        val navOptions = NavOptions.Builder()
          .setPopUpTo(KiwixDestination.Reader.route, inclusive = true)
          .build()
        activity.navigate(KiwixDestination.Library.route, navOptions)
      })
    }
    activity.enableLeftDrawer()
    openPageInBookFromNavigationArguments()
  }

  @Suppress("MagicNumber")
  internal fun openPageInBookFromNavigationArguments() {
    showProgressBarWithProgress(30)
    val kiwixMainActivity = activity as? KiwixMainActivity
    val zimFileUri = getNavigationResult(ZIM_FILE_URI_KEY, kiwixMainActivity)
    val pageUrl = getNavigationResult(PAGE_URL_KEY, kiwixMainActivity)
    val searchItemTitle = getNavigationResult(SEARCH_ITEM_TITLE_KEY, kiwixMainActivity)
    runSafelyInCoreReaderLifecycleScope {
      // Refresh the cached renderer preference before deciding how to open pages.
      refreshEmbeddedReaderPreference()
      if (pageUrl.isNotEmpty()) {
        if (zimFileUri.isNotEmpty()) {
          tryOpeningZimFile(zimFileUri)
        } else {
          // Set up bookmarks for the current book when opening bookmarks from the Bookmark screen.
          // This is necessary because we are not opening the ZIM file again; the bookmark is
          // inside the currently opened book. Bookmarks are set up when opening the ZIM file.
          // See https://github.com/kiwix/kiwix-android/issues/3541
          zimReaderContainer?.zimFileReader?.let(::setUpBookmarks)
        }
        hideProgressBar()
        loadUrlWithCurrentWebview(pageUrl)
      } else {
        if (zimFileUri.isNotEmpty()) {
          tryOpeningZimFile(zimFileUri)
        } else {
          isWebViewHistoryRestoring = true
          val restoreOrigin =
            if (searchItemTitle.isNotEmpty()) FromSearchScreen else FromExternalLaunch
          manageExternalLaunchAndRestoringViewState(restoreOrigin)
        }
      }
      // Consume the argument.
      kiwixMainActivity?.apply {
        safelyConsumeObservable<String>(ZIM_FILE_URI_KEY)
        safelyConsumeObservable<String>(PAGE_URL_KEY)
        safelyConsumeObservable<String>(SEARCH_ITEM_TITLE_KEY)
      }
    }
  }

  private fun getNavigationResult(key: String, kiwixMainActivity: KiwixMainActivity?) =
    kiwixMainActivity?.getObservableNavigationResult<String>(key)?.value.orEmpty()

  private suspend fun tryOpeningZimFile(zimFileUri: String) {
    // Stop any ongoing WebView loading and clear the WebView list
    // before setting a new ZIM file to the reader. This helps prevent native crashes.
    // The WebView's `shouldInterceptRequest` method continues to be invoked until the WebView is
    // fully destroyed, which can cause a native crash. This happens because a new ZIM file is set
    // in the reader while the WebView is still trying to access content from the old archive.
    stopOngoingLoadingAndClearWebViewList()
    // Close the previously opened book in the reader before opening a new ZIM file
    // to avoid native crashes due to "null pointer dereference." These crashes can occur
    // when setting a new ZIM file in the archive while the previous one is being disposed of.
    // Since the WebView may still asynchronously request data from the disposed archive,
    // we close the previous book before opening a new ZIM file in the archive.
    closeZimBook()
    // Update the reader screen title to prevent showing the previously set title
    // when creating the new archive object.
    updateTitle()
    val filePath = FileUtils.getLocalFilePathByUri(
      requireActivity().applicationContext,
      zimFileUri.toUri()
    )
    if (filePath == null || !File(filePath).isFileExist()) {
      // Close the previously opened book in the reader. Since this file is not found,
      // it will not be set in the zimFileReader. The previously opened ZIM file
      // will be saved when we move between fragments. If we return to the reader again,
      // it will attempt to open the last opened ZIM file with the last loaded URL,
      // which is inside the non-existing ZIM file. This leads to unexpected behavior.
      exitBook()
      activity.toast(getString(string.error_file_not_found, zimFileUri))
      return
    }
    val zimReaderSource = ZimReaderSource(File(filePath))
    openZimFile(zimReaderSource)
  }

  override fun openHomeScreen() {
    runSafelyInCoreReaderLifecycleScope {
      // Run safely because it is runs after 300 MS.
      delay(OPEN_HOME_SCREEN_DELAY)
      if (webViewList.isEmpty()) {
        hideTabSwitcher(false)
      }
    }
  }

  /**
   * Hides the tab switcher and optionally closes the ZIM book based on the `shouldCloseZimBook` parameter.
   *
   * @param shouldCloseZimBook If `true`, the ZIM book will be closed, and the `ZimFileReader` will be set to `null`.
   * If `false`, it skips setting the `ZimFileReader` to `null`. This is particularly useful when restoring tabs,
   * as setting the `ZimFileReader` to `null` would require re-creating it, which is a resource-intensive operation,
   * especially for large ZIM files.
   *
   * Refer to the following methods for more details:
   * @See exitBook
   * @see closeTab
   * @see closeAllTabs
   */
  override fun hideTabSwitcher(shouldCloseZimBook: Boolean) {
    enableLeftDrawer()
    (requireActivity() as? CoreMainActivity)?.showBottomAppBar()
    if (webViewList.isEmpty()) {
      readerMenuState?.hideTabSwitcher()
      exitBook(shouldCloseZimBook)
    } else {
      // Reset the top margin of web views to 0 to remove any previously set margin
      // This ensures that the web views are displayed without any additional
      // top margin for kiwix main app.
      // setTopMarginToWebViews(0)
      readerScreenState.update {
        copy(
          shouldShowBottomAppBar = true,
          pageLoadingItem = false to ZERO,
        )
      }
      readerMenuState?.showWebViewOptions(urlIsValid())
      selectTab(currentWebViewIndex)
    }
  }

  @Suppress("DEPRECATION")
  override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
    super.onCreateOptionsMenu(menu, menuInflater)
    if (zimReaderContainer?.zimFileReader == null) {
      readerMenuState?.hideBookSpecificMenuItems()
    }
  }

  override fun onCreateOptionsMenu(
    menu: Menu,
    activity: AppCompatActivity
  ): Super = ShouldCall

  override fun onResume() {
    super.onResume()
    if (isFullScreenVideo) {
      hideNavBar()
    }
  }

  override suspend fun restoreViewStateOnInvalidWebViewHistory() {
    Log.d(TAG_KIWIX, "Kiwix normal start, no zimFile loaded last time  -> display home page")
    exitBook()
  }

  /**
   * Restores the view state based on the provided webViewHistoryItemList data and restore origin.
   *
   * Depending on the `restoreOrigin`, this method either restores the last opened ZIM file
   * (if the launch is external) or skips re-opening the ZIM file when coming from the search screen,
   * as the ZIM file is already set in the reader. The method handles setting up the ZIM file and bookmarks,
   * and restores the tabs and positions from the provided data.
   *
   * @param webViewHistoryItemList   WebViewHistoryItem list representing the list of articles to be restored.
   * @param currentTab Index of the tab to be restored as the currently active one.
   * @param restoreOrigin Indicates whether the restoration is triggered from an external launch or the search screen.
   * @param onComplete  Callback to be invoked upon completion of the restoration process.
   */
  override suspend fun restoreViewStateOnValidWebViewHistory(
    webViewHistoryItemList: List<WebViewHistoryItem>,
    currentTab: Int,
    restoreOrigin: RestoreOrigin,
    onComplete: () -> Unit
  ) {
    if (shouldOpenInAlternativeRenderer()) {
      // Tabs are WebView based; with an embedded renderer the book is
      // simply (re)opened and any pending search result is loaded afterwards
      // by the onComplete callback.
      restoreBookInEmbeddedReader(restoreOrigin, onComplete)
      return
    }
    when (restoreOrigin) {
      FromExternalLaunch -> {
        if (!isAdded) return
        val zimReaderSource =
          kiwixDataStore?.currentZimFile?.map { value ->
            fromDatabaseValue(value)
          }?.first()
        if (zimReaderSource?.canOpenInLibkiwix() == true) {
          if (zimReaderContainer?.zimReaderSource == null) {
            openZimFile(zimReaderSource)
            Log.d(
              TAG_KIWIX,
              "Kiwix normal start, Opened last used zimFile: -> ${zimReaderSource.toDatabase()}"
            )
          } else {
            zimReaderContainer?.zimFileReader?.let(::setUpBookmarks)
          }
          restoreTabs(webViewHistoryItemList, currentTab, onComplete)
        } else {
          readerScreenState.value.snackBarHostState.snack(
            requireActivity().getString(string.zim_not_opened),
            lifecycleScope = lifecycleScope
          )
          exitBook() // hide the options for zim file to avoid unexpected UI behavior
        }
      }

      FromSearchScreen -> {
        restoreTabs(webViewHistoryItemList, currentTab, onComplete)
      }
    }
  }

  override fun onFullscreenVideoToggled(isFullScreen: Boolean) {
    isFullScreenVideo = isFullScreen
    if (isFullScreenVideo) {
      hideNavBar()
    } else {
      showNavBar()
    }
    super.onFullscreenVideoToggled(isFullScreen)
  }

  private fun hideNavBar() {
    (requireActivity() as CoreMainActivity).hideBottomAppBar()
  }

  private fun showNavBar() {
    (requireActivity() as CoreMainActivity).showBottomAppBar()
  }

  override fun createNewTab() {
    newMainPageTab()
  }

  /**
   * Open books with the embedded engine when the user prefers it in the
   * settings, or when this device has no usable WebView. Only applies to builds
   * that bundle one (see EmbeddedReaderSupport); a WebView build has none.
   */
  override suspend fun shouldOpenInAlternativeRenderer(): Boolean =
    if (EmbeddedReaderSupport.IS_AVAILABLE) {
      // Such builds render every book with the bundled engine and never touch
      // the Android WebView (which may be missing, broken, or censored by an
      // MDM). The engine is served over a localhost server that bypasses any
      // WebView based content filtering.
      refreshEmbeddedReaderPreference()
    } else {
      // Regular build: fall back to the native (WebView-free) reader mode.
      super.shouldOpenInAlternativeRenderer()
    }

  // With an embedded engine the Android WebView must never be instantiated, not even
  // transiently while restoring tabs.
  override fun isWebViewRenderingDisabled(): Boolean = EmbeddedReaderSupport.IS_AVAILABLE

  /**
   * With an embedded engine there is no WebView to read the current URL from, so
   * derive it from the embedded renderer, which shows the localhost
   * server URL (…/content/<bookId>/<path>); map it back to the
   * `https://kiwix.app/<path>` content URL the rest of the app uses so bookmarks
   * and URL tracking stay consistent with the WebView build.
   */
  override fun currentArticleUrl(): String? {
    if (!EmbeddedReaderSupport.IS_AVAILABLE) return super.currentArticleUrl()
    val servedUrl = embeddedReader?.currentUrl ?: return null
    val bookId = zimReaderContainer?.zimFileReader?.id ?: return null
    val marker = "content/$bookId/"
    val pathStart = servedUrl.indexOf(marker)
    return if (pathStart >= 0) {
      CONTENT_PREFIX + servedUrl.substring(pathStart + marker.length)
    } else {
      null
    }
  }

  override fun currentArticleTitle(): String? =
    if (EmbeddedReaderSupport.IS_AVAILABLE) {
      embeddedReader?.currentTitle
    } else {
      super.currentArticleTitle()
    }

  /**
   * Refreshes the cached [preferEmbeddedReader] flag. Builds with an embedded
   * engine always render with it, so this is unconditionally true there; the
   * embedded engine, so this is unconditionally true there; the settings switch
   * settings switch only matters as a (currently no-op) escape hatch. In a
   * WebView build the
   * flag has no effect.
   */
  private suspend fun refreshEmbeddedReaderPreference(): Boolean =
    (
      EmbeddedReaderSupport.IS_AVAILABLE ||
        kiwixDataStore?.preferGeckoRenderer?.first() == true
    ).also { preferEmbeddedReader = it }

  /**
   * Whether pages should currently be rendered with the embedded engine.
   * Uses the cached settings value so it can be checked from non-suspending
   * code paths (e.g. URL loading).
   */
  private fun useEmbeddedReader(): Boolean =
    EmbeddedReaderSupport.IS_AVAILABLE &&
      (isAlternativeReaderActive() || preferEmbeddedReader || isWebViewNotAvailable())

  /**
   * Shows the current book inside the reader screen with the embedded engine,
   * served by the in-app localhost kiwix server. When [pageUrl]
   * (a https://kiwix.app/... URL) is given, that page is loaded; otherwise the
   * main page of the book is loaded.
   */
  private suspend fun openBookInEmbeddedReader(pageUrl: String? = null) {
    val baseUrl = startLocalContentServer() ?: return
    val reader = embeddedReader
      ?: EmbeddedReaderSupport.createEmbeddedReader(requireContext())?.also { embeddedReader = it }
    if (reader == null) {
      activity.toast(string.failed_to_open_in_browser)
      return
    }
    reader.callback = embeddedReaderCallback
    // If the served page could not be loaded, fall back to the server root,
    // which lists the served book.
    reader.loadUrl(servedUrlForPage(baseUrl, pageUrl), baseUrl)
    setAlternativeReaderView(reader.view)
    readerMenuState?.onFileOpened(true)
    updateTitle()
  }

  /**
   * Maps a https://kiwix.app/... page URL (or the book's main page when null)
   * to the corresponding URL on the localhost kiwix server. The server
   * addresses books by their ZIM UUID.
   */
  private fun servedUrlForPage(baseUrl: String, pageUrl: String?): String {
    val zimFileReader = zimReaderContainer?.zimFileReader
    val bookId = zimFileReader?.id
    val pagePath = pageUrl?.substringAfter(CONTENT_PREFIX) ?: zimFileReader?.mainPage
    return if (bookId != null && pagePath != null) {
      "${baseUrl}content/$bookId/$pagePath"
    } else {
      baseUrl
    }
  }

  override fun openBookInAlternativeRenderer() {
    if (EmbeddedReaderSupport.IS_AVAILABLE) {
      lifecycleScope.launch { openBookInEmbeddedReader() }
    } else {
      super.openBookInAlternativeRenderer()
    }
  }

  override fun loadUrlInAlternativeReader(url: String): Boolean {
    if (!EmbeddedReaderSupport.IS_AVAILABLE) return super.loadUrlInAlternativeReader(url)
    if (!useEmbeddedReader()) return false
    lifecycleScope.launch { openBookInEmbeddedReader(url) }
    return true
  }

  override fun onAlternativeReaderBackPressed(): Boolean {
    if (!EmbeddedReaderSupport.IS_AVAILABLE) return super.onAlternativeReaderBackPressed()
    val reader = embeddedReader ?: return false
    if (!isAlternativeReaderActive() || !reader.canGoBack) return false
    reader.goBack()
    return true
  }

  override fun closeAlternativeReader() {
    if (EmbeddedReaderSupport.IS_AVAILABLE) {
      embeddedReader?.close()
      embeddedReader = null
    }
    super.closeAlternativeReader()
  }

  override fun showWebViewNotAvailableDialog() {
    // With a bundled engine there is no need to ask the user to open an external
    // browser: render the book with that engine directly.
    if (EmbeddedReaderSupport.IS_AVAILABLE) {
      openBookInAlternativeRenderer()
    } else {
      super.showWebViewNotAvailableDialog()
    }
  }

  override suspend fun invalidZimFileFound(onInvalidZimFileFound: () -> Unit) {
    // Invoke the function so that it can show toast message to user.
    runCatching { onInvalidZimFileFound.invoke() }
  }

  private suspend fun restoreBookInEmbeddedReader(restoreOrigin: RestoreOrigin, onComplete: () -> Unit) {
    when (restoreOrigin) {
      FromExternalLaunch -> {
        if (!isAdded) return
        val zimReaderSource =
          kiwixDataStore?.currentZimFile?.map { value ->
            fromDatabaseValue(value)
          }?.first()
        if (zimReaderSource?.canOpenInLibkiwix() == true) {
          if (zimReaderContainer?.zimReaderSource == null) {
            // Opens the book, which shows it with the embedded renderer.
            openZimFile(zimReaderSource)
          } else {
            zimReaderContainer?.zimFileReader?.let(::setUpBookmarks)
            openBookInEmbeddedReader()
          }
        } else {
          readerScreenState.value.snackBarHostState.snack(
            requireActivity().getString(string.zim_not_opened),
            lifecycleScope = lifecycleScope
          )
          exitBook()
        }
      }

      FromSearchScreen -> {
        // The book is already open in the container; make sure the embedded view
        // is showing before the searched page is loaded.
        if (!isAlternativeReaderActive()) {
          openBookInEmbeddedReader()
        }
      }
    }
    onComplete.invoke()
  }
}
