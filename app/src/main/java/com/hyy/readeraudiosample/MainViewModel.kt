package com.hyy.readeraudiosample

import android.app.Application
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.android.uamp.media.extensions.currentPlayBackPosition
import com.example.android.uamp.media.extensions.isPlayEnabled
import com.example.android.uamp.media.extensions.isPlaying
import com.example.android.uamp.media.extensions.isPrepared
import com.hyy.readeraudiosample.model.ChapterAudioItem
import com.hyy.readeraudiosample.model.NowPlayingMetadata

/**
 *Create by hyy on 2020/12/14
 */
class MainViewModel(
    private val app: Application,
    private val musicServiceConnection: MusicServiceConnection
) : ViewModel(){

    companion object {
        const val TAG = "MainViewModel"
        const val POSITION_UPDATE_INTERVAL_MILLIS = 100L
    }

    private var preloadDone: Boolean = false
    private var updatePosition: Boolean = true

    //默认播放状态
    private var playbackState: PlaybackStateCompat = EMPTY_PLAYBACK_STATE
    //当前播放位置
    val playbackPosition = MutableLiveData<Long>().apply {
        postValue(0L)
    }
    //当前播放元数据
    val mediaMetadata = MutableLiveData<NowPlayingMetadata>()
    val mediaButtonRes = MutableLiveData<Int>().apply {
        postValue(R.drawable.ic_player_start)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val subscriptionCallback = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(
            parentId: String,
            children: List<MediaBrowserCompat.MediaItem>
        ) {
            Log.d(TAG, "onChildrenLoaded: ")
            //TODO 根据service端的mediaItem来设置UI
            children.forEach { child->
                Log.d(TAG, "onChildrenLoaded: mediaId--> ${child.mediaId}")
                Log.d(TAG, "onChildrenLoaded: description.title--> ${child.description.title}")
                Log.d(TAG, "onChildrenLoaded: description.mediaId--> ${child.description.mediaId}")
            }
//            val itemsList = children.map { child ->
//                val subtitle = child.description.subtitle ?: ""
//            }
//            _mediaItems.postValue(itemsList)
        }
    }

    /**
     * When the session's [PlaybackStateCompat] changes, the [mediaItems] need to be updated
     * so the correct [MediaItemData.playbackRes] is displayed on the active item.
     * (i.e.: play/pause button or blank)
     * 播放状态监听
     */
    private val playbackStateObserver = Observer<PlaybackStateCompat> {
        playbackState = it ?: EMPTY_PLAYBACK_STATE
        val metadata = musicServiceConnection.nowPlaying.value ?: NOTHING_PLAYING
        updateState(playbackState, metadata)
    }

    /**
     * When the session's [MediaMetadataCompat] changes, the [mediaItems] need to be updated
     * as it means the currently active item has changed. As a result, the new, and potentially
     * old item (if there was one), both need to have their [MediaItemData.playbackRes]
     * changed. (i.e.: play/pause button or blank)
     * 播放的元数据监听 （播放元数据可以认为是一首音乐数据）
     */
    private val mediaMetadataObserver = Observer<MediaMetadataCompat> {
        updateState(playbackState, it)
    }

    init {
        initConnection()
    }

    private fun initConnection() {
        musicServiceConnection.run {
            playbackState.observeForever(playbackStateObserver)
            nowPlaying.observeForever(mediaMetadataObserver)
            //subscribe(MY_MEDIA_ROOT_ID, subscriptionCallback)
            checkPlaybackPosition()
        }
    }

    /**
     * Internal function that recursively calls itself every [POSITION_UPDATE_INTERVAL_MILLIS] ms
     * to check the current playback position and updates the corresponding LiveData object when it
     * has changed.
     */
    private fun checkPlaybackPosition(): Boolean = handler.postDelayed({
        val currPosition = playbackState.currentPlayBackPosition
        if (playbackPosition.value != currPosition)
            playbackPosition.postValue(currPosition)
        if (updatePosition)
            checkPlaybackPosition()
    }, POSITION_UPDATE_INTERVAL_MILLIS)

    private fun updateState(
        playbackState: PlaybackStateCompat,
        mediaMetadata: MediaMetadataCompat
    ) {

        // Only update media item once we have duration available
        Log.d(TAG, "updateState: mediaMetadata.id-->${mediaMetadata.id}")
        Log.d(TAG, "updateState: mediaMetadata.duration-->${mediaMetadata.duration}")
        Log.d(TAG, "updateState: mediaMetadata.title-->${mediaMetadata.title}")
        Log.d(TAG, "updateState: mediaMetadata.albumArtUri-->${mediaMetadata.albumArtUri}")
        Log.d(TAG, "updateState: mediaMetadata.displaySubtitle-->${mediaMetadata.displaySubtitle}")
        if (mediaMetadata.duration != 0L && mediaMetadata.id != null) {
            val nowPlayingMetadata = NowPlayingMetadata(
                mediaMetadata.id!!,
                mediaMetadata.albumArtUri,
                mediaMetadata.title?.trim(),
                mediaMetadata.displaySubtitle?.trim(),
                NowPlayingMetadata.timestampToMSS(app, mediaMetadata.duration),
                mediaMetadata.duration.toSeconds
            )
            this.mediaMetadata.postValue(nowPlayingMetadata)
        }

        if (playbackState.state == PlaybackStateCompat.STATE_STOPPED) {
            //addMediaItem()
        }

        if (mediaMetadata.id == "18890" && playbackState.state == PlaybackStateCompat.STATE_PLAYING) {
            if (preloadDone.not()) {
                Toast.makeText(app, "开始预加载下个内容", Toast.LENGTH_LONG).show()
//                addMediaItem()
                preloadDone =true
            }
        }

        // Update the media button resource ID
        mediaButtonRes.postValue(
            when (playbackState.isPlaying) {
                true -> R.drawable.ic_player_pause
                else -> R.drawable.ic_player_start
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        musicServiceConnection.unsubscribe(MY_MEDIA_ROOT_ID, subscriptionCallback)

        // Remove the permanent observers from the MusicServiceConnection.
        musicServiceConnection.playbackState.removeObserver(playbackStateObserver)
        musicServiceConnection.nowPlaying.removeObserver(mediaMetadataObserver)

        // Stop updating the position
        updatePosition = false
    }

    fun playMediaId() {
        val nowPlaying = musicServiceConnection.nowPlaying.value
        val transportControls = musicServiceConnection.transportControls

        Log.d(TAG, "playMediaId:id---> ${nowPlaying?.id}")
        val isPrepared = musicServiceConnection.playbackState.value?.isPrepared ?: false
        Log.d(TAG, "playMediaId: isPrepared-->${isPrepared}")

//        if (isPrepared && mediaId == nowPlaying?.id) {
        if (isPrepared) {
            musicServiceConnection.playbackState.value?.let { playbackState ->
                when {
                    playbackState.isPlaying -> transportControls.pause()
                    playbackState.isPlayEnabled -> {
                        transportControls.play()
                    }
                    else -> {
                        Log.w(
                            TAG, "Playable item clicked but neither play nor pause are enabled!"
                            // " (mediaId=$mediaId)"
                        )
                    }
                }
            }
        } else {
            val bundle = bundleOf()
            bundle.putParcelable(ACTION_ADD_MEDIA_ITEM, TestDataFactory.mediaItem1())
            transportControls.playFromMediaId(TestDataFactory.mediaItem1().id, bundle)
        }
    }

    fun fastForward() {
        musicServiceConnection.transportControls.fastForward()
    }

    fun fastRewind() {
        musicServiceConnection.transportControls.rewind()
    }

    fun seekToPosition(pos: Int) {
        musicServiceConnection.transportControls.seekTo(pos*1000L)
    }

    fun changePlaybackRate(rate: Float) {
        Log.d(TAG, "changePlaybackRate: rate-->$rate")
//        musicServiceConnection.transportControls.setPlaybackSpeed(rate)
        musicServiceConnection.setCustomAction(ACTION_PLAYBACK_SPEED, bundleOf(PLAYBACK_SPEED to rate))
    }

    fun addMediaItem() {
        //可以通过这里预加载下个章节的音频文件 通过过bundle 吧
        val bundle = bundleOf()
        bundle.putParcelableArrayList(ACTION_ADD_MEDIA_ITEM, createTestChapterAudioItem2())
        musicServiceConnection.setCustomAction(ACTION_ADD_MEDIA_ITEM, bundle)
        //musicServiceConnection.transportControls.prepareFromMediaId()
        //musicServiceConnection.transportControls.
    }

    fun createTestChapterAudioItem1() : ArrayList<ChapterAudioItem>{
        return arrayListOf(TestDataFactory.mediaItem1(), TestDataFactory.mediaItem2(), TestDataFactory.mediaItem3())
    }

    fun createTestChapterAudioItem2() : ArrayList<ChapterAudioItem>{
        return arrayListOf(TestDataFactory.mediaItem4(), TestDataFactory.mediaItem5(), TestDataFactory.mediaItem6())
    }

    internal class Factory(
        private val app: Application,
        private val musicServiceConnection: MusicServiceConnection
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
              return MainViewModel(app, musicServiceConnection) as T
            }
            throw IllegalStateException("not a valid class")
        }
    }
}