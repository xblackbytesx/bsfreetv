package com.bsfreetv.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.tv.TvContract.WatchNextPrograms as W
import android.util.Log
import androidx.compose.runtime.Immutable
import com.bsfreetv.launcher.domain.home.HomeCommands
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class NextUpItem(
    val id: Long,
    val packageName: String,
    val title: String,
    val subtitle: String?,
    val imageUri: String?,
    val intentUri: String?,
    /** 0..1, or null when the app does not report a position. */
    val progress: Float?,
    val lastEngagement: Long,
)

/**
 * Reads other apps' Watch Next rows from TvProvider. With READ_TV_LISTINGS
 * (dangerous, granted at runtime or via adb) Android 11 returns every row an
 * app marked searchable; without it only our own (none). Custom selections
 * throw, so filtering happens here.
 */
@Singleton
class WatchNext @Inject constructor(@ApplicationContext private val context: Context) {
    val hasPermission: Boolean
        get() = context.checkSelfPermission(HomeCommands.TV_LISTINGS) == PackageManager.PERMISSION_GRANTED

    suspend fun load(packages: Set<String>, limit: Int = 12): List<NextUpItem> = withContext(Dispatchers.IO) {
        if (!hasPermission || packages.isEmpty()) return@withContext emptyList()
        runCatching {
            context.contentResolver.query(W.CONTENT_URI, PROJECTION, null, null, null)?.use { c -> rows(c) }.orEmpty()
        }.onFailure { Log.w(TAG, "watch next query failed", it) }.getOrDefault(emptyList())
            .filter { it.packageName in packages }
            .sortedByDescending { it.lastEngagement }
            .distinctBy { it.packageName to it.title }
            .take(limit)
    }

    fun launch(item: NextUpItem): Boolean {
        val uri = item.intentUri ?: return false
        return runCatching {
            context.startActivity(Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w(TAG, "cannot open ${item.title}", it) }.isSuccess
    }

    private fun rows(c: Cursor): List<NextUpItem> {
        fun idx(col: String) = c.getColumnIndex(col)
        val iId = idx(W._ID)
        val iPkg = idx(W.COLUMN_PACKAGE_NAME)
        val iTitle = idx(W.COLUMN_TITLE)
        val iEp = idx(W.COLUMN_EPISODE_TITLE)
        val iSeason = idx(W.COLUMN_SEASON_DISPLAY_NUMBER)
        val iEpNo = idx(W.COLUMN_EPISODE_DISPLAY_NUMBER)
        val iThumb = idx(W.COLUMN_THUMBNAIL_URI)
        val iPoster = idx(W.COLUMN_POSTER_ART_URI)
        val iIntent = idx(W.COLUMN_INTENT_URI)
        val iPos = idx(W.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)
        val iDur = idx(W.COLUMN_DURATION_MILLIS)
        val iEng = idx(W.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS)
        val iBrowsable = idx(W.COLUMN_BROWSABLE)
        fun str(i: Int) = if (i >= 0 && !c.isNull(i)) c.getString(i)?.takeIf { it.isNotBlank() } else null
        fun long(i: Int) = if (i >= 0 && !c.isNull(i)) c.getLong(i) else null
        val out = ArrayList<NextUpItem>()
        while (c.moveToNext()) {
            if (iBrowsable >= 0 && !c.isNull(iBrowsable) && c.getInt(iBrowsable) == 0) continue
            val pkg = str(iPkg) ?: continue
            val title = str(iTitle) ?: continue
            out += NextUpItem(
                id = long(iId) ?: continue,
                packageName = pkg,
                title = title,
                subtitle = episodeLine(str(iSeason), str(iEpNo), str(iEp)),
                imageUri = str(iThumb) ?: str(iPoster),
                intentUri = str(iIntent),
                progress = progress(long(iPos), long(iDur)),
                lastEngagement = long(iEng) ?: 0L,
            )
        }
        return out
    }

    companion object {
        private const val TAG = "WatchNext"
        private val PROJECTION = arrayOf(
            W._ID, W.COLUMN_PACKAGE_NAME, W.COLUMN_TITLE, W.COLUMN_EPISODE_TITLE,
            W.COLUMN_SEASON_DISPLAY_NUMBER, W.COLUMN_EPISODE_DISPLAY_NUMBER,
            W.COLUMN_THUMBNAIL_URI, W.COLUMN_POSTER_ART_URI, W.COLUMN_INTENT_URI,
            W.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, W.COLUMN_DURATION_MILLIS,
            W.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, W.COLUMN_BROWSABLE,
        )

        /** "S2 · E5 · The Title", omitting whatever is missing. */
        fun episodeLine(season: String?, episode: String?, episodeTitle: String?): String? =
            listOfNotNull(season?.let { "S$it" }, episode?.let { "E$it" }, episodeTitle)
                .joinToString(" · ").ifEmpty { null }

        fun progress(position: Long?, duration: Long?): Float? =
            if (position == null || duration == null || duration <= 0 || position <= 0) null
            else (position.toFloat() / duration).coerceIn(0f, 1f)
    }
}
