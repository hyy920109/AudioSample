/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hyy.readeraudiosample;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.media.session.MediaSessionCompat;
import android.widget.RemoteViews;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.NotificationUtil;
import com.google.android.exoplayer2.util.Util;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerNotificationManager {

    public interface MediaDescriptionAdapter {
        CharSequence getCurrentContentTitle(Player player);

        @Nullable
        PendingIntent createCurrentContentIntent(Player player);
        @Nullable
        CharSequence getCurrentContentText(Player player);

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Nullable
        default CharSequence getCurrentSubText(Player player) {
            return null;
        }
        @Nullable
        Bitmap getCurrentLargeIcon(Player player, BitmapCallback callback);
    }
    public interface CustomActionReceiver {
        Map<String, NotificationCompat.Action> createCustomActions(Context context, int instanceId);
        List<String> getCustomActions(Player player);
        void onCustomAction(Player player, String action, Intent intent);
    }
    public interface NotificationListener {
        @Deprecated
        default void onNotificationStarted(int notificationId, Notification notification) {}
        @Deprecated
        default void onNotificationCancelled(int notificationId) {}
        default void onNotificationCancelled(int notificationId, boolean dismissedByUser) {}
        default void onNotificationPosted(
                int notificationId, Notification notification, boolean ongoing) {}
    }

    /** Receives a {@link Bitmap}. */
    public final class BitmapCallback {
        private final int notificationTag;

        /** Create the receiver. */
        private BitmapCallback(int notificationTag) {
            this.notificationTag = notificationTag;
        }

        /**
         * Called when {@link Bitmap} is available.
         *
         * @param bitmap The bitmap to use as the large icon of the notification.
         */
        public void onBitmap(final Bitmap bitmap) {
            if (bitmap != null) {
                postUpdateNotificationBitmap(bitmap, notificationTag);
            }
        }
    }

    /** The action which starts playback. */
    public static final String ACTION_PLAY = "com.google.android.exoplayer.play";
    /** The action which pauses playback. */
    public static final String ACTION_PAUSE = "com.google.android.exoplayer.pause";
    /** The action which skips to the previous window. */
    public static final String ACTION_PREVIOUS = "com.google.android.exoplayer.prev";
    /** The action which skips to the next window. */
    public static final String ACTION_NEXT = "com.google.android.exoplayer.next";
    /** The action which fast forwards. */
    public static final String ACTION_FAST_FORWARD = "com.google.android.exoplayer.ffwd";
    /** The action which rewinds. */
    public static final String ACTION_REWIND = "com.google.android.exoplayer.rewind";
    /** The action which stops playback. */
    public static final String ACTION_STOP = "com.google.android.exoplayer.stop";
    /** The extra key of the instance id of the player notification manager. */
    public static final String EXTRA_INSTANCE_ID = "INSTANCE_ID";
    /**
     * The action which is executed when the notification is dismissed. It cancels the notification
     * and calls {@link NotificationListener#onNotificationCancelled(int, boolean)}.
     */
    private static final String ACTION_DISMISS = "com.google.android.exoplayer.dismiss";

    // Internal messages.

    private static final int MSG_START_OR_UPDATE_NOTIFICATION = 0;
    private static final int MSG_UPDATE_NOTIFICATION_BITMAP = 1;

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            NotificationCompat.PRIORITY_DEFAULT,
            NotificationCompat.PRIORITY_MAX,
            NotificationCompat.PRIORITY_HIGH,
            NotificationCompat.PRIORITY_LOW,
            NotificationCompat.PRIORITY_MIN
    })
    public @interface Priority {}

    /** The default fast forward increment, in milliseconds. */
    public static final int DEFAULT_FAST_FORWARD_MS = 15000;
    /** The default rewind increment, in milliseconds. */
    public static final int DEFAULT_REWIND_MS = 5000;

    private static final long MAX_POSITION_FOR_SEEK_TO_PREVIOUS = 3000;

    private static int instanceIdCounter;

    private final Context context;
    private final String channelId;
    private final int notificationId;
    private final MediaDescriptionAdapter mediaDescriptionAdapter;
    @Nullable private final CustomActionReceiver customActionReceiver;
    private final Handler mainHandler;
    private final NotificationManagerCompat notificationManager;
    private final IntentFilter intentFilter;
    private final Player.EventListener playerListener;
    private final NotificationBroadcastReceiver notificationBroadcastReceiver;
    private final Map<String, NotificationCompat.Action> playbackActions;
    private final Map<String, NotificationCompat.Action> customActions;
    private final PendingIntent dismissPendingIntent;
    private final int instanceId;
    private final Timeline.Window window;

    @Nullable private NotificationCompat.Builder builder;
    @Nullable private ArrayList<NotificationCompat.Action> builderActions;
    @Nullable private Player player;
    @Nullable private PlaybackPreparer playbackPreparer;
    private ControlDispatcher controlDispatcher;
    private boolean isNotificationStarted;
    private int currentNotificationTag;
    @Nullable private NotificationListener notificationListener;
    @Nullable private MediaSessionCompat.Token mediaSessionToken;
    private boolean useNavigationActions;
    private boolean useNavigationActionsInCompactView;
    private boolean usePlayPauseActions;
    private boolean useStopAction;
    private long fastForwardMs;
    private long rewindMs;
    private int badgeIconType;
    private boolean colorized;
    private int defaults;
    private int color;
    @DrawableRes private int smallIconResourceId;
    private int visibility;
    @Priority private int priority;
    private boolean useChronometer;

    /**
     * @deprecated Use {@link #createWithNotificationChannel(Context, String, int, int, int,
     *     MediaDescriptionAdapter)}.
     */
    @Deprecated
    public static PlayerNotificationManager createWithNotificationChannel(
            Context context,
            String channelId,
            @StringRes int channelName,
            int notificationId,
            MediaDescriptionAdapter mediaDescriptionAdapter) {
        return createWithNotificationChannel(
                context,
                channelId,
                channelName,
                /* channelDescription= */ 0,
                notificationId,
                mediaDescriptionAdapter);
    }

    public static PlayerNotificationManager createWithNotificationChannel(
            Context context,
            String channelId,
            @StringRes int channelName,
            @StringRes int channelDescription,
            int notificationId,
            MediaDescriptionAdapter mediaDescriptionAdapter) {
        NotificationUtil.createNotificationChannel(
                context, channelId, channelName, channelDescription, NotificationUtil.IMPORTANCE_LOW);
        return new PlayerNotificationManager(
                context, channelId, notificationId, mediaDescriptionAdapter);
    }

    @Deprecated
    public static PlayerNotificationManager createWithNotificationChannel(
            Context context,
            String channelId,
            @StringRes int channelName,
            int notificationId,
            MediaDescriptionAdapter mediaDescriptionAdapter,
            @Nullable NotificationListener notificationListener) {
        return createWithNotificationChannel(
                context,
                channelId,
                channelName,
                /* channelDescription= */ 0,
                notificationId,
                mediaDescriptionAdapter,
                notificationListener);
    }

    public static PlayerNotificationManager createWithNotificationChannel(
            Context context,
            String channelId,
            @StringRes int channelName,
            @StringRes int channelDescription,
            int notificationId,
            MediaDescriptionAdapter mediaDescriptionAdapter,
            @Nullable NotificationListener notificationListener) {
        NotificationUtil.createNotificationChannel(
                context, channelId, channelName, channelDescription, NotificationUtil.IMPORTANCE_LOW);
        return new PlayerNotificationManager(
                context, channelId, notificationId, mediaDescriptionAdapter, notificationListener);
    }

    public PlayerNotificationManager(
            Context context,
            String channelId,
            int notificationId,
            MediaDescriptionAdapter mediaDescriptionAdapter) {
        this(
                context,
                channelId,
                notificationId,
                mediaDescriptionAdapter,
                /* notificationListener= */ null,
                /* customActionReceiver */ null);
    }

    public PlayerNotificationManager(
            Context context,
            String channelId,
            int notificationId,
            MediaDescriptionAdapter mediaDescriptionAdapter,
            @Nullable NotificationListener notificationListener) {
        this(
                context,
                channelId,
                notificationId,
                mediaDescriptionAdapter,
                notificationListener,
                /* customActionReceiver*/ null);
    }

    public PlayerNotificationManager(
            Context context,
            String channelId,
            int notificationId,
            MediaDescriptionAdapter mediaDescriptionAdapter,
            @Nullable NotificationListener notificationListener,
            @Nullable CustomActionReceiver customActionReceiver) {
        context = context.getApplicationContext();
        this.context = context;
        this.channelId = channelId;
        this.notificationId = notificationId;
        this.mediaDescriptionAdapter = mediaDescriptionAdapter;
        this.notificationListener = notificationListener;
        this.customActionReceiver = customActionReceiver;
        controlDispatcher = new DefaultControlDispatcher();
        window = new Timeline.Window();
        instanceId = instanceIdCounter++;
        //noinspection Convert2MethodRef
        mainHandler =
                Util.createHandler(
                        Looper.getMainLooper(), msg -> PlayerNotificationManager.this.handleMessage(msg));
        notificationManager = NotificationManagerCompat.from(context);
        playerListener = new PlayerListener();
        notificationBroadcastReceiver = new NotificationBroadcastReceiver();
        intentFilter = new IntentFilter();
        useNavigationActions = true;
        usePlayPauseActions = true;
        colorized = true;
        useChronometer = true;
        color = Color.TRANSPARENT;
        smallIconResourceId = R.drawable.exo_notification_small_icon;
        defaults = 0;
        priority = NotificationCompat.PRIORITY_HIGH;
        fastForwardMs = DEFAULT_FAST_FORWARD_MS;
        rewindMs = DEFAULT_REWIND_MS;
        badgeIconType = NotificationCompat.BADGE_ICON_SMALL;
        visibility = NotificationCompat.VISIBILITY_PUBLIC;

        // initialize actions
        playbackActions = createPlaybackActions(context, instanceId);
        for (String action : playbackActions.keySet()) {
            intentFilter.addAction(action);
        }
        customActions =
                customActionReceiver != null
                        ? customActionReceiver.createCustomActions(context, instanceId)
                        : Collections.emptyMap();
        for (String action : customActions.keySet()) {
            intentFilter.addAction(action);
        }
        dismissPendingIntent = createBroadcastIntent(ACTION_DISMISS, context, instanceId);
        intentFilter.addAction(ACTION_DISMISS);
    }

    public final void setPlayer(@Nullable Player player) {
        Assertions.checkState(Looper.myLooper() == Looper.getMainLooper());
        Assertions.checkArgument(
                player == null || player.getApplicationLooper() == Looper.getMainLooper());
        if (this.player == player) {
            return;
        }
        if (this.player != null) {
            this.player.removeListener(playerListener);
            if (player == null) {
                stopNotification(/* dismissedByUser= */ false);
            }
        }
        this.player = player;
        if (player != null) {
            player.addListener(playerListener);
            postStartOrUpdateNotification();
        }
    }

    @Deprecated
    public final void setNotificationListener(NotificationListener notificationListener) {
        this.notificationListener = notificationListener;
    }

    public final void setFastForwardIncrementMs(long fastForwardMs) {
        if (this.fastForwardMs == fastForwardMs) {
            return;
        }
        this.fastForwardMs = fastForwardMs;
        invalidate();
    }

    public final void setRewindIncrementMs(long rewindMs) {
        if (this.rewindMs == rewindMs) {
            return;
        }
        this.rewindMs = rewindMs;
        invalidate();
    }

    public final void setMediaSessionToken(MediaSessionCompat.Token token) {
        if (!Util.areEqual(this.mediaSessionToken, token)) {
            mediaSessionToken = token;
            invalidate();
        }
    }

    public final void setSmallIcon(@DrawableRes int smallIconResourceId) {
        if (this.smallIconResourceId != smallIconResourceId) {
            this.smallIconResourceId = smallIconResourceId;
            invalidate();
        }
    }

    /** Forces an update of the notification if already started. */
    public void invalidate() {
        if (isNotificationStarted) {
            postStartOrUpdateNotification();
        }
    }

    private void startOrUpdateNotification(Player player, @Nullable Bitmap bitmap) {
        boolean ongoing = getOngoing(player);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder = createNotification(player, builder, ongoing, bitmap);
        }
        if (builder == null) {
            stopNotification(/* dismissedByUser= */ false);
            return;
        }
        Notification notification = builder.build();
        notificationManager.notify(notificationId, notification);
        if (!isNotificationStarted) {
            isNotificationStarted = true;
            context.registerReceiver(notificationBroadcastReceiver, intentFilter);
            if (notificationListener != null) {
                notificationListener.onNotificationStarted(notificationId, notification);
            }
        }
        @Nullable NotificationListener listener = notificationListener;
        if (listener != null) {
            listener.onNotificationPosted(notificationId, notification, ongoing);
        }
    }

    private void stopNotification(boolean dismissedByUser) {
        if (isNotificationStarted) {
            isNotificationStarted = false;
            mainHandler.removeMessages(MSG_START_OR_UPDATE_NOTIFICATION);
            notificationManager.cancel(notificationId);
            context.unregisterReceiver(notificationBroadcastReceiver);
            if (notificationListener != null) {
                notificationListener.onNotificationCancelled(notificationId, dismissedByUser);
                notificationListener.onNotificationCancelled(notificationId);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @SuppressWarnings("nullness:argument.type.incompatible")
    @Nullable
    protected NotificationCompat.Builder createNotification(
            Player player,
            @Nullable NotificationCompat.Builder builder,
            boolean ongoing,
            @Nullable Bitmap largeIcon) {
        if (player.getPlaybackState() == Player.STATE_IDLE
                && (player.getCurrentTimeline().isEmpty() || playbackPreparer == null)) {
            builderActions = null;
            return null;
        }

        List<String> actionNames = getActions(player);
        ArrayList<NotificationCompat.Action> actions = new ArrayList<>(actionNames.size());
        for (int i = 0; i < actionNames.size(); i++) {
            String actionName = actionNames.get(i);
            NotificationCompat.Action action =
                    playbackActions.containsKey(actionName)
                            ? playbackActions.get(actionName)
                            : customActions.get(actionName);
            if (action != null) {
                actions.add(action);
            }
        }

        if (builder == null || !actions.equals(builderActions)) {
            builder = new NotificationCompat.Builder(context, channelId);
            builderActions = actions;
            for (int i = 0; i < actions.size(); i++) {
                builder.addAction(actions.get(i));
            }
        }

        MediaStyle mediaStyle = new MediaStyle();
        if (mediaSessionToken != null) {
            mediaStyle.setMediaSession(mediaSessionToken);
        }
        mediaStyle.setShowActionsInCompactView(getActionIndicesForCompactView(actionNames, player));
        // Configure dismiss action prior to API 21 ('x' button).
        mediaStyle.setShowCancelButton(!ongoing);
        mediaStyle.setCancelButtonIntent(dismissPendingIntent);
//    builder.setStyle(mediaStyle);

        // Set intent which is sent if the user selects 'clear all'
        builder.setDeleteIntent(dismissPendingIntent);

        RemoteViews remoteViews = new RemoteViews(context.getPackageName(),R.layout.notification_small);
        remoteViews.setTextViewText(R.id.title,mediaDescriptionAdapter.getCurrentContentTitle(player));
        remoteViews.setTextViewText(R.id.content,mediaDescriptionAdapter.getCurrentContentText(player));
        // Set notification properties from getters.
        builder
                .setCustomContentView(remoteViews)
                .setCustomBigContentView(remoteViews)
                .setBadgeIconType(badgeIconType)
                .setOngoing(ongoing)
                .setColor(color)
                .setColorized(colorized)
                .setSmallIcon(smallIconResourceId)
                .setVisibility(visibility)
                .setPriority(priority)
                .setDefaults(defaults);

        // Changing "showWhen" causes notification flicker if SDK_INT < 21.
        if (Util.SDK_INT >= 21
                && useChronometer
                && player.isPlaying()
                && !player.isPlayingAd()
                && !player.isCurrentWindowDynamic()
                && player.getPlaybackParameters().speed == 1f) {
            builder
                    .setWhen(System.currentTimeMillis() - player.getContentPosition())
                    .setShowWhen(true)
                    .setUsesChronometer(true);
        } else {
            builder.setShowWhen(false).setUsesChronometer(false);
        }

        // Set media specific notification properties from MediaDescriptionAdapter.
//        builder.setContentTitle("大王子");
//        builder.setContentText(mediaDescriptionAdapter.getCurrentContentText(player));
//        builder.setSubText(mediaDescriptionAdapter.getCurrentSubText(player));
        if (largeIcon == null) {
            largeIcon =
                    mediaDescriptionAdapter.getCurrentLargeIcon(
                            player, new BitmapCallback(++currentNotificationTag));
        }
        remoteViews.setImageViewBitmap(R.id.image,largeIcon);
        remoteViews.setImageViewResource(R.id.play, player.isPlaying()?R.drawable.ic_player_pause:R.drawable.ic_player_start);
        remoteViews.setOnClickPendingIntent(R.id.play,player.isPlaying()?createBroadcastIntent(ACTION_PAUSE, context, instanceId):createBroadcastIntent(ACTION_PLAY, context, instanceId));
        remoteViews.setOnClickPendingIntent(R.id.close,createBroadcastIntent(ACTION_DISMISS, context, instanceId));
//    setLargeIcon(builder, largeIcon);
        builder.setContentIntent(mediaDescriptionAdapter.createCurrentContentIntent(player));

        return builder;
    }

    protected List<String> getActions(Player player) {
        boolean enablePrevious = false;
        boolean enableRewind = false;
        boolean enableFastForward = false;
        boolean enableNext = false;
        Timeline timeline = player.getCurrentTimeline();
        if (!timeline.isEmpty() && !player.isPlayingAd()) {
            timeline.getWindow(player.getCurrentWindowIndex(), window);
            enablePrevious = window.isSeekable || !window.isDynamic || player.hasPrevious();
            enableRewind = rewindMs > 0;
            enableFastForward = fastForwardMs > 0;
            enableNext = window.isDynamic || player.hasNext();
        }

        List<String> stringActions = new ArrayList<>();
        if (useNavigationActions && enablePrevious) {
            stringActions.add(ACTION_PREVIOUS);
        }
        if (enableRewind) {
            stringActions.add(ACTION_REWIND);
        }
        if (usePlayPauseActions) {
            if (shouldShowPauseButton(player)) {
                stringActions.add(ACTION_PAUSE);
            } else {
                stringActions.add(ACTION_PLAY);
            }
        }
        if (enableFastForward) {
            stringActions.add(ACTION_FAST_FORWARD);
        }
        if (useNavigationActions && enableNext) {
            stringActions.add(ACTION_NEXT);
        }
        if (customActionReceiver != null) {
            stringActions.addAll(customActionReceiver.getCustomActions(player));
        }
        if (useStopAction) {
            stringActions.add(ACTION_STOP);
        }
        return stringActions;
    }

    @SuppressWarnings("unused")
    protected int[] getActionIndicesForCompactView(List<String> actionNames, Player player) {
        int pauseActionIndex = actionNames.indexOf(ACTION_PAUSE);
        int playActionIndex = actionNames.indexOf(ACTION_PLAY);
        int skipPreviousActionIndex =
                useNavigationActionsInCompactView ? actionNames.indexOf(ACTION_PREVIOUS) : -1;
        int skipNextActionIndex =
                useNavigationActionsInCompactView ? actionNames.indexOf(ACTION_NEXT) : -1;

        int[] actionIndices = new int[3];
        int actionCounter = 0;
        if (skipPreviousActionIndex != -1) {
            actionIndices[actionCounter++] = skipPreviousActionIndex;
        }
        boolean shouldShowPauseButton = shouldShowPauseButton(player);
        if (pauseActionIndex != -1 && shouldShowPauseButton) {
            actionIndices[actionCounter++] = pauseActionIndex;
        } else if (playActionIndex != -1 && !shouldShowPauseButton) {
            actionIndices[actionCounter++] = playActionIndex;
        }
        if (skipNextActionIndex != -1) {
            actionIndices[actionCounter++] = skipNextActionIndex;
        }
        return Arrays.copyOf(actionIndices, actionCounter);
    }

    /** Returns whether the generated notification should be ongoing. */
    protected boolean getOngoing(Player player) {
        int playbackState = player.getPlaybackState();
        return (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY)
                && player.getPlayWhenReady();
    }

    private void previous(Player player) {
        Timeline timeline = player.getCurrentTimeline();
        if (timeline.isEmpty() || player.isPlayingAd()) {
            return;
        }
        int windowIndex = player.getCurrentWindowIndex();
        timeline.getWindow(windowIndex, window);
        int previousWindowIndex = player.getPreviousWindowIndex();
        if (previousWindowIndex != C.INDEX_UNSET
                && (player.getCurrentPosition() <= MAX_POSITION_FOR_SEEK_TO_PREVIOUS
                || (window.isDynamic && !window.isSeekable))) {
            seekTo(player, previousWindowIndex, C.TIME_UNSET);
        } else {
            seekTo(player, windowIndex, /* positionMs= */ 0);
        }
    }

    private void next(Player player) {
        Timeline timeline = player.getCurrentTimeline();
        if (timeline.isEmpty() || player.isPlayingAd()) {
            return;
        }
        int windowIndex = player.getCurrentWindowIndex();
        int nextWindowIndex = player.getNextWindowIndex();
        if (nextWindowIndex != C.INDEX_UNSET) {
            seekTo(player, nextWindowIndex, C.TIME_UNSET);
        } else if (timeline.getWindow(windowIndex, window).isDynamic) {
            seekTo(player, windowIndex, C.TIME_UNSET);
        }
    }

    private void rewind(Player player) {
        if (player.isCurrentWindowSeekable() && rewindMs > 0) {
            seekToOffset(player, /* offsetMs= */ -rewindMs);
        }
    }

    private void fastForward(Player player) {
        if (player.isCurrentWindowSeekable() && fastForwardMs > 0) {
            seekToOffset(player, /* offsetMs= */ fastForwardMs);
        }
    }

    private void seekToOffset(Player player, long offsetMs) {
        long positionMs = player.getCurrentPosition() + offsetMs;
        long durationMs = player.getDuration();
        if (durationMs != C.TIME_UNSET) {
            positionMs = Math.min(positionMs, durationMs);
        }
        positionMs = Math.max(positionMs, 0);
        seekTo(player, player.getCurrentWindowIndex(), positionMs);
    }

    private void seekTo(Player player, int windowIndex, long positionMs) {
        controlDispatcher.dispatchSeekTo(player, windowIndex, positionMs);
    }

    private boolean shouldShowPauseButton(Player player) {
        return player.getPlaybackState() != Player.STATE_ENDED
                && player.getPlaybackState() != Player.STATE_IDLE
                && player.getPlayWhenReady();
    }

    private void postStartOrUpdateNotification() {
        if (!mainHandler.hasMessages(MSG_START_OR_UPDATE_NOTIFICATION)) {
            mainHandler.sendEmptyMessage(MSG_START_OR_UPDATE_NOTIFICATION);
        }
    }

    private void postUpdateNotificationBitmap(Bitmap bitmap, int notificationTag) {
        mainHandler
                .obtainMessage(
                        MSG_UPDATE_NOTIFICATION_BITMAP, notificationTag, C.INDEX_UNSET /* ignored */, bitmap)
                .sendToTarget();
    }

    private boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_START_OR_UPDATE_NOTIFICATION:
                if (player != null) {
                    startOrUpdateNotification(player, /* bitmap= */ null);
                }
                break;
            case MSG_UPDATE_NOTIFICATION_BITMAP:
                if (player != null && isNotificationStarted && currentNotificationTag == msg.arg1) {
                    startOrUpdateNotification(player, (Bitmap) msg.obj);
                }
                break;
            default:
                return false;
        }
        return true;
    }

    private static Map<String, NotificationCompat.Action> createPlaybackActions(
            Context context, int instanceId) {
        Map<String, NotificationCompat.Action> actions = new HashMap<>();
        actions.put(
                ACTION_PLAY,
                new NotificationCompat.Action(
                        R.drawable.exo_notification_play,
                        context.getString(R.string.exo_controls_play_description),
                        createBroadcastIntent(ACTION_PLAY, context, instanceId)));
        actions.put(
                ACTION_PAUSE,
                new NotificationCompat.Action(
                        R.drawable.exo_notification_pause,
                        context.getString(R.string.exo_controls_pause_description),
                        createBroadcastIntent(ACTION_PAUSE, context, instanceId)));
        actions.put(
                ACTION_STOP,
                new NotificationCompat.Action(
                        R.drawable.exo_notification_stop,
                        context.getString(R.string.exo_controls_stop_description),
                        createBroadcastIntent(ACTION_STOP, context, instanceId)));
        actions.put(
                ACTION_REWIND,
                new NotificationCompat.Action(
                        R.drawable.exo_notification_rewind,
                        context.getString(R.string.exo_controls_rewind_description),
                        createBroadcastIntent(ACTION_REWIND, context, instanceId)));
        actions.put(
                ACTION_FAST_FORWARD,
                new NotificationCompat.Action(
                        R.drawable.exo_notification_fastforward,
                        context.getString(R.string.exo_controls_fastforward_description),
                        createBroadcastIntent(ACTION_FAST_FORWARD, context, instanceId)));
        actions.put(
                ACTION_PREVIOUS,
                new NotificationCompat.Action(
                        R.drawable.exo_notification_previous,
                        context.getString(R.string.exo_controls_previous_description),
                        createBroadcastIntent(ACTION_PREVIOUS, context, instanceId)));
        actions.put(
                ACTION_NEXT,
                new NotificationCompat.Action(
                        R.drawable.exo_notification_next,
                        context.getString(R.string.exo_controls_next_description),
                        createBroadcastIntent(ACTION_NEXT, context, instanceId)));
        return actions;
    }

    private static PendingIntent createBroadcastIntent(
            String action, Context context, int instanceId) {
        Intent intent = new Intent(action).setPackage(context.getPackageName());
        intent.putExtra(EXTRA_INSTANCE_ID, instanceId);
        return PendingIntent.getBroadcast(
                context, instanceId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @SuppressWarnings("nullness:argument.type.incompatible")
    private static void setLargeIcon(NotificationCompat.Builder builder, @Nullable Bitmap largeIcon) {
        builder.setLargeIcon(largeIcon);
    }

    private class PlayerListener implements Player.EventListener {

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, @Player.State int playbackState) {
            postStartOrUpdateNotification();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            postStartOrUpdateNotification();
        }

        @Override
        public void onTimelineChanged(Timeline timeline, int reason) {
            postStartOrUpdateNotification();
        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            postStartOrUpdateNotification();
        }

        @Override
        public void onPositionDiscontinuity(int reason) {
            postStartOrUpdateNotification();
        }

        @Override
        public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
            postStartOrUpdateNotification();
        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            postStartOrUpdateNotification();
        }
    }

    private class NotificationBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Player player = PlayerNotificationManager.this.player;
            if (player == null
                    || !isNotificationStarted
                    || intent.getIntExtra(EXTRA_INSTANCE_ID, instanceId) != instanceId) {
                return;
            }
            String action = intent.getAction();
            if (ACTION_PLAY.equals(action)) {
                if (player.getPlaybackState() == Player.STATE_IDLE) {
                    if (playbackPreparer != null) {
                        playbackPreparer.preparePlayback();
                    }
                } else if (player.getPlaybackState() == Player.STATE_ENDED) {
                    seekTo(player, player.getCurrentWindowIndex(), C.TIME_UNSET);
                }
                controlDispatcher.dispatchSetPlayWhenReady(player, /* playWhenReady= */ true);
            } else if (ACTION_PAUSE.equals(action)) {
                controlDispatcher.dispatchSetPlayWhenReady(player, /* playWhenReady= */ false);
            } else if (ACTION_PREVIOUS.equals(action)) {
                previous(player);
            } else if (ACTION_REWIND.equals(action)) {
                rewind(player);
            } else if (ACTION_FAST_FORWARD.equals(action)) {
                fastForward(player);
            } else if (ACTION_NEXT.equals(action)) {
                next(player);
            } else if (ACTION_STOP.equals(action)) {
                controlDispatcher.dispatchStop(player, /* reset= */ true);
            } else if (ACTION_DISMISS.equals(action)) {
                stopNotification(/* dismissedByUser= */ true);
            } else if (action != null
                    && customActionReceiver != null
                    && customActions.containsKey(action)) {
                customActionReceiver.onCustomAction(player, action, intent);
            }
        }
    }
}
