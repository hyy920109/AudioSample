package com.hyy.readeraudiosample.model

import android.content.Context
import android.net.Uri
import com.hyy.readeraudiosample.R

/**
 *Create by hyy on 2020/12/15
 * //正在播放的元数据
 *
 */
data class NowPlayingMetadata(
    val id: String,
    val albumArtUri: Uri?,
    val title: String?,
    val subtitle: String?,
    val durationStr: String,
    val totalDuration: Int//totalSeconds
) {
    companion object {
        /**
         * Utility method to convert milliseconds to a display of minutes and seconds
         */
        fun timestampToMSS(context: Context, position: Long): String {
            val totalSeconds = Math.floor(position / 1E3).toInt()
            val minutes = totalSeconds / 60
            val remainingSeconds = totalSeconds - (minutes * 60)
            return if (position < 0) context.getString(R.string.duration_unknown)
            else context.getString(R.string.duration_format).format(minutes, remainingSeconds)
        }
    }
}
