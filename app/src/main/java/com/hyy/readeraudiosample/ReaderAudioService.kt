package com.hyy.readeraudiosample

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.hyy.readeraudiosample.model.ChapterAudioItem
import java.util.*

/**
 *Create by hyy on 2020/12/14
 */
const val MY_MEDIA_ROOT_ID = "media_root_id"
private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"

/*
 * (Media) Session events
 */
const val NETWORK_FAILURE = "com.hyy.readeraudiosample.NETWORK_FAILURE"
const val MEDIA_DESCRIPTION_EXTRAS_START_PLAYBACK_POSITION_MS = "playback_start_position_ms"
const val ACTION_PLAYBACK_SPEED = "action_playback_speed"
const val ACTION_ADD_MEDIA_ITEM = "action_add_media_item"
const val PLAYBACK_SPEED = "playback_speed"
class ReaderAudioService : MediaBrowserServiceCompat() {

    companion object {
        const val TAG = "ReaderAudioService"
    }

    private var lastWindowIndex: Int = -1//上一次播放章节所在播放列表中的位置
    private var currentPlaylistItems: MutableList<MediaMetadataCompat> = mutableListOf()

    // The current player will either be an ExoPlayer (for local playback) or a CastPlayer (for
    // remote playback through a Cast device).
    private lateinit var currentPlayer: Player
    private lateinit var notificationManager: UampNotificationManager

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var mediaSessionConnector: MediaSessionConnector

    private var isForegroundService = false
    private val uAmpAudioAttributes = AudioAttributes.Builder()
        .setContentType(C.CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private val playerListener = PlayerEventListener()

    private val mediaSource = ConcatenatingMediaSource()

    /**
     * Configure ExoPlayer to handle audio focus for us.
     */
    private val exoPlayer: ExoPlayer by lazy {
        SimpleExoPlayer.Builder(this).build().apply {
            setAudioAttributes(uAmpAudioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            addListener(playerListener)

        }
    }

    private val dataSourceFactory: DefaultDataSourceFactory by lazy {
        DefaultDataSourceFactory(
            /* context= */ this,
            "com.hyy.sample.audio", /* listener= */
            null
        )
    }

    val novelModel by lazy {
        ChapterAudioItem(
            "Chapter One",
            "Test",
            "http://tts.sg.ufileos.com/audio/dev/33/54733/11670333/1/1607933874.mp3",
            "1001",
            429,
            "https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/art.jpg"
        )
    }

    val novelModel2 by lazy {
        ChapterAudioItem(
            "Chapter One",
            "Test",
            "https://storage.googleapis.com/automotive-media/Tell_The_Angels.mp3",
            "1001",
            429,
            "https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/art.jpg"
        )
    }

    val mediaMetadataCompat by lazy {
        MediaMetadataCompat.Builder()
            .from(novelModel)
            .apply {
                displayIconUri = novelModel.img // Used by ExoPlayer and Notification
                albumArtUri = novelModel.img
            }
            .build().apply {
                //将description填充数据
                description.extras?.putAll(bundle)
            }
    }

    val mediaMetadataCompat2 by lazy {
        MediaMetadataCompat.Builder()
            .from(novelModel2)
            .apply {
                displayIconUri = novelModel.img // Used by ExoPlayer and Notification
                albumArtUri = novelModel.img
            }
            .build().apply {
                //将description填充数据
                description.extras?.putAll(bundle)
            }
    }

//    val


    /**
     * Load the supplied list of songs and the song to play into the current player.
     */
    private fun preparePlaylist(
        metadataList: List<MediaMetadataCompat>,//播放元数据列表
        itemToPlay: MediaMetadataCompat?,//即将要播放的Mediaitem 元数据
        playWhenReady: Boolean,//播放器准备耗资源后是否自动播放
        playbackStartPositionMs: Long//起始播放位置
    ) {
        // Since the playlist was probably based on some ordering (such as tracks
        // on an album), find which window index to play first so that the song the
        // user actually wants to hear plays first.
        val initialWindowIndex = if (itemToPlay == null) 0 else metadataList.indexOf(itemToPlay)
//        if (currentPlaylistItems.isEmpty()) {
//            currentPlaylistItems.clear()
//            currentPlaylistItems.addAll(metadataList)
//        }

        currentPlayer.playWhenReady = playWhenReady
        currentPlayer.stop(/* reset= */ true)
        if (currentPlayer == exoPlayer) {
            currentPlaylistItems.forEach {
                mediaSource.addMediaSource(it.toMediaSource(dataSourceFactory))
            }
            exoPlayer.prepare(mediaSource)
            exoPlayer.seekTo(initialWindowIndex, playbackStartPositionMs)
        }
    }

    val mySessionCallback = object  : MediaSessionCompat.Callback() {
        override fun onPlay() {
            super.onPlay()
            Log.d(TAG, "onPlay: ")
//            setupNotification()
            //preparePlaylist(listOf(mediaMetadataCompat), mediaMetadataCompat, true, 0)
        }

        override fun onStop() {
            super.onStop()
            Log.d(TAG, "onStop: ")
            currentPlayer.stop(false)
        }

        override fun onPause() {
            super.onPause()
            Log.d(TAG, "onPause: ")
            currentPlayer.stop(false)
        }

        override fun onSetPlaybackSpeed(speed: Float) {
            super.onSetPlaybackSpeed(speed)
            Log.d(TAG, "onSetPlaybackSpeed: speed-->${speed}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: ")
        currentPlayer = exoPlayer
        // Build a PendingIntent that can be used to launch the UI.
        val sessionActivityPendingIntent =
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                PendingIntent.getActivity(this, 0, sessionIntent, 0)
            }

        mediaSession = MediaSessionCompat(this, "ReaderAudioService").apply {
            setSessionActivity(sessionActivityPendingIntent)
            isActive = true
            // Enable callbacks from MediaButtons and TransportControls
//            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
//                    or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
//            )

            // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
            stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY
                            or PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            setPlaybackState(stateBuilder.build())

            // MySessionCallback() has methods that handle callbacks from a media controller
            //setCallback(mySessionCallback)
//            setSessionToken()
        }

        //set sessionToken
        sessionToken = mediaSession.sessionToken

        /**
         * The notification manager will use our player and media session to decide when to post
         * notifications. When notifications are posted or removed our listener will be called, this
         * allows us to promote the service to foreground (required so that we're not killed if
         * the main UI is not visible).
         */
        notificationManager = UampNotificationManager(
            this,
            mediaSession.sessionToken,
            PlayerNotificationListener()
        )
        // ExoPlayer will manage the MediaSession for us.
        mediaSessionConnector = MediaSessionConnector(mediaSession)
        mediaSessionConnector.setRewindIncrementMs(15000)
//        mediaSessionConnector.invalidateMediaSessionPlaybackState()
        mediaSessionConnector.setPlaybackPreparer(UampPlaybackPreparer())
        //将元数据注入到UI层
        mediaSessionConnector.setQueueNavigator(UampQueueNavigator(mediaSession))
        mediaSessionConnector.setPlayer(currentPlayer)

        notificationManager.showNotificationForPlayer(currentPlayer)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        currentPlayer.stop(true)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        Log.d(TAG, "onGetRoot: ")
        return BrowserRoot(MY_MEDIA_ROOT_ID, null)
    }

    override fun onCustomAction(action: String, extras: Bundle?, result: Result<Bundle>) {
        super.onCustomAction(action, extras, result)
        Log.d(TAG, "onCustomAction: action-->${action}")
        if (ACTION_PLAYBACK_SPEED == action) {
            extras?.run {
                val speed = getFloat(PLAYBACK_SPEED, 1f)
                exoPlayer.setPlaybackParameters(PlaybackParameters(speed))
            }
        }
        if (ACTION_ADD_MEDIA_ITEM == action) {
            extras?.run {
                val mediaItemList = getParcelableArrayList<ChapterAudioItem>(ACTION_ADD_MEDIA_ITEM)
                Log.d(TAG, "onPrepareFromMediaId: mediaItemList size ->>${mediaItemList?.size}")
                mediaItemList?.run {
                    val metadataList = createMetaDataList(this)
                    currentPlaylistItems.addAll(metadataList)
                    metadataList.forEach {
                        mediaSource.addMediaSource(it.toMediaSource(dataSourceFactory))
                    }
                }
                mediaItemList
            }
        }
    }

    //对于音乐播放器来说 这个方法是根据当前歌单的分类来加载当前歌单内的所有歌曲
    //对于小说阅读器来说 不能把整本书的章节播放信息加载进来 而且由于用户播放章节的位置不固定
    //需要通过client传递参数来告诉服务 从哪个进度开始进度开始播放
    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        Log.d(TAG, "onLoadChildren: ")
        result.detach()
        // Assume for example that the music catalog is already loaded/cached.

        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()

        Log.d(TAG, "onLoadChildren: mediaMetaData-->${mediaMetadataCompat}")
        Log.d(TAG, "onLoadChildren: mediaMetaData id-->${mediaMetadataCompat.id}")
        Log.d(TAG, "onLoadChildren: mediaMetaData title-->${mediaMetadataCompat.title}")
        Log.d(TAG, "onLoadChildren: mediaMetaData duration-->${mediaMetadataCompat.duration}")
        Log.d(TAG, "onLoadChildren: mediaMetaData album-->${mediaMetadataCompat.album}")
        // Check if this is the root menu:
        if (MY_MEDIA_ROOT_ID == parentId) {
            // Build the MediaItem objects for the top level,
            // and put them in the mediaItems list...
            mediaItems.add(
                MediaBrowserCompat.MediaItem(
                    mediaMetadataCompat.description,
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                )
            )
        } else {
            // Examine the passed parentMediaId to see which submenu we're at,
            // and put the children of that menu in the mediaItems list...
        }
        result.sendResult(mediaItems)
    }

    /**
     * Listen for events from ExoPlayer.
     */
    private inner class PlayerEventListener : Player.EventListener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            Log.d(TAG, "onPlayerStateChanged: state--> $playbackState")
            when (playbackState) {
                Player.STATE_BUFFERING,
                Player.STATE_READY -> {
                    // TODO: 2020/12/14 同步通知状态
                    notificationManager.showNotificationForPlayer(currentPlayer)
                    if (playbackState == Player.STATE_READY) {

                        if (!playWhenReady) {
                            // If playback is paused we remove the foreground state which allows the
                            // notification to be dismissed. An alternative would be to provide a
                            // "close" button in the notification which stops playback and clears
                            // the notification.
                            //设置notification可以被移除
//                            stopForeground(true)
                        }
                    }
                }
                else -> {
                    notificationManager.hideNotification()
                }
            }
        }

        //播放器player切换上一首或下一首的监听
        override fun onPositionDiscontinuity(reason: Int) {
            super.onPositionDiscontinuity(reason)
            //THIS METHOD GETS CALLED FOR EVERY NEW SOURCE THAT IS PLAYED
            val latestWindowIndex = exoPlayer.currentWindowIndex
            Log.d(TAG, "onPositionDiscontinuity: lastWindowIndex before--->$lastWindowIndex")
            if (latestWindowIndex != lastWindowIndex) {
                // item selected in playlist has changed, handle here
                lastWindowIndex = latestWindowIndex
                // ...
            }
            if (latestWindowIndex == (currentPlaylistItems.size-1)) {
                Log.d(TAG, "onPositionDiscontinuity: preloadNextMediaItem")
                preloadNextMediaItem(latestWindowIndex)
            }

            Log.d(TAG, "onPositionDiscontinuity: lastWindowIndex --->$lastWindowIndex")
            Log.d(TAG, "onPositionDiscontinuity: latestWindowIndex --->$latestWindowIndex")

        }
        override fun onPlayerError(error: ExoPlaybackException) {
            var message = R.string.generic_error;
            when (error.type) {
                // If the data from MediaSource object could not be loaded the Exoplayer raises
                // a type_source error.
                // An error message is printed to UI via Toast message to inform the user.
                ExoPlaybackException.TYPE_SOURCE -> {
                    message = R.string.error_media_not_found;
                    Log.e(TAG, "TYPE_SOURCE: " + error.sourceException.message)
                }
                // If the error occurs in a render component, Exoplayer raises a type_remote error.
                ExoPlaybackException.TYPE_RENDERER -> {
                    Log.e(TAG, "TYPE_RENDERER: " + error.rendererException.message)
                }
                // If occurs an unexpected RuntimeException Exoplayer raises a type_unexpected error.
                ExoPlaybackException.TYPE_UNEXPECTED -> {
                    Log.e(TAG, "TYPE_UNEXPECTED: " + error.unexpectedException.message)
                }
                // Occurs when there is a OutOfMemory error.
                ExoPlaybackException.TYPE_OUT_OF_MEMORY -> {
                    Log.e(TAG, "TYPE_OUT_OF_MEMORY: " + error.outOfMemoryError.message)
                }
                // If the error occurs in a remote component, Exoplayer raises a type_remote error.
                ExoPlaybackException.TYPE_REMOTE -> {
                    Log.e(TAG, "TYPE_REMOTE: " + error.message)
                }
            }
            Toast.makeText(
                applicationContext,
                message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun preloadNextMediaItem(latestWindowIndex: Int) {
        when(latestWindowIndex) {
            0 -> {
                prepareNextMediaItem(TestDataFactory.mediaItem2())
            }
            1 -> {
                prepareNextMediaItem(TestDataFactory.mediaItem3())
            }
            2 -> {
                prepareNextMediaItem(TestDataFactory.mediaItem4())
            }

        }
    }

    private fun prepareNextMediaItem(audioItem: ChapterAudioItem) {
        audioItem.run {
            val metaData = createMetaDataFromAudioItem(this)
            currentPlaylistItems.add(metaData)
            mediaSource.addMediaSource(metaData.toMediaSource(dataSourceFactory))
        }

    }

    /**
     * Listen for notification events.
     */
    private inner class PlayerNotificationListener :
        PlayerNotificationManager.NotificationListener {
        override fun onNotificationPosted(
            notificationId: Int,
            notification: Notification,
            ongoing: Boolean
        ) {
            if (ongoing && !isForegroundService) {
                ContextCompat.startForegroundService(
                    applicationContext,
                    Intent(applicationContext, this@ReaderAudioService.javaClass)
                )

                startForeground(notificationId, notification)
                isForegroundService = true
            }
        }

        override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
            //currentPlayer.release()
            exoPlayer.stop(false)
            stopForeground(true)
            isForegroundService = false
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.run {
            isActive = false
            release()
        }

        // Free ExoPlayer resources.
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }

    private fun setupNotification() {
        val controller = mediaSession.controller
        val mediaMetadata = controller.metadata
        val description = mediaMetadata.description

        val builder = NotificationCompat.Builder(this, "com.hyy.reader_audio_sample").apply {
            // Add the metadata for the currently playing track
            setContentTitle(description.title)
            setContentText(description.subtitle)
            setSubText(description.description)
            setLargeIcon(description.iconBitmap)

            // Enable launching the player by clicking the notification
            setContentIntent(controller.sessionActivity)

            // Stop the service when the notification is swiped away
            setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this@ReaderAudioService,
                    PlaybackStateCompat.ACTION_STOP
                )
            )

            // Make the transport controls visible on the lockscreen
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            // Add an app icon and set its accent color
            // Be careful about the color
            setSmallIcon(R.mipmap.ic_launcher)
            color = ContextCompat.getColor(applicationContext, R.color.purple_200)

            // Add a pause button
            addAction(
                NotificationCompat.Action(
                    android.R.drawable.presence_audio_away,
                    getString(R.string.pause),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        applicationContext,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                )
            )

            // Take advantage of MediaStyle features
            setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
                    // Add a cancel button
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            applicationContext,
                            PlaybackStateCompat.ACTION_STOP
                        )
                    )
            )
        }

        // Display the notification and place the service in the foreground
        startForeground(2020, builder.build())
    }

    private inner class UampQueueNavigator(
        mediaSession: MediaSessionCompat
    ) : TimelineQueueNavigator(mediaSession) {
        override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
            return currentPlaylistItems[windowIndex].description
        }
    }

    private inner class UampPlaybackPreparer : MediaSessionConnector.PlaybackPreparer {

        /**
         * UAMP supports preparing (and playing) from search, as well as media ID, so those
         * capabilities are declared here.
         *
         * TODO: Add support for ACTION_PREPARE and ACTION_PLAY, which mean "prepare/play something".
         */
        override fun getSupportedPrepareActions(): Long =
            PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
//                    PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH or
//                    PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH

        override fun onPrepare(playWhenReady: Boolean) {
            Log.d(TAG, "onPrepare: ")
        }

        override fun onPrepareFromMediaId(
            mediaId: String,
            playWhenReady: Boolean,
            extras: Bundle?
        ) {
            Log.d(TAG, "onPrepareFromMediaId: mediaId--> $mediaId")
            Log.d(TAG, "onPrepareFromMediaId: playWhenReady--> $playWhenReady")
            Log.d(TAG, "onPrepareFromMediaId: extras--> $extras")
//            val item = currentPlaylistItems.find { mediaId == it.description.mediaId }
            extras?.run {
                val mediaItem = getParcelable<ChapterAudioItem>(ACTION_ADD_MEDIA_ITEM)
                Log.d(TAG, "onPrepareFromMediaId: mediaItem id -->${mediaItem?.id}")
                mediaItem?.run {
                    val metadata = createMetaDataFromAudioItem(this)
                    val item = currentPlaylistItems.find { metadata.id == it.id }
                    if (item != null) {
                        exoPlayer.playWhenReady = true
                        exoPlayer.prepare(mediaSource)
                    }else {
                        currentPlaylistItems.add(metadata)
                        preparePlaylist(
                            metadataList = currentPlaylistItems,
                            metadata,
                            playWhenReady,
                            0
                        )
                    }
                }
            }

        }

        /**
         * This method is used by the Google Assistant to respond to requests such as:
         * - Play Geisha from Wake Up on UAMP
         * - Play electronic music on UAMP
         * - Play music on UAMP
         *
         * For details on how search is handled, see [AbstractMusicSource.search].
         */
        override fun onPrepareFromSearch(query: String, playWhenReady: Boolean, extras: Bundle?) {
        }

        override fun onPrepareFromUri(uri: Uri, playWhenReady: Boolean, extras: Bundle?) = Unit

        override fun onCommand(
            player: Player,
            controlDispatcher: ControlDispatcher,
            command: String,
            extras: Bundle?,
            cb: ResultReceiver?
        ) = false

        /**
         * Builds a playlist based on a [MediaMetadataCompat].
         *
         * TODO: Support building a playlist by artist, genre, etc...
         *
         * @param item Item to base the playlist on.
         * @return a [List] of [MediaMetadataCompat] objects representing a playlist.
         */
        private fun buildPlaylist(item: MediaMetadataCompat): List<MediaMetadataCompat> {
            return listOf(mediaMetadataCompat)
        }
    }

    private fun createMetaDataList(audioItems: ArrayList<ChapterAudioItem>): List<MediaMetadataCompat> {
        val metadataList = audioItems.map {
            Log.d(TAG, "createMetaDataList: id---> ${it.id}")
            MediaMetadataCompat.Builder()
                .from(it)
                .apply {
                    displayIconUri = novelModel.img // Used by ExoPlayer and Notification
                    albumArtUri = novelModel.img
                }
                .build()
        }
        metadataList.forEach {
            //将description填充数据
            it.description.extras?.putAll(it.bundle)
        }
        return metadataList
    }

    private fun createMetaDataFromAudioItem(audioItem: ChapterAudioItem): MediaMetadataCompat {
        val metaData = MediaMetadataCompat.Builder()
            .from(audioItem)
            .apply {
                displayIconUri = audioItem.img // Used by ExoPlayer and Notification
                albumArtUri = audioItem.img
            }
            .build()
        //将description填充数据
        metaData.description.extras?.putAll(metaData.bundle)
        return metaData
    }
}