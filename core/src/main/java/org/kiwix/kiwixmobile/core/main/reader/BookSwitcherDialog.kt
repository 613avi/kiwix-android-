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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kiwix.kiwixmobile.core.R
import org.kiwix.kiwixmobile.core.utils.ComposeDimens.EIGHT_DP
import org.kiwix.kiwixmobile.core.utils.ComposeDimens.SEARCH_ITEM_TEXT_SIZE
import org.kiwix.kiwixmobile.core.utils.ComposeDimens.SIXTEEN_DP
import org.kiwix.kiwixmobile.core.utils.ComposeDimens.TWELVE_DP
import org.kiwix.kiwixmobile.core.zim_manager.fileselect_view.BooksOnDiskListItem.BookOnDisk

const val BOOK_SWITCHER_ITEM_TESTING_TAG = "bookSwitcherItemTestingTag"
private const val BOOK_SWITCHER_MAX_HEIGHT_DP = 420

/**
 * Content of the "Switch book" dialog: a scrollable list of the ZIM files on
 * the device. Tapping a book opens it in the reader. The currently open book
 * (matched by [currentBookId]) is marked and cannot be re-selected.
 */
@Composable
fun BookSwitcherDialogContent(
  books: List<BookOnDisk>,
  currentBookId: String?,
  onBookClick: (BookOnDisk) -> Unit
) {
  if (books.isEmpty()) {
    Text(
      text = stringResource(R.string.no_book_to_switch),
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(SIXTEEN_DP)
    )
    return
  }
  LazyColumn(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(max = BOOK_SWITCHER_MAX_HEIGHT_DP.dp)
  ) {
    items(books, key = { it.id }) { bookOnDisk ->
      BookSwitcherItem(
        bookOnDisk = bookOnDisk,
        isCurrent = bookOnDisk.book.id == currentBookId,
        onClick = { onBookClick(bookOnDisk) }
      )
    }
  }
}

@Composable
private fun BookSwitcherItem(
  bookOnDisk: BookOnDisk,
  isCurrent: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(if (isCurrent) Modifier else Modifier.clickable(onClick = onClick))
      .padding(horizontal = EIGHT_DP, vertical = TWELVE_DP),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = bookOnDisk.book.title,
        fontSize = SEARCH_ITEM_TEXT_SIZE,
        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      if (isCurrent) {
        Text(
          text = stringResource(R.string.currently_open),
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
      }
    }
    if (isCurrent) {
      Icon(
        painter = painterResource(id = R.drawable.ic_check_circle_blue_24dp),
        contentDescription = stringResource(R.string.currently_open),
        modifier = Modifier.size(24.dp)
      )
    }
  }
}
