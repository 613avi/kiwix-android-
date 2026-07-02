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

package org.kiwix.kiwixmobile.core.main.reader

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.kiwix.kiwixmobile.core.reader.ZimReaderSource
import org.kiwix.kiwixmobile.core.utils.files.Log
import org.kiwix.libkiwix.Book
import org.kiwix.libkiwix.Library
import org.kiwix.libkiwix.Server
import java.net.ServerSocket

private const val TAG = "ExternalBrowserContentServer"
private const val LOCALHOST = "127.0.0.1"

/**
 * A minimal localhost kiwix server used as a fallback on devices where the
 * Android System WebView is not available. It serves the currently opened ZIM
 * file over http://127.0.0.1:<port>/ so the content can be read in any
 * installed browser, regardless of its engine (e.g. Gecko based browsers such
 * as Firefox).
 *
 * The server is bound to the loopback interface only, so the content is not
 * exposed to other devices on the network (unlike the WiFi hotspot server).
 */
object ExternalBrowserContentServer {
  // Serializes concurrent start() calls (e.g. opening the book and a searched
  // page at the same time) so only one server is created per ZIM file.
  private val serverMutex = Mutex()
  private var server: Server? = null

  // The server runs on top of this library. Keep a reference to it so the
  // native library object is not garbage collected while the server is
  // running. See https://github.com/kiwix/java-libkiwix/issues/51
  @Suppress("UnusedPrivateProperty")
  private var library: Library? = null
  private var servedZimReaderSource: ZimReaderSource? = null
  private var serverUrl: String? = null

  /**
   * Starts (or reuses) a localhost server serving the given ZIM file.
   *
   * @return the URL of the served book, or null if the server could not be started.
   */
  @Suppress("InjectDispatcher")
  suspend fun start(
    zimReaderSource: ZimReaderSource,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
  ): String? =
    withContext(dispatcher) {
      serverMutex.withLock {
        if (server != null && zimReaderSource == servedZimReaderSource) {
          return@withContext serverUrl
        }
        stop()
        startNewServer(zimReaderSource)
      }
    }

  private suspend fun startNewServer(zimReaderSource: ZimReaderSource): String? =
    runCatching {
      val archive = zimReaderSource.createArchive() ?: return null
      val kiwixLibrary = Library()
      kiwixLibrary.addBook(Book().apply { update(archive) })
      val port = findFreePort()
      val kiwixServer =
        Server(kiwixLibrary).apply {
          setAddress(LOCALHOST)
          setPort(port)
        }
      if (!kiwixServer.start()) {
        Log.e(TAG, "Could not start the localhost server on port $port")
        return null
      }
      library = kiwixLibrary
      server = kiwixServer
      servedZimReaderSource = zimReaderSource
      serverUrl = "http://$LOCALHOST:$port/"
      serverUrl
    }.onFailure {
      Log.e(TAG, "Could not serve ${zimReaderSource.toDatabase()} for an external browser. $it")
    }.getOrNull()

  fun stop() {
    runCatching { server?.stop() }
    server = null
    library = null
    servedZimReaderSource = null
    serverUrl = null
  }

  private fun findFreePort(): Int = ServerSocket(0).use(ServerSocket::getLocalPort)
}
