package com.sandolpin.gallery.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** ピン留めしたフォルダのID(MediaStoreのBUCKET_ID)を、端末内に保存する。 */
@Singleton
class PinnedFolderRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val pinnedKey = stringSetPreferencesKey("pinned_folder_ids")

    val pinnedIds: Flow<Set<Long>> = dataStore.data.map { prefs ->
        prefs[pinnedKey].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun setPinned(folderId: Long, pinned: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[pinnedKey].orEmpty().toMutableSet()
            if (pinned) current += folderId.toString() else current -= folderId.toString()
            prefs[pinnedKey] = current
        }
    }

    /** ピン留めを、[folderIds] だけに置き換える(設定の読み込みで使う)。 */
    suspend fun setPinnedIds(folderIds: Set<Long>) {
        dataStore.edit { prefs ->
            prefs[pinnedKey] = folderIds.map { it.toString() }.toSet()
        }
    }
}