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

package org.kiwix.kiwixmobile.core.search.viewmodel.effects

import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kiwix.kiwixmobile.core.base.SideEffect
import org.kiwix.kiwixmobile.core.dao.RecentSearchRoomDao

/**
 * Saves the search *term* the user typed to the recent searches, so tapping a
 * recent entry re-runs that search (rather than reopening a single article).
 * The stored URL is null on purpose: a recent search is a query, not a page.
 */
@Suppress("InjectDispatcher")
data class SaveSearchToRecents(
  private val recentSearchRoomDao: RecentSearchRoomDao,
  private val searchTerm: String,
  private val id: String?,
  private val viewModelScope: CoroutineScope
) : SideEffect<Unit> {
  override fun invokeWith(activity: AppCompatActivity) {
    if (searchTerm.isBlank()) return
    id?.let {
      viewModelScope.launch(Dispatchers.IO) {
        recentSearchRoomDao.saveSearch(searchTerm, it, null)
      }
    }
  }
}
