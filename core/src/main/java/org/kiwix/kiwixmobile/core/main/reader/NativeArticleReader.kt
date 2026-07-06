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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kiwix.kiwixmobile.core.reader.ZimFileReader.Companion.CONTENT_PREFIX
import org.kiwix.kiwixmobile.core.ui.theme.KiwixTheme
import org.kiwix.kiwixmobile.core.utils.ComposeDimens.EIGHT_DP
import org.kiwix.kiwixmobile.core.utils.ComposeDimens.SIXTEEN_DP
import org.kiwix.kiwixmobile.core.utils.files.Log
import java.net.URI

private const val TAG = "NativeArticleReader"

/**
 * A lightweight, WebView-free article reader. It fetches the HTML of a ZIM page
 * and renders it natively with Jetpack Compose: headings, paragraphs, links and
 * inline images. It does not run CSS or JavaScript, so complex layouts (tables,
 * infoboxes) are flattened, but the article text and images are readable on any
 * Android version without a WebView, a browser or the Gecko engine.
 */
class NativeArticleReader(
  context: Context,
  private val loadHtml: suspend (url: String) -> String?,
  private val loadImageBytes: suspend (url: String) -> ByteArray?,
  private val onInternalUrl: (url: String) -> Unit,
  private val onExternalUrl: (url: String) -> Unit
) {
  private var currentUrl by mutableStateOf<String?>(null)
  private val backStack = ArrayDeque<String>()

  val view: View = ComposeView(context).apply {
    setContent {
      KiwixTheme {
        ArticleScreen(
          url = currentUrl,
          loadHtml = loadHtml,
          loadImageBytes = loadImageBytes,
          onLinkClick = ::onLinkClicked
        )
      }
    }
  }

  val canGoBack: Boolean get() = backStack.size > 1

  /**
   * Loads and renders the given ZIM page URL (a https://kiwix.app/... URL). The
   * URL is pushed onto the internal back stack when [addToBackStack] is true.
   */
  fun loadUrl(url: String, addToBackStack: Boolean = true) {
    if (addToBackStack && backStack.lastOrNull() != url) {
      backStack.addLast(url)
    }
    currentUrl = url
  }

  fun goBack() {
    if (!canGoBack) return
    backStack.removeLast()
    currentUrl = backStack.lastOrNull()
  }

  fun close() {
    backStack.clear()
    currentUrl = null
  }

  private fun onLinkClicked(url: String) {
    val absolute = resolveUrl(currentUrl, url)
    if (absolute.startsWith(CONTENT_PREFIX)) {
      loadUrl(absolute)
      onInternalUrl(absolute)
    } else {
      onExternalUrl(absolute)
    }
  }
}

/**
 * A renderable block of an article: a run of formatted text or an image.
 */
sealed class ArticleBlock {
  data class TextBlock(val text: AnnotatedString) : ArticleBlock()
  data class ImageBlock(val url: String) : ArticleBlock()
}

private val IMG_TAG_REGEX = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
private val IMG_SRC_REGEX = Regex("""\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val SCRIPT_STYLE_REGEX =
  Regex(
    """<(script|style)\b[^>]*>.*?</\1>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
  )

/**
 * Splits article HTML into ordered text/image blocks. Text runs are parsed with
 * Compose's HTML support (bold/italic/headings/links); images are emitted as
 * separate blocks so they can be lazily loaded from the ZIM file.
 */
internal fun parseHtmlToBlocks(
  html: String,
  baseUrl: String,
  linkListener: LinkInteractionListener
): List<ArticleBlock> {
  val cleaned = SCRIPT_STYLE_REGEX.replace(html, "")
  val body = extractBody(cleaned)
  val blocks = mutableListOf<ArticleBlock>()
  var lastIndex = 0
  IMG_TAG_REGEX.findAll(body).forEach { match ->
    addTextBlock(blocks, body.substring(lastIndex, match.range.first), linkListener)
    val src = IMG_SRC_REGEX.find(match.value)?.groupValues?.getOrNull(1)
    if (!src.isNullOrBlank()) {
      blocks.add(ArticleBlock.ImageBlock(resolveUrl(baseUrl, src)))
    }
    lastIndex = match.range.last + 1
  }
  addTextBlock(blocks, body.substring(lastIndex), linkListener)
  return blocks
}

private fun extractBody(html: String): String {
  val bodyStart = html.indexOf("<body", ignoreCase = true)
  if (bodyStart == -1) return html
  val afterTag = html.indexOf('>', bodyStart)
  if (afterTag == -1) return html
  val bodyEnd = html.indexOf("</body>", afterTag, ignoreCase = true)
  return if (bodyEnd == -1) html.substring(afterTag + 1) else html.substring(afterTag + 1, bodyEnd)
}

private fun addTextBlock(
  blocks: MutableList<ArticleBlock>,
  htmlChunk: String,
  linkListener: LinkInteractionListener
) {
  if (htmlChunk.isBlank()) return
  val annotated = AnnotatedString.fromHtml(
    htmlChunk,
    linkStyles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline)),
    linkInteractionListener = linkListener
  )
  if (annotated.text.isNotBlank()) {
    blocks.add(ArticleBlock.TextBlock(annotated))
  }
}

/**
 * Resolves a possibly-relative URL against the current article URL, returning an
 * absolute https://kiwix.app/... (or external) URL.
 */
internal fun resolveUrl(baseUrl: String?, url: String): String =
  runCatching {
    when {
      url.startsWith("http://") || url.startsWith("https://") -> url
      baseUrl == null -> CONTENT_PREFIX + url.trimStart('/')
      else -> URI(baseUrl).resolve(url).toString()
    }
  }.getOrDefault(url)

@Composable
private fun ArticleScreen(
  url: String?,
  loadHtml: suspend (url: String) -> String?,
  loadImageBytes: suspend (url: String) -> ByteArray?,
  onLinkClick: (String) -> Unit
) {
  val linkListener = LinkInteractionListener { annotation ->
    (annotation as? LinkAnnotation.Url)?.url?.let(onLinkClick)
  }
  var isLoading by remember { mutableStateOf(false) }
  var blocks by remember { mutableStateOf<List<ArticleBlock>>(emptyList()) }
  val listState = rememberLazyListState()
  LaunchedEffect(url) {
    if (url == null) return@LaunchedEffect
    isLoading = true
    blocks = withContext(Dispatchers.Default) {
      runCatching { parseHtmlToBlocks(loadHtml(url).orEmpty(), url, linkListener) }
        .onFailure { Log.e(TAG, "Could not render $url natively. $it") }
        .getOrDefault(emptyList())
    }
    listState.scrollToItem(0)
    isLoading = false
  }
  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
      items(blocks) { block ->
        when (block) {
          is ArticleBlock.TextBlock ->
            Text(
              text = block.text,
              color = MaterialTheme.colorScheme.onBackground,
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SIXTEEN_DP, vertical = EIGHT_DP)
            )

          is ArticleBlock.ImageBlock -> ArticleImage(block.url, loadImageBytes)
        }
      }
    }
    if (isLoading) {
      CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
  }
}

@Composable
private fun ArticleImage(
  url: String,
  loadImageBytes: suspend (url: String) -> ByteArray?
) {
  val bitmap by produceState<Bitmap?>(initialValue = null, url) {
    value = withContext(Dispatchers.IO) {
      runCatching {
        loadImageBytes(url)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
      }.getOrNull()
    }
  }
  bitmap?.let {
    Image(
      bitmap = it.asImageBitmap(),
      contentDescription = null,
      contentScale = ContentScale.FillWidth,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = SIXTEEN_DP, vertical = EIGHT_DP)
    )
  }
}
