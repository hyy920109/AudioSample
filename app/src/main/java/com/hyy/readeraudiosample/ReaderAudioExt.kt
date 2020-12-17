package com.hyy.readeraudiosample

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import com.hyy.readeraudiosample.model.ChapterAudioItem
import java.util.concurrent.TimeUnit

/**
 *Create by hyy on 2020/12/14
 */

/**
 * Extension method for [MediaMetadataCompat.Builder] to set the fields from
 * our JSON constructed object (to make the code a bit easier to see).
 */
fun MediaMetadataCompat.Builder.from(novel: ChapterAudioItem): MediaMetadataCompat.Builder {
    // The duration from the JSON is given in seconds, but the rest of the code works in
    // milliseconds. Here's where we convert to the proper units.
    val durationMs = TimeUnit.SECONDS.toMillis(novel.duration)

    id = novel.id
    title = novel.chapterName
//    artist = jsonMusic.artist
    album = novel.bookName
    duration = durationMs
    // genre = jsonMusic.genre
    mediaUri = novel.source
    albumArtUri = novel.img
    trackNumber = 1
    trackCount = 1
    flag = MediaBrowserCompat.MediaItem.FLAG_PLAYABLE

    // To make things easier for *displaying* these, set the display properties as well.
    displayTitle = novel.chapterName
    displaySubtitle = novel.bookName
    displayDescription = novel.chapterName
    displayIconUri = novel.img

    // Add downloadStatus to force the creation of an "extras" bundle in the resulting
    // MediaMetadataCompat object. This is needed to send accurate metadata to the
    // media session during updates.
    downloadStatus = MediaDescriptionCompat.STATUS_NOT_DOWNLOADED


    // Allow it to be used in the typical builder style.
    return this
}

val Long.toSeconds: Int
    get() = Math.floor(this / 1E3).toInt()

