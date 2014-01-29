/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.boko.vimusic.service;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Random;
import java.util.Vector;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.RemoteControlClient;
import android.media.RemoteControlClient.MetadataEditor;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;

import com.boko.vimusic.MediaButtonIntentReceiver;
import com.boko.vimusic.NotificationHelper;
import com.boko.vimusic.appwidgets.AppWidgetLarge;
import com.boko.vimusic.appwidgets.AppWidgetLargeAlternate;
import com.boko.vimusic.appwidgets.AppWidgetSmall;
import com.boko.vimusic.appwidgets.RecentWidgetProvider;
import com.boko.vimusic.cache.ImageCache;
import com.boko.vimusic.cache.ImageFetcher;
import com.boko.vimusic.loaders.SongLoader;
import com.boko.vimusic.model.HostType;
import com.boko.vimusic.model.Song;
import com.boko.vimusic.model.SongFactory;
import com.boko.vimusic.provider.FavoritesStore;
import com.boko.vimusic.provider.RecentStore;
import com.boko.vimusic.utils.CommonUtils;
import com.boko.vimusic.utils.MusicUtils;

/**
 * Provides "background" audio playback capabilities, allowing the
 * user to switch between activities without stopping playback.
 */
@SuppressLint("NewApi")
public class MediaPlaybackService extends Service {
	public static final boolean DEBUG = true;
    /** used to specify whether enqueue() should start playing
     * the new list of files right away, next or once all the currently
     * queued files have been played
     */
    public static final int NOW = 1;
    public static final int NEXT = 2;
    public static final int LAST = 3;
    
    public static final int SHUFFLE_NONE = 0;
    public static final int SHUFFLE_NORMAL = 1;
    
    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_CURRENT = 1;
    public static final int REPEAT_ALL = 2;
    
	/**
	 * For backwards compatibility reasons, also provide sticky broadcasts under
	 * the music package
	 */
	public static final String VIMUSIC_PACKAGE_NAME = "com.boko.vimusic";
	public static final String ANDROID_PACKAGE_NAME = "com.android.music";

    public static final String PLAYSTATE_CHANGED = VIMUSIC_PACKAGE_NAME + ".playstatechanged";
    public static final String META_CHANGED = VIMUSIC_PACKAGE_NAME + ".metachanged";
    public static final String QUEUE_CHANGED = VIMUSIC_PACKAGE_NAME + ".queuechanged";
    public static final String POSITION_CHANGED = VIMUSIC_PACKAGE_NAME + ".positionchanged";
    public static final String REPEATMODE_CHANGED = VIMUSIC_PACKAGE_NAME + ".repeatmodechanged";
    public static final String SHUFFLEMODE_CHANGED = VIMUSIC_PACKAGE_NAME + ".shufflemodechanged";
    public static final String FOREGROUND_STATE_CHANGED = VIMUSIC_PACKAGE_NAME + ".fgstatechanged";
    public static final String NOW_IN_FOREGROUND = "nowinforeground";

    public static final String SERVICECMD = VIMUSIC_PACKAGE_NAME + ".musicservicecommand";
    public static final String CMDNAME = "command";
    public static final String CMDTOGGLEPAUSE = "togglepause";
    public static final String CMDSTOP = "stop";
    public static final String CMDPAUSE = "pause";
    public static final String CMDPLAY = "play";
    public static final String CMDPREVIOUS = "previous";
    public static final String CMDNEXT = "next";
    public static final String CMDNOTIF = "buttonId";

    public static final String TOGGLEPAUSE_ACTION = SERVICECMD + ".togglepause";
    public static final String PAUSE_ACTION = SERVICECMD + ".pause";
    public static final String PREVIOUS_ACTION = SERVICECMD + ".previous";
    public static final String NEXT_ACTION = SERVICECMD + ".next";
	public static final String STOP_ACTION = SERVICECMD + ".stop";
	public static final String REPEAT_ACTION = SERVICECMD + ".repeat";
	public static final String SHUFFLE_ACTION = SERVICECMD + ".shuffle";
	public static final String REFRESH = SERVICECMD + ".refresh";
	public static final String UPDATE_LOCKSCREEN = SERVICECMD + ".updatelockscreen";

    private static final int TRACK_ENDED = 1;
    private static final int RELEASE_WAKELOCK = 2;
    private static final int SERVER_DIED = 3;
    private static final int FOCUSCHANGE = 4;
    private static final int FADEDOWN = 5;
    private static final int FADEUP = 6;
    private static final int TRACK_WENT_TO_NEXT = 7;
    
    private MultiPlayer mPlayer;
    private String mFileToPlay;
    private int mShuffleMode = SHUFFLE_NONE;
    private int mRepeatMode = REPEAT_NONE;
    private int mMediaMountedCount = 0;
    private Song [] mPlayList = null;
    private int [] mPlayOrder = null;
    private int mPlayListLen = 0;
    private int mPlayPos = -1;
    private int mNextPlayPos = -1;
    public static final String LOGTAG = MediaPlaybackService.class.getSimpleName();
    private int mOpenFailedCounter = 0;
    private BroadcastReceiver mUnmountReceiver = null;
    private WakeLock mWakeLock;
    private int mServiceStartId = -1;
    private boolean mServiceInUse = false;
    private boolean mIsSupposedToBePlaying = false;
    private AudioManager mAudioManager;
    private boolean mQueueIsSaveable = true;
    // used to track what type of audio focus loss caused the playback to pause
    private boolean mPausedByTransientLossOfFocus = false;
    // Used to track whether any of activities is in the foreground
    private boolean mAnyActivityInForeground = false;

    private SharedPreferences mPreferences;
    // We use this to distinguish between different cards when saving/restoring playlists.
    // This will have to change if we want to support multiple simultaneous cards.
    private int mCardId;
    
    // Widgets
	private final AppWidgetSmall mAppWidgetSmall = AppWidgetSmall.getInstance();
	private final AppWidgetLarge mAppWidgetLarge = AppWidgetLarge.getInstance();
	private final AppWidgetLargeAlternate mAppWidgetLargeAlternate = AppWidgetLargeAlternate
			.getInstance();
	private final RecentWidgetProvider mRecentWidgetProvider = RecentWidgetProvider
			.getInstance();
    
    // interval after which we stop the service when idle
    private static final int IDLE_DELAY = 60000;

    private RemoteControlClient mRemoteControlClient;

    private Handler mDelayedStopHandler;
    private Handler mMediaplayerHandler;
    
	// Image cache
	private ImageFetcher mImageFetcher;
	// Used to build the notification
	private NotificationHelper mNotificationHelper;
	// Recently listened database
	private RecentStore mRecentsCache;
	// Favorites database
	private FavoritesStore mFavoritesCache;
	// Used to save the queue as reverse hexadecimal numbers, which we can
	// generate faster than normal decimal or hexadecimal numbers, which in turn
	// allows us to save the playlist more often without worrying too much about
	// performance
    private final char hexdigits [] = new char [] {
            '0', '1', '2', '3',
            '4', '5', '6', '7',
            '8', '9', 'a', 'b',
            'c', 'd', 'e', 'f'
    };
    
    private final IBinder mBinder = new ServiceStub(this);
    
    private static QuerySongTask songTask;
    
	private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			String cmd = intent.getStringExtra(CMDNAME);
			int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            // Someone asked us to refresh a set of specific widgets, probably
            // because they were just added.
			if (AppWidgetSmall.CMDAPPWIDGETUPDATE.equals(cmd)) {
				mAppWidgetSmall.performUpdate(MediaPlaybackService.this, appWidgetIds);
			} else if (AppWidgetLarge.CMDAPPWIDGETUPDATE.equals(cmd)) {
				mAppWidgetLarge.performUpdate(MediaPlaybackService.this, appWidgetIds);
			} else if (AppWidgetLargeAlternate.CMDAPPWIDGETUPDATE
					.equals(cmd)) {
				mAppWidgetLargeAlternate.performUpdate(
						MediaPlaybackService.this, appWidgetIds);
			} else if (RecentWidgetProvider.CMDAPPWIDGETUPDATE.equals(cmd)) {
				mRecentWidgetProvider.performUpdate(MediaPlaybackService.this,
						appWidgetIds);
			} else {
				handleCommandIntent(intent);
			}
		}
	};

	private final OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
		@Override
		public void onAudioFocusChange(final int focusChange) {
			if (DEBUG) Log.d(LOGTAG, "Audio focus changed. Status = " + focusChange);
			mMediaplayerHandler.obtainMessage(FOCUSCHANGE, focusChange, 0)
					.sendToTarget();
		}
	};
	
    public MediaPlaybackService() {
    }
	
	@Override
	public void onCreate() {
		if (DEBUG) Log.d(LOGTAG, "Creating service");
		super.onCreate();
		
		// Initialize the favorites and recents databases
		mRecentsCache = RecentStore.getInstance(this);
		mFavoritesCache = FavoritesStore.getInstance(this);

		// Initialize the notification helper
		mNotificationHelper = new NotificationHelper(this);

		// Initialize the image fetcher
		mImageFetcher = ImageFetcher.getInstance(this);
		// Initialize the image cache
		mImageFetcher.setImageCache(ImageCache.getInstance(this));
		
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        ComponentName rec = new ComponentName(getPackageName(),
                MediaButtonIntentReceiver.class.getName());
        mAudioManager.registerMediaButtonEventReceiver(rec);

        Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
        i.setComponent(rec);
        PendingIntent pi = PendingIntent.getBroadcast(getApplicationContext() /*context*/,
                0 /*requestCode, ignored*/, i /*intent*/, PendingIntent.FLAG_UPDATE_CURRENT /*flags*/);
        mRemoteControlClient = new RemoteControlClient(pi);
        mAudioManager.registerRemoteControlClient(mRemoteControlClient);

        int flags = RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS
                | RemoteControlClient.FLAG_KEY_MEDIA_NEXT
                | RemoteControlClient.FLAG_KEY_MEDIA_PLAY
                | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
                | RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
                | RemoteControlClient.FLAG_KEY_MEDIA_STOP;

		if (CommonUtils.hasJellyBeanMR2()) {
			flags |= RemoteControlClient.FLAG_KEY_MEDIA_POSITION_UPDATE;

			mRemoteControlClient
					.setOnGetPlaybackPositionListener(new RemoteControlClient.OnGetPlaybackPositionListener() {
						@Override
						public long onGetPlaybackPosition() {
							return position();
						}
					});
			mRemoteControlClient
					.setPlaybackPositionUpdateListener(new RemoteControlClient.OnPlaybackPositionUpdateListener() {
						@Override
						public void onPlaybackPositionUpdate(long newPositionMs) {
							seek(newPositionMs);
						}
					});
		}

		mRemoteControlClient.setTransportControlFlags(flags);

		mPreferences = getSharedPreferences("Service", 0);
		mCardId = MusicUtils.getCardId(this);

		registerExternalStorageListener();

		// Start up the thread running the service. Note that we create a
		// separate thread because the service normally runs in the process's
		// main thread, which we don't want to block. We also make it
		// background priority so CPU-intensive work will not disrupt the UI.
		final HandlerThread thread = new HandlerThread("MusicPlayerHandler",
				android.os.Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();

		// Initialize the handler
		mDelayedStopHandler = new DelayedStopHandler(this);
		mMediaplayerHandler = new MediaPlayerHandler(this, thread.getLooper());
		
		mPlayer = new MultiPlayer(this);
		mPlayer.setHandler(mMediaplayerHandler);
		
		reloadQueue();
		notifyChange(QUEUE_CHANGED);
		notifyChange(META_CHANGED);

        IntentFilter commandFilter = new IntentFilter();
        commandFilter.addAction(SERVICECMD);
        commandFilter.addAction(TOGGLEPAUSE_ACTION);
        commandFilter.addAction(PAUSE_ACTION);
        commandFilter.addAction(NEXT_ACTION);
        commandFilter.addAction(PREVIOUS_ACTION);
		commandFilter.addAction(STOP_ACTION);
		commandFilter.addAction(REPEAT_ACTION);
		commandFilter.addAction(SHUFFLE_ACTION);
		registerReceiver(mIntentReceiver, commandFilter);

        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
        mWakeLock.setReferenceCounted(false);
        
        // If the service was idle, but got killed before it stopped itself, the
        // system will relaunch it. Make sure it gets stopped again in that case.
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
	}

	@Override
	public void onDestroy() {
		if (DEBUG) Log.d(LOGTAG, "Destroying service");
        // release all MediaPlayer resources, including the native player and wakelocks
        Intent i = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
        i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        sendBroadcast(i);
        mPlayer.release();
        mPlayer = null;

		mAudioManager.abandonAudioFocus(mAudioFocusListener);
		mAudioManager.unregisterRemoteControlClient(mRemoteControlClient);
		
		// make sure there aren't any other messages coming
        mDelayedStopHandler.removeCallbacksAndMessages(null);
		mMediaplayerHandler.removeCallbacksAndMessages(null);

        unregisterReceiver(mIntentReceiver);
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
            mUnmountReceiver = null;
        }
        mWakeLock.release();
        super.onDestroy();
	}
	
	private void saveQueue(final boolean full) {
        if (!mQueueIsSaveable) {
            return;
        }
        if (DEBUG) Log.d(LOGTAG, "Saving queue.");
		Editor ed = mPreferences.edit();
		if (full) {
			StringBuilder q = new StringBuilder();
			int len = mPlayListLen;
			for (int i = 0; i < len; i++) {
				Song song = mPlayList[i];
				if (song != null && song.getId() != null) {
					q.append(song.getId() + "&" + song.getHost() + "|");
				}
			}
			ed.putString("queue", q.toString());
			ed.putInt("cardid", mCardId);
            if (mShuffleMode != SHUFFLE_NONE) {
                // In shuffle mode we need to save the order too
                len = mPlayOrder.length;
                q.setLength(0);
                for (int i = 0; i < len; i++) {
                    int n = mPlayOrder[i];
                    if (n == 0) {
                        q.append("0;");
                    } else {
                        while (n != 0) {
                            int digit = (n & 0xf);
                            n >>>= 4;
                            q.append(hexdigits[digit]);
                        }
                        q.append(";");
                    }
                }
                ed.putString("order", q.toString());
            }
		}
        ed.putInt("curpos", mPlayPos);
        if (mPlayer.isInitialized()) {
            ed.putLong("seekpos", mPlayer.position());
        }
        ed.putInt("repeatmode", mRepeatMode);
        ed.putInt("shufflemode", mShuffleMode);
		ed.apply();
	}

	private void reloadQueue() {
		if (DEBUG) Log.d(LOGTAG, "Reloading queue.");
		String q = null;
		
		int id = mCardId;
        if (mPreferences.contains("cardid")) {
            id = mPreferences.getInt("cardid", ~mCardId);
        }
		if (id == mCardId) {
            // Only restore the saved playlist if the card is still
            // the same one as when the playlist was saved
			q = mPreferences.getString("queue", "");
		}
		int qlen = q != null ? q.length() : 0;
		if (qlen > 1) {
			int plen = 0;
			int n = 0;
			int shift = 0;
			String[] songs = q.split("|");
			for (String song : songs) {
				String[] parts = song.split("&");
				if (parts != null && parts.length == 2) {
					ensurePlayListCapacity(plen + 1);
					mPlayList[plen] = SongFactory.newSong(HostType.getHost(Integer.valueOf(parts[1])), parts[0]);
					plen++;
				}
			}
			mPlayListLen = plen;
			final int pos = mPreferences.getInt("curpos", 0);
			if (pos < 0 || pos >= mPlayListLen) {
				// The saved playlist is bogus, discard it
				mPlayListLen = 0;
				mPlayOrder = new int[0];
				return;
			}
			mPlayPos = pos;
			
			int repmode = mPreferences.getInt("repeatmode", REPEAT_NONE);
            if (repmode != REPEAT_ALL && repmode != REPEAT_CURRENT) {
                repmode = REPEAT_NONE;
            }
            mRepeatMode = repmode;

            int shufmode = mPreferences.getInt("shufflemode", SHUFFLE_NONE);
            if (shufmode != SHUFFLE_NORMAL) {
                shufmode = SHUFFLE_NONE;
                mPlayOrder = generateArray(0, mPlayListLen);
            } else {
                // in shuffle mode we need to restore the history too
                q = mPreferences.getString("order", "");
                qlen = q != null ? q.length() : 0;
                if (qlen > 1) {
                    plen = 0;
                    n = 0;
                    shift = 0;
                    Vector<Integer> order = new Vector<Integer>();
                    for (int i = 0; i < qlen; i++) {
                        char c = q.charAt(i);
                        if (c == ';') {
                            if (n >= mPlayListLen) {
                                // bogus history data
                            	order.clear();
                                break;
                            }
                            order.add(n);
                            n = 0;
                            shift = 0;
                        } else {
                            if (c >= '0' && c <= '9') {
                                n += ((c - '0') << shift);
                            } else if (c >= 'a' && c <= 'f') {
                                n += ((10 + c - 'a') << shift);
                            } else {
                                // bogus history data
                            	order.clear();
                                break;
                            }
                            shift += 4;
                        }
                    }
                    if (order.size() == mPlayListLen) {
                    	mPlayOrder = new int[mPlayListLen];
                    	for (int i = 0; i < mPlayListLen; i++) {
                    		mPlayOrder[i] = order.get(i);
                    	}
                    } else {
                    	mPlayOrder = generateArray(0, mPlayListLen);
                    	shuffleArray(mPlayOrder);
                    }
                }
            }
            mShuffleMode = shufmode;
			
            // Make sure we don't auto-skip to the next song, since that
            // also starts playback. What could happen in that case is:
            // - music is paused
            // - go to UMS and delete some files, including the currently playing one
            // - come back from UMS
            // (time passes)
            // - music app is killed for some reason (out of memory)
            // - music service is restarted, service restores state, doesn't find
            //   the "current" file, goes to the next and: playback starts on its
            //   own, potentially at some random inconvenient time.
			mOpenFailedCounter = 20;
			prepareAndPlayCurrent();
		}
	}

	@Override
	public IBinder onBind(final Intent intent) {
		if (DEBUG) Log.d(LOGTAG, "Service bound, intent = " + intent);
		mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
        return mBinder;
	}
	
	@Override
	public void onRebind(final Intent intent) {
		if (DEBUG) Log.d(LOGTAG, "Service rebound, intent = " + intent);
		mDelayedStopHandler.removeCallbacksAndMessages(null);
		mServiceInUse = true;
	}
	
	@Override
	public int onStartCommand(final Intent intent, final int flags,
			final int startId) {
		mServiceStartId = startId;
		mDelayedStopHandler.removeCallbacksAndMessages(null);

		if (intent != null) {
			if (intent.hasExtra(NOW_IN_FOREGROUND)) {
				mAnyActivityInForeground = intent.getBooleanExtra(
						NOW_IN_FOREGROUND, false);
				updateNotification();
			}

			handleCommandIntent(intent);
		}

        // make sure the service will shut down on its own if it was
        // just started but not bound to and nothing is playing
		gotoIdleState();
		return START_STICKY;
	}
	
	private void handleCommandIntent(Intent intent) {
		String action = intent.getAction();
		String cmd = intent.getStringExtra(CMDNAME);
		if (DEBUG) Log.d(LOGTAG, "Handling intent. Action = " + action + " / " + cmd);

		if (CMDNEXT.equals(cmd) || NEXT_ACTION.equals(action)) {
			gotoNext(true);
		} else if (CMDPREVIOUS.equals(cmd) || PREVIOUS_ACTION.equals(action)) {
			prev();
		} else if (CMDTOGGLEPAUSE.equals(cmd)
				|| TOGGLEPAUSE_ACTION.equals(action)) {
			if (isPlaying()) {
				pause();
				mPausedByTransientLossOfFocus = false;
			} else {
				play();
			}
		} else if (CMDPAUSE.equals(cmd) || PAUSE_ACTION.equals(action)) {
			pause();
			mPausedByTransientLossOfFocus = false;
		} else if (CMDPLAY.equals(cmd)) {
			play();
		} else if (CMDSTOP.equals(cmd) || STOP_ACTION.equals(action)) {
			pause();
			mPausedByTransientLossOfFocus = false;
			seek(0);
			shutdownImmediate(this);
		} else if (REPEAT_ACTION.equals(action)) {
			cycleRepeat();
		} else if (SHUFFLE_ACTION.equals(action)) {
			cycleShuffle();
		}
	}

	@Override
	public boolean onUnbind(final Intent intent) {
		if (DEBUG) Log.d(LOGTAG, "Service unbound, intent = " + intent);
        mServiceInUse = false;

        // Take a snapshot of the current playlist
        saveQueue(true);

        if (isPlaying() || mPausedByTransientLossOfFocus) {
            // something is currently playing, or will be playing once 
            // an in-progress action requesting audio focus ends, so don't stop the service now.
            return true;
        }
        
        // If there is a playlist but playback is paused, then wait a while
        // before stopping the service, so that pause/resume isn't slow.
        // Also delay stopping the service if we're transitioning between tracks.
        if (mPlayListLen > 0  || mMediaplayerHandler.hasMessages(TRACK_ENDED)) {
        	gotoIdleState();
            return true;
        }
        
        // No active playlist, OK to stop the service right now
        stopSelf(mServiceStartId);
        return true;
	}
	
    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
     * The intent will call closeExternalStorageFiles() if the external media
     * is going to be ejected, so applications can clean up any files they have open.
     */
    public void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                    	if (DEBUG) Log.d(LOGTAG, "Media ejected.");
                    	stop(true);
                        saveQueue(true);
                        mQueueIsSaveable = false;
                        notifyChange(QUEUE_CHANGED);
                        notifyChange(META_CHANGED);
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                    	if (DEBUG) Log.d(LOGTAG, "Media mounted.");
                        mMediaMountedCount++;
                        mCardId = MusicUtils.getCardId(MediaPlaybackService.this);
                        reloadQueue();
                        mQueueIsSaveable = true;
                        notifyChange(QUEUE_CHANGED);
                        notifyChange(META_CHANGED);
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }
    
    /**
     * Notify the change-receivers that something has changed.
     * The intent that is sent contains the following data
     * for the currently playing track:
     * "id" - Integer: the database row ID
     * "artist" - String: the name of the artist
     * "album" - String: the name of the album
     * "track" - String: the name of the track
     * The intent has an action that is one of
     * "com.android.music.metachanged"
     * "com.android.music.queuechanged",
     * "com.android.music.playbackcomplete"
     * "com.android.music.playstatechanged"
     * respectively indicating that a new track has
     * started playing, that the playback queue has
     * changed, that playback has stopped because
     * the last file in the list has been played,
     * or that the play-state changed (paused/resumed).
     */
    private void notifyChange(final String what) {
		if (DEBUG) Log.d(LOGTAG, "notifyChange: what = " + what);

		Intent i = new Intent(what);
		i.putExtra("id", getAudioId());
		i.putExtra("track", getTrackName());
        i.putExtra("artist", getArtistName());
        i.putExtra("album",getAlbumName());
        i.putExtra("playing", isPlaying());
		i.putExtra("isfavorite", isFavorite());
		sendStickyBroadcast(i);

		final Intent musicIntent = new Intent(i);
		musicIntent.setAction(what.replace(VIMUSIC_PACKAGE_NAME,
				ANDROID_PACKAGE_NAME));
		sendStickyBroadcast(musicIntent);
		
		// Update the lockscreen controls
		updateRemoteControlClient(what);

		if (what.equals(POSITION_CHANGED)) {
			return;
		} else if (what.equals(META_CHANGED)) {
			// Increase the play count for favorite songs.
			if (!mFavoritesCache.isFavoriteSong(getAudioId(), HostType.LOCAL)) {
				mFavoritesCache.addSong(getAudioId(), HostType.LOCAL, getTrackName(),
						getAlbumName(), getArtistName());
			}
			// Add the track to the recently played list.
			mRecentsCache.addAlbum(getAlbumId(), "", getAlbumName(),
					getArtistName(),
					MusicUtils.getSongCountForAlbum(this, getAlbumId()),
					MusicUtils.getReleaseDateForAlbum(this, getAlbumId()));
		} else if (what.equals(QUEUE_CHANGED)) {
			saveQueue(true);
			if (isPlaying()) {
				setNextTrack();
			}
		} else {
			saveQueue(false);
		}

		if (what.equals(PLAYSTATE_CHANGED)) {
			mNotificationHelper.updatePlayState(isPlaying());
		}

		// Share this notification directly with our widgets
		mAppWidgetSmall.notifyChange(this, what);
		mAppWidgetLarge.notifyChange(this, what);
		mAppWidgetLargeAlternate.notifyChange(this, what);
		mRecentWidgetProvider.notifyChange(this, what);
	}
    
	/**
	 * Updates the lockscreen controls.
	 * 
	 * @param what The broadcast
	 */
	private void updateRemoteControlClient(final String what) {
		if (what.equals(META_CHANGED) || what.equals(QUEUE_CHANGED)) {
            RemoteControlClient.MetadataEditor ed = mRemoteControlClient.editMetadata(true);
            ed.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, getTrackName());
            ed.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, getArtistName());
            ed.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, getAlbumName());
            ed.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, duration());
            Bitmap b = getAlbumArt();
            if (b != null) {
				// RemoteControlClient wants to recycle the bitmaps thrown at
				// it, so we need
				// to make sure not to hand out our cache copy
				Bitmap.Config config = b.getConfig();
				if (config == null) {
					config = Bitmap.Config.ARGB_8888;
				}
				b = b.copy(config, false);
				ed.putBitmap(MetadataEditor.BITMAP_KEY_ARTWORK, b);
			}
            ed.apply();
		}
		if (what.equals(PLAYSTATE_CHANGED) || what.equals(POSITION_CHANGED) || what.equals(META_CHANGED) || what.equals(QUEUE_CHANGED)) {
			int playState = isPlaying() ? RemoteControlClient.PLAYSTATE_PLAYING : RemoteControlClient.PLAYSTATE_PAUSED;
			if (CommonUtils.hasJellyBeanMR2()) {
				mRemoteControlClient.setPlaybackState(playState, position(), 1.0f);
			} else {
				mRemoteControlClient.setPlaybackState(playState);
			}
		}
	}
    
	private void ensurePlayListCapacity(final int size) {
        if (mPlayList == null || size > mPlayList.length) {
            // reallocate at 2x requested size so we don't
            // need to grow and copy the array for every
            // insert
            Song [] newlist = new Song[size * 2];
            int len = mPlayList != null ? mPlayList.length : mPlayListLen;
            for (int i = 0; i < len; i++) {
                newlist[i] = mPlayList[i];
            }
            mPlayList = newlist;
        }
        // FIXME: shrink the array when the needed size is much smaller
        // than the allocated size
	}
	
	// insert the list of songs at the specified position in the playlist
	private void addToPlayList(final Song[] list, int position) {
        int addlen = list.length;
        if (position < 0) { // overwrite
            mPlayListLen = 0;
            mPlayOrder = new int[0];
            position = 0;
        }
        ensurePlayListCapacity(mPlayListLen + addlen);
        if (position > mPlayListLen) {
            position = mPlayListLen;
        }
        
        // move part of list after insertion point
        int tailsize = mPlayListLen - position;
        for (int i = tailsize ; i > 0 ; i--) {
            mPlayList[position + i] = mPlayList[position + i - addlen]; 
        }
        
        // copy list into playlist
        for (int i = 0; i < addlen; i++) {
            mPlayList[position + i] = list[i];
        }
        int[] a = generateArray(mPlayListLen, addlen);
        if (mShuffleMode == SHUFFLE_NORMAL) {
        	shuffleArray(a);
        }
        mPlayOrder = concat(mPlayOrder, a);
        mPlayListLen += addlen;
        if (mPlayListLen == 0) {
            notifyChange(META_CHANGED);
        }
	}
	
    /**
     * Appends a list of tracks to the current playlist.
     * If nothing is playing currently, playback will be started at
     * the first track.
     * If the action is NOW, playback will switch to the first of
     * the new tracks immediately.
     * @param list The list of tracks to append.
     * @param action NOW, NEXT or LAST
     */
	public void enqueue(final Song[] list, final int action) {
        synchronized(this) {
            if (action == NEXT && mPlayPos + 1 < mPlayListLen) {
                addToPlayList(list, mPlayPos + 1);
                notifyChange(QUEUE_CHANGED);
            } else {
                // action == LAST || action == NOW || mPlayPos + 1 == mPlayListLen
                addToPlayList(list, Integer.MAX_VALUE);
                notifyChange(QUEUE_CHANGED);
                if (action == NOW) {
                    mPlayPos = mPlayListLen - list.length;
                    prepareAndPlayCurrent();
                    return;
                }
            }
            if (mPlayPos < 0) {
                mPlayPos = 0;
                prepareAndPlayCurrent();
            }
        }
	}
	
    /**
     * Replaces the current playlist with a new list,
     * and prepares for starting playback at the specified
     * position in the list, or a random position if the
     * specified position is 0.
     * @param list The new list of tracks.
     */
	public void open(final Song[] list, final int position) {
        synchronized (this) {
            int listlength = list.length;
            boolean newlist = true;
            if (mPlayListLen == listlength) {
                // possible fast path: list might be the same
                newlist = false;
                for (int i = 0; i < listlength; i++) {
                    if (list[i] != mPlayList[i]) {
                        newlist = true;
                        break;
                    }
                }
            }
            if (newlist) {
                addToPlayList(list, -1);
                notifyChange(QUEUE_CHANGED);
            }
            if (position >= 0) {
                mPlayPos = position;
            } else {
            	Random rand = new Random();
                mPlayPos = rand.nextInt(mPlayListLen);
            }

            prepareAndPlayCurrent();
        }
	}
	
    /**
     * Moves the item at index1 to index2.
     * @param index1
     * @param index2
     */
	public void moveQueueItem(int index1, int index2) {
		synchronized (this) {
            if (index1 >= mPlayListLen) {
                index1 = mPlayListLen - 1;
            }
            if (index2 >= mPlayListLen) {
                index2 = mPlayListLen - 1;
            }
            if (index1 < index2) {
                Song tmp = mPlayList[index1];
                for (int i = index1; i < index2; i++) {
                    mPlayList[i] = mPlayList[i+1];
                }
                mPlayList[index2] = tmp;
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index1 && mPlayPos <= index2) {
                        mPlayPos--;
                }
            } else if (index2 < index1) {
            	Song tmp = mPlayList[index1];
                for (int i = index1; i > index2; i--) {
                    mPlayList[i] = mPlayList[i-1];
                }
                mPlayList[index2] = tmp;
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index2 && mPlayPos <= index1) {
                        mPlayPos++;
                }
            }
            notifyChange(QUEUE_CHANGED);
		}
	}
	
    /**
     * Returns the current play list
     * @return An array of the tracks in the play list
     */
	public Song[] getQueue() {
        synchronized (this) {
            int len = mPlayListLen;
            Song [] list = new Song[len];
            for (int i = 0; i < len; i++) {
                list[i] = mPlayList[i];
            }
            return list;
        }
	}
    
	private void setNextTrack() {
		mNextPlayPos = getNextPosition(false);
		if (mPlayList != null && mNextPlayPos >= 0) {
			prepareNext();
		} else {
			mPlayer.setNextDataSource(null);
		}
	}
	
    /**
     * Starts playback of a previously opened file.
     */
	public void play() {
		int requestStatus = mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

		if (DEBUG)
			Log.d(LOGTAG, "Starting playback: audio focus request status = "
					+ requestStatus);

		if (requestStatus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
			return;
		}
        
        mAudioManager.registerMediaButtonEventReceiver(new ComponentName(this.getPackageName(),
                MediaButtonIntentReceiver.class.getName()));

        if (mPlayer.isInitialized()) {
        	// if we are at the end of the song, go to the next song first
            long duration = mPlayer.duration();
            if (mRepeatMode != REPEAT_CURRENT && duration > 2000 &&
                mPlayer.position() >= duration - 2000) {
                gotoNext(true);
            }

			mPlayer.start();
            // make sure we fade in, in case a previous fadein was stopped because
            // of another focus loss
			mMediaplayerHandler.removeMessages(FADEDOWN);
			mMediaplayerHandler.sendEmptyMessage(FADEUP);

            if (!mIsSupposedToBePlaying) {
                mIsSupposedToBePlaying = true;
                notifyChange(PLAYSTATE_CHANGED);
            }
            
            updateNotification();
        } else if (mPlayListLen <= 0) {
            // This is mostly so that if you press 'play' on a bluetooth headset
            // without every having played anything before, it will still play
            // something.
        	shuffleAll();
		}
	}

	private void updateNotification() {
		if (!mAnyActivityInForeground) {
			mNotificationHelper.buildNotification(getAlbumName(),
					getArtistName(), getTrackName(), getAlbumId(),
					getAlbumArt(), isPlaying());
		} else if (mAnyActivityInForeground) {
			mNotificationHelper.killNotification();
		}
	}
	
	private void stop(final boolean goToIdle) {
        if (mPlayer != null) {
            mPlayer.stop();
        }
        mFileToPlay = null;
        if (goToIdle) {
        	gotoIdleState();
            if (mIsSupposedToBePlaying) {
                mIsSupposedToBePlaying = false;
                notifyChange(PLAYSTATE_CHANGED);
            }
        } else {
            stopForeground(false);
        }
	}
	
    /**
     * Pauses playback (call play() to resume)
     */
	public void pause() {
		synchronized (this) {
			mMediaplayerHandler.removeMessages(FADEUP);
			if (isPlaying()) {
				mPlayer.pause();
				gotoIdleState();
				mIsSupposedToBePlaying = false;
				notifyChange(PLAYSTATE_CHANGED);
			}
		}
	}
	
    /** Returns whether something is currently playing
    *
    * @return true if something is playing (or will be playing shortly, in case
    * we're currently transitioning between tracks), false if not.
    */
   public boolean isPlaying() {
       return mIsSupposedToBePlaying;
   }
   
   
   /**
   * Desired behavior for prev/next/shuffle:
   * <p>
   * - NEXT will move to the next track in the list when not shuffling, and to
   * a track randomly picked from the not-yet-played tracks when shuffling.
   * If all tracks have already been played, pick from the full set, but
   * avoid picking the previously played track if possible.
   * <p>
   * - when shuffling, PREV will go to the previously played track. Hitting PREV
   * again will go to the track played before that, etc. When the start of the
   * history has been reached, PREV is a no-op.
   * <br>
   * When not shuffling, PREV will go to the sequentially previous track (the
   * difference with the shuffle-case is mainly that when not shuffling, the
   * user can back up to tracks that are not in the history).
   * <p>
   * Example:
   * When playing an album with 10 tracks from the start, and enabling shuffle
   * while playing track 5, the remaining tracks (6-10) will be shuffled, e.g.
   * the final play order might be 1-2-3-4-5-8-10-6-9-7.
   * <br>
   * When hitting 'prev' 8 times while playing track 7 in this example, the
   * user will go to tracks 9-6-10-8-5-4-3-2. If the user then hits 'next',
   * a random track will be picked again. If at any time user disables shuffling
   * the next/previous track will be picked in sequential order again.
   */
   public void prev() {
		synchronized (this) {
			int pos = getPreShuffledPos();
            if (pos > 0) {
            	mPlayPos = mPlayOrder[pos - 1];
            } else {
                mPlayPos = mPlayOrder[mPlayListLen - 1];
            }
            stop(false);
            prepareAndPlayCurrent();
		}
	}
   
   /**
    * Get the next position to play. Note that this may actually modify mPlayPos
    * if playback is in SHUFFLE_AUTO mode and the shuffle list window needed to
    * be adjusted. Either way, the return value is the next value that should be
    * assigned to mPlayPos;
    */
   private int getNextPosition(boolean force) {
      if (mRepeatMode == REPEAT_CURRENT) {
           if (mPlayPos < 0) return 0;
           return mPlayPos;
       } else {
    	   int pos = getPreShuffledPos();
           if (pos >= mPlayListLen - 1) {
        	   // we're at the end of the list
               if (mRepeatMode == REPEAT_NONE && !force) {
                   // all done
                   return -1;
               } else if (mRepeatMode == REPEAT_ALL || force) {
                   return mPlayOrder[0];
               }
               return -1;
           } else {
               return mPlayOrder[pos + 1];
           }
       }
   }
   
   public void gotoNext(boolean force) {
		synchronized (this) {
			if (mPlayListLen <= 0) {
				if (DEBUG)
					Log.d(LOGTAG, "No play queue");
				gotoIdleState();
				return;
			}

            int pos = getNextPosition(force);
            if (pos < 0) {
            	stop(true);
                return;
			}
			stop(false);
			mPlayPos = pos;
			prepareAndPlayCurrent();
		}
	}
   
   private void gotoIdleState() {
		if (DEBUG)
			Log.d(LOGTAG, "Going to idle state");
       mDelayedStopHandler.removeCallbacksAndMessages(null);
       Message msg = mDelayedStopHandler.obtainMessage();
       mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
   }
   
   protected static void shutdownImmediate(final MediaPlaybackService service) {
		if (DEBUG)
			Log.d(LOGTAG, "Stoping service.");
        // Check again to make sure nothing is playing right now
        if (service.isPlaying() || service.mPausedByTransientLossOfFocus
                || service.mMediaplayerHandler.hasMessages(TRACK_ENDED)) {
            return;
        }

		if (DEBUG)
			Log.d(LOGTAG, "Nothing is playing anymore, releasing notification");
		service.mNotificationHelper.killNotification();
		service.mAudioManager.abandonAudioFocus(service.mAudioFocusListener);

		if (!service.mServiceInUse) {
            // save the queue again, because it might have changed
            // since the user exited the music app (because of
            // party-shuffle or because the play-position changed)
			service.saveQueue(true);
			service.stopSelf(service.mServiceStartId);
		}
   }
   
    /**
     * Removes the range of tracks specified from the play list. If a file within the range is
     * the file currently being played, playback will move to the next file after the
     * range. 
     * @param first The first file to be removed
     * @param last The last file to be removed
     * @return the number of tracks deleted
     */
	public int removeTracks(int first, int last) {
		int numremoved = removeTracksInternal(first, last);
        if (numremoved > 0) {
            notifyChange(QUEUE_CHANGED);
        }
        return numremoved;
	}
	
	private int removeTracksInternal(int first, int last) {
		synchronized (this) {
			if (last < first) return 0;
			if (first < 0) first = 0;
			if (last >= mPlayListLen) last = mPlayListLen - 1;

			boolean gotonext = false;
			if (first <= mPlayPos && mPlayPos <= last) {
				mPlayPos = first;
				gotonext = true;
			} else if (mPlayPos > last) {
				mPlayPos -= (last - first + 1);
			}
			int num = mPlayListLen - last - 1;
            for (int i = 0; i < num; i++) {
                mPlayList[first + i] = mPlayList[last + 1 + i];
            }
            mPlayListLen -= last - first + 1;
            
            mPlayOrder = removeArray(first,last);

			if (gotonext) {
				if (mPlayListLen == 0) {
					stop(true);
					mPlayPos = -1;
					notifyChange(META_CHANGED);
				} else {
                    if (mPlayPos >= mPlayListLen) {
                        mPlayPos = 0;
                    }
                    stop(false);
                    prepareAndPlayCurrent();
				}
			}
			return last - first + 1;
		}
	}
	
    /**
     * Removes all instances of the track with the given id
     * from the playlist.
     * @param song The song to be removed
     * @return how many instances of the track were removed
     */
	public int removeTrack(final Song song) {
		int numremoved = 0;
		synchronized (this) {
			for (int i = 0; i < mPlayListLen; i++) {
				if (mPlayList[i].equals(song)) {
					numremoved += removeTracksInternal(i, i);
					i--;
				}
			}
		}
		if (numremoved > 0) {
			notifyChange(QUEUE_CHANGED);
		}
		return numremoved;
	}
	
	public void setShuffleMode(int shufflemode) {
		synchronized (this) {
            if (mShuffleMode == shufflemode && mPlayListLen > 0) {
                return;
            }
            if (shufflemode == SHUFFLE_NONE) {
            	mPlayOrder = generateArray(0, mPlayListLen);
            } else {
            	shuffleArray(mPlayOrder);
            }
            mShuffleMode = shufflemode;
            saveQueue(false);
			notifyChange(SHUFFLEMODE_CHANGED);
		}
	}
	
    public int getShuffleMode() {
        return mShuffleMode;
    }
    
    public void setRepeatMode(int repeatmode) {
		synchronized (this) {
			mRepeatMode = repeatmode;
			setNextTrack();
			saveQueue(false);
			notifyChange(REPEATMODE_CHANGED);
		}
	}
    
	public int getRepeatMode() {
		return mRepeatMode;
	}
	
	public int getMediaMountedCount() {
		return mMediaMountedCount;
	}
	
    /**
     * Returns the path of the currently playing file, or null if
     * no file is currently playing.
     */
    public String getPath() {
        return mFileToPlay;
    }
    
    /**
     * Returns the id of the currently playing file, or null if
     * no file is currently playing.
     */
	public String getAudioId() {
		if (mPlayPos >= 0) {
			return mPlayList[mPlayPos].getId();
		}
		return null;
	}
	
    /**
     * Returns the position in the queue 
     * @return the position in the queue
     */
    public int getQueuePosition() {
    	return mPlayPos;
    }
    
    /**
     * Starts playing the track at the given position in the queue.
     * @param pos The position in the queue of the track that will be played.
     */
    public void setQueuePosition(int pos) {
        synchronized(this) {
            stop(false);
            mPlayPos = pos;
            prepareAndPlayCurrent();
        }
    }
    
	public String getArtistName() {
		if (mPlayPos >= 0) {
			return mPlayList[mPlayPos].mArtistName;
		}
		return null;
	}
	
	public String getArtistId() {
		return null;
	}
	
	public String getAlbumName() {
		if (mPlayPos >= 0) {
			return mPlayList[mPlayPos].mAlbumName;
		}
		return null;
	}
	
	public String getAlbumId() {
		return null;
	}

	public String getAlbumArtistName() {
		return null;
	}
	
	public String getTrackName() {
		if (mPlayPos >= 0) {
			return mPlayList[mPlayPos].getName();
		}
		return null;
	}
	
    /**
     * Returns the duration of the file in milliseconds.
     * Currently this method returns -1 for the duration of MIDI files.
     */
    public long duration() {
        if (mPlayer.isInitialized()) {
            return mPlayer.duration();
        }
        return -1;
    }

    /**
     * Returns the current playback position in milliseconds
     */
    public long position() {
        if (mPlayer.isInitialized()) {
            return mPlayer.position();
        }
        return -1;
    }
    
    /**
     * Seeks to the position specified.
     *
     * @param pos The position to seek to, in milliseconds
     */
    public long seek(long pos) {
    	if (mPlayer.isInitialized()) {
    		if (pos < 0) pos = 0;
            if (pos > mPlayer.duration()) pos = mPlayer.duration();
			long ret = mPlayer.seek(pos);
			notifyChange(POSITION_CHANGED);
			return ret;
		}
		return -1;
	}
    
    /**
     * Sets the audio session ID.
     *
     * @param sessionId: the audio session ID.
     */
    public void setAudioSessionId(int sessionId) {
    	mPlayer.setAudioSessionId(sessionId);
    }

    /**
     * Returns the audio session ID.
     */
    public int getAudioSessionId() {
    	return mPlayer.getAudioSessionId();
    }
    
	/**
	 * True if the current track is a "favorite", false otherwise
	 */
	public boolean isFavorite() {
		synchronized (this) {
			if (mFavoritesCache != null) {
				return mFavoritesCache.isFavoriteSong(getAudioId(), HostType.LOCAL);
			}
		}
		return false;
	}

	/**
	 * Toggles the current song as a favorite.
	 */
	public void toggleFavorite() {
		synchronized (this) {
			if (mFavoritesCache != null) {
				mFavoritesCache.toggleSong(getAudioId(), HostType.LOCAL, getTrackName(),
						getAlbumName(), getArtistName());
			}
		}
	}
	
	/**
	 * Cycles through the different repeat modes
	 */
	private void cycleRepeat() {
		if (mRepeatMode == REPEAT_NONE) {
			setRepeatMode(REPEAT_ALL);
		} else if (mRepeatMode == REPEAT_ALL) {
			setRepeatMode(REPEAT_CURRENT);
			if (mShuffleMode != SHUFFLE_NONE) {
				setShuffleMode(SHUFFLE_NONE);
			}
		} else {
			setRepeatMode(REPEAT_NONE);
		}
	}

	/**
	 * Cycles through the different shuffle modes
	 */
	private void cycleShuffle() {
		if (mShuffleMode == SHUFFLE_NONE) {
			setShuffleMode(SHUFFLE_NORMAL);
			if (mRepeatMode == REPEAT_CURRENT) {
				setRepeatMode(REPEAT_ALL);
			}
		} else {
			setShuffleMode(SHUFFLE_NONE);
		}
	}
	
	/**
	 * Called when one of the lists should refresh or requery.
	 */
	public void refresh() {
		notifyChange(REFRESH);
	}

	/**
	 * @return The album art for the current album.
	 */
	public Bitmap getAlbumArt() {
		// Return the cached artwork
		final Bitmap bitmap = mImageFetcher.getArtwork(getAlbumName(),
				getAlbumId(), getArtistName());
		return bitmap;
	}
	
	private static final class DelayedStopHandler extends Handler {
		private final WeakReference<MediaPlaybackService> mService;

		public DelayedStopHandler(final MediaPlaybackService service) {
			super();
			mService = new WeakReference<MediaPlaybackService>(service);
		}

        @Override
        public void handleMessage(Message msg) {
    		final MediaPlaybackService service = mService.get();
    		if (service == null) {
    			return;
    		}
        	shutdownImmediate(service);
        }
	}

	private static final class MediaPlayerHandler extends Handler {
		private final WeakReference<MediaPlaybackService> mService;
		private float mCurrentVolume = 1.0f;

		/**
		 * Constructor of <code>MediaPlayerHandler</code>
		 * 
		 * @param service The service to use.
		 * @param looper The thread to run on.
		 */
		public MediaPlayerHandler(final MediaPlaybackService service,
				final Looper looper) {
			super(looper);
			mService = new WeakReference<MediaPlaybackService>(service);
		}

		@Override
		public void handleMessage(final Message msg) {
    		if (DEBUG)
    			Log.d(LOGTAG, "Handling media message. Msg = " + msg);
			final MediaPlaybackService service = mService.get();
			if (service == null) {
				return;
			}

			switch (msg.what) {
			case FADEDOWN:
				mCurrentVolume -= .05f;
				if (mCurrentVolume > .2f) {
					sendEmptyMessageDelayed(FADEDOWN, 10);
				} else {
					mCurrentVolume = .2f;
				}
				service.mPlayer.setVolume(mCurrentVolume);
				break;
			case FADEUP:
				mCurrentVolume += .01f;
				if (mCurrentVolume < 1.0f) {
					sendEmptyMessageDelayed(FADEUP, 10);
				} else {
					mCurrentVolume = 1.0f;
				}
				service.mPlayer.setVolume(mCurrentVolume);
				break;
			case SERVER_DIED:
				if (service.isPlaying()) {
					service.gotoNext(true);
				} else {
                    // the server died when we were idle, so just
                    // reopen the same song (it will start again
                    // from the beginning though when the user
                    // restarts)
					service.prepareAndPlayCurrent();
				}
				break;
			case TRACK_WENT_TO_NEXT:
				service.mPlayPos = service.mNextPlayPos;
				service.notifyChange(META_CHANGED);
				service.updateNotification();
				service.setNextTrack();
				break;
			case TRACK_ENDED:
				if (service.mRepeatMode == REPEAT_CURRENT) {
					service.seek(0);
					service.play();
				} else {
					service.gotoNext(false);
				}
				break;
			case RELEASE_WAKELOCK:
				service.mWakeLock.release();
				break;
			case FOCUSCHANGE:
				if (DEBUG)
					Log.d(LOGTAG, "Received audio focus change event " + msg.arg1);
                // This code is here so we can better synchronize it with the code that
                // handles fade-in
                switch (msg.arg1) {
                case AudioManager.AUDIOFOCUS_LOSS:
                	if (DEBUG) Log.d(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS");
                    if(service.isPlaying()) {
                    	service.mPausedByTransientLossOfFocus = false;
                    }
                    service.pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    removeMessages(FADEUP);
                    sendEmptyMessage(FADEDOWN);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                	if (DEBUG) Log.d(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT");
                    if(service.isPlaying()) {
                    	service.mPausedByTransientLossOfFocus = true;
                    }
                    service.pause();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                	if (DEBUG) Log.d(LOGTAG, "AudioFocus: received AUDIOFOCUS_GAIN");
                    if(!service.isPlaying() && service.mPausedByTransientLossOfFocus) {
                    	service.mPausedByTransientLossOfFocus = false;
                        mCurrentVolume = 0f;
                        service.mPlayer.setVolume(mCurrentVolume);
                        service.play(); // also queues a fade-in
                    } else {
                        removeMessages(FADEDOWN);
                        sendEmptyMessage(FADEUP);
                    }
                    break;
                default:
                	if (DEBUG) Log.e(LOGTAG, "Unknown audio focus change code");
            }
            break;

        default:
            break;
			}
		}
	}

    /**
     * Provides a unified interface for dealing with midi files and
     * other media files.
     */
	private static final class MultiPlayer {
		private final WeakReference<MediaPlaybackService> mService;
		private CompatMediaPlayer mCurrentMediaPlayer;
		private CompatMediaPlayer mNextMediaPlayer;
        private String mCurrentPath;
        private String mNextPath;
		private Handler mHandler;
		private boolean mIsInitialized = false;
		
		private OnPreparedListener onCurrentPreparedListener;
		private OnPreparedListener onNextPreparedListener;
		private OnErrorListener onCurrentErrorListener;
		private OnErrorListener onNextErrorListener;

		public MultiPlayer(final MediaPlaybackService service) {
			mService = new WeakReference<MediaPlaybackService>(service);
			mCurrentMediaPlayer = new CompatMediaPlayer();
			mCurrentMediaPlayer.setWakeMode(mService.get(),
					PowerManager.PARTIAL_WAKE_LOCK);
		}

		public void setDataSource(final String path) {
    		if (DEBUG) Log.d(LOGTAG, "Setting current datasource = " + path);
    		if (path != null && path.equals(mCurrentPath)) {
    			if (DEBUG) Log.d(LOGTAG, "Datasource has already set. Abort.");
    			return;
    		}
    		reset();
    		if (path == null) {
    			return;
    		}
    		mCurrentMediaPlayer.setOnPreparedListener(new OnPreparedListener() {
				@Override
				public void onPrepared(MediaPlayer mp) {
					preparedListener.onPrepared(mp);
					mIsInitialized = true;
					mCurrentPath = path;
					if (onCurrentPreparedListener != null) {
						onCurrentPreparedListener.onPrepared(mp);
					}
				}
    		});
    		mCurrentMediaPlayer.setOnErrorListener(new OnErrorListener() {
				
				@Override
				public boolean onError(MediaPlayer mp, int what, int extra) {
					if (onCurrentErrorListener != null) {
						onCurrentErrorListener.onError(mp, what, extra);
					}
					return true;
				}
			});
    		setDataSource(mCurrentMediaPlayer, path);
		}
		
		public void setNextDataSource(final String path) {
    		if (DEBUG) Log.d(LOGTAG, "Setting next datasource = " + path);
    		if (path != null && path.equals(mNextPath)) {
    			if (DEBUG) Log.d(LOGTAG, "Datasource has already set. Abort.");
    			return;
    		}
    		resetNext();
			try {
				mCurrentMediaPlayer.setNextMediaPlayer(null);
			} catch (IllegalStateException e) {
				Log.e(LOGTAG, "Media player not initialized!");
				return;
			}
            if (path == null) {
                return;
            }
			mNextMediaPlayer = new CompatMediaPlayer();
			mNextMediaPlayer.setWakeMode(mService.get(), PowerManager.PARTIAL_WAKE_LOCK);
			mNextMediaPlayer.setAudioSessionId(getAudioSessionId());
			mNextMediaPlayer.setOnPreparedListener(new OnPreparedListener() {
				@Override
				public void onPrepared(MediaPlayer mp) {
					preparedListener.onPrepared(mp);
					mNextPath = path;
					mCurrentMediaPlayer.setNextMediaPlayer(mNextMediaPlayer);
					if (onNextPreparedListener != null) {
						onNextPreparedListener.onPrepared(mp);
					}
				}
    		});
			mNextMediaPlayer.setOnErrorListener(new OnErrorListener() {
				
				@Override
				public boolean onError(MediaPlayer mp, int what, int extra) {
					// failed to open next, we'll transition the old fashioned way,
					// which will skip over the faulty file
					resetNext();
					if (onNextErrorListener != null) {
						onNextErrorListener.onError(mp, what, extra);
					}
					return true;
				}
			});
    		setDataSource(mNextMediaPlayer, path);
		}

		private void setDataSource(final MediaPlayer player, String path) {
			try {
				if (path.startsWith("content://")) {
					player.setDataSource(mService.get(), Uri.parse(path));
				} else {
					player.setDataSource(path);
				}
				player.setAudioStreamType(AudioManager.STREAM_MUSIC);
				player.prepareAsync();
			} catch (IOException todo) {
				// TODO: notify the user why the file couldn't be opened
			}
		}
		
		public boolean isInitialized() {
			return mIsInitialized;
		}

		public void start() {
			mCurrentMediaPlayer.start();
		}

		public void stop() {
			reset();
		}

		/**
		 * You CANNOT use this player anymore after calling release()
		 */
		public void release() {
			reset();
			mCurrentMediaPlayer.release();
		}

		public void pause() {
			mCurrentMediaPlayer.pause();
		}
		
		private void reset() {
			if (mCurrentMediaPlayer != null) {
				mCurrentMediaPlayer.release();
			}
			mCurrentMediaPlayer = new CompatMediaPlayer();
			mCurrentMediaPlayer.setWakeMode(mService.get(), PowerManager.PARTIAL_WAKE_LOCK);
			mCurrentPath = null;
			mIsInitialized = false;
			resetNext();
		}
		
		private void resetNext() {
			if (mNextMediaPlayer != null) {
				mNextMediaPlayer.release();
				mNextMediaPlayer = null;
			}
			mNextPath = null;
		}

		public void setHandler(Handler handler) {
			mHandler = handler;
		}
		
		public void setPreparedListener(final OnPreparedListener onPreparedListener) {
			onCurrentPreparedListener = onPreparedListener;
		}
		
		public void setErrorListener(final OnErrorListener onErrorListener) {
			onCurrentErrorListener = onErrorListener;
		}
		
		MediaPlayer.OnPreparedListener preparedListener = new MediaPlayer.OnPreparedListener() {

			@Override
			public void onPrepared(MediaPlayer mp) {
				mp.setOnCompletionListener(completionListener);
				mp.setOnErrorListener(errorListener);
				Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
				i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
				i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, mService.get().getPackageName());
				mService.get().sendBroadcast(i);
			}
		};

		MediaPlayer.OnCompletionListener completionListener = new MediaPlayer.OnCompletionListener() {
			public void onCompletion(MediaPlayer mp) {
				if (mp == mCurrentMediaPlayer && mNextMediaPlayer != null) {
					mCurrentMediaPlayer.release();
					mCurrentMediaPlayer = mNextMediaPlayer;
					mCurrentPath = mNextPath;
					mNextMediaPlayer = null;
					mNextPath = null;
					mHandler.sendEmptyMessage(TRACK_WENT_TO_NEXT);
				} else {
                    // Acquire a temporary wakelock, since when we return from
                    // this callback the MediaPlayer will release its wakelock
                    // and allow the device to go to sleep.
                    // This temporary wakelock is released when the RELEASE_WAKELOCK
                    // message is processed, but just in case, put a timeout on it.
					mService.get().mWakeLock.acquire(30000);
					mHandler.sendEmptyMessage(TRACK_ENDED);
					mHandler.sendEmptyMessage(RELEASE_WAKELOCK);
				}
			}
		};

		MediaPlayer.OnErrorListener errorListener = new MediaPlayer.OnErrorListener() {
			public boolean onError(MediaPlayer mp, int what, int extra) {
				switch (what) {
				case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
					mCurrentMediaPlayer.release();
					mCurrentPath = null;
					mIsInitialized = false;
                    // Creating a new MediaPlayer and settings its wakemode does not
                    // require the media service, so it's OK to do this now, while the
                    // service is still being restarted
					mCurrentMediaPlayer = new CompatMediaPlayer();
					mCurrentMediaPlayer.setWakeMode(mService.get(), PowerManager.PARTIAL_WAKE_LOCK);
					mHandler.sendMessageDelayed(mHandler.obtainMessage(SERVER_DIED), 2000);
					return true;
				default:
					if (DEBUG)
						Log.d("MultiPlayer", "Error: " + what + "," + extra);
					break;
				}
				return false;
			}
		};

		public long duration() {
			return mCurrentMediaPlayer.getDuration();
		}

		public long position() {
			return mCurrentMediaPlayer.getCurrentPosition();
		}

		public long seek(long whereto) {
			mCurrentMediaPlayer.seekTo((int) whereto);
			return whereto;
		}

		public void setVolume(float vol) {
			mCurrentMediaPlayer.setVolume(vol, vol);
		}

		public void setAudioSessionId(int sessionId) {
			mCurrentMediaPlayer.setAudioSessionId(sessionId);
		}

		public int getAudioSessionId() {
			return mCurrentMediaPlayer.getAudioSessionId();
		}
	}
	
    private static class CompatMediaPlayer extends MediaPlayer implements MediaPlayer.OnCompletionListener {

        private boolean mCompatMode;
        private MediaPlayer mCompatNextPlayer;
        private OnCompletionListener mCompletionListener;

        public CompatMediaPlayer() {
            try {
                MediaPlayer.class.getMethod("setNextMediaPlayer", MediaPlayer.class);
            } catch (NoSuchMethodException e) {
                mCompatMode = true;
                super.setOnCompletionListener(this);
            }
        }

        public void setNextMediaPlayer(MediaPlayer next) {
            if (mCompatMode) {
                mCompatNextPlayer = next;
            } else {
                super.setNextMediaPlayer(next);
            }
        }

        @Override
        public void setOnCompletionListener(OnCompletionListener listener) {
            if (mCompatMode) {
            	mCompletionListener = listener;
            } else {
                super.setOnCompletionListener(listener);
            }
        }

        @Override
        public void onCompletion(MediaPlayer mp) {
        	if (mCompatMode) {
                if (mCompatNextPlayer != null) {
                    // as it turns out, starting a new MediaPlayer on the completion
                    // of a previous player ends up slightly overlapping the two
                    // playbacks, so slightly delaying the start of the next player
                    // gives a better user experience
                    SystemClock.sleep(50);
                    mCompatNextPlayer.start();
                }
                mCompletionListener.onCompletion(this);
        	}
        }
    }
	
    /*
     * By making this a static class with a WeakReference to the Service, we
     * ensure that the Service can be GCd even when the system process still
     * has a remote reference to the stub.
     */
	private static final class ServiceStub extends IMediaPlaybackService.Stub {
        private final WeakReference<MediaPlaybackService> mService;
        
        private ServiceStub(MediaPlaybackService service) {
            mService = new WeakReference<MediaPlaybackService>(service);
        }

		public void playFile(String path) throws RemoteException {
			 mService.get().playFile(path);
		}
        public void open(Song [] list, int position) throws RemoteException {
            mService.get().open(list, position);
        }
        public int getQueuePosition() throws RemoteException {
            return mService.get().getQueuePosition();
        }
        public void setQueuePosition(int index) throws RemoteException {
            mService.get().setQueuePosition(index);
        }
        public boolean isPlaying() throws RemoteException {
            return mService.get().isPlaying();
        }
        public void stop() throws RemoteException {
            mService.get().stop(true);
        }
        public void pause() throws RemoteException {
            mService.get().pause();
        }
        public void play() throws RemoteException {
            mService.get().play();
        }
        public void prev() throws RemoteException {
            mService.get().prev();
        }
        public void next() throws RemoteException {
            mService.get().gotoNext(true);
        }
        public String getTrackName() throws RemoteException {
            return mService.get().getTrackName();
        }
        public String getAlbumName() throws RemoteException {
            return mService.get().getAlbumName();
        }
        public String getAlbumId() throws RemoteException {
            return mService.get().getAlbumId();
        }
        public String getArtistName() throws RemoteException {
            return mService.get().getArtistName();
        }
        public String getArtistId() throws RemoteException {
            return mService.get().getArtistId();
        }
        public void enqueue(Song [] list , int action) throws RemoteException {
            mService.get().enqueue(list, action);
        }
        public Song [] getQueue() throws RemoteException {
            return mService.get().getQueue();
        }
        public void moveQueueItem(int from, int to) throws RemoteException {
            mService.get().moveQueueItem(from, to);
        }
        public String getPath() throws RemoteException {
            return mService.get().getPath();
        }
        public String getAudioId() throws RemoteException {
            return mService.get().getAudioId();
        }
        public long position() throws RemoteException {
            return mService.get().position();
        }
        public long duration() throws RemoteException {
            return mService.get().duration();
        }
        public long seek(long pos) throws RemoteException {
            return mService.get().seek(pos);
        }
        public void setShuffleMode(int shufflemode) throws RemoteException {
            mService.get().setShuffleMode(shufflemode);
        }
        public int getShuffleMode() throws RemoteException {
            return mService.get().getShuffleMode();
        }
        public int removeTracks(int first, int last) throws RemoteException {
            return mService.get().removeTracks(first, last);
        }
        public int removeTrack(Song song) throws RemoteException {
            return mService.get().removeTrack(song);
        }
        public void setRepeatMode(int repeatmode) throws RemoteException {
            mService.get().setRepeatMode(repeatmode);
        }
        public int getRepeatMode() throws RemoteException {
            return mService.get().getRepeatMode();
        }
        public int getMediaMountedCount() throws RemoteException {
            return mService.get().getMediaMountedCount();
        }
        public int getAudioSessionId() throws RemoteException {
            return mService.get().getAudioSessionId();
        }
		public boolean isFavorite() throws RemoteException {
			return mService.get().isFavorite();
		}
		public void toggleFavorite() throws RemoteException {
			mService.get().toggleFavorite();
		}
		public void refresh() throws RemoteException {
			mService.get().refresh();
		}
	}
	
	private void prepareNext() {
		if (mPlayList == null || mNextPlayPos < 0) {
			return;
		}
		cancelRunningTask();
		Song song = mPlayList[mNextPlayPos];
    	if (!song.isQueried()) {
    		songTask = new QuerySongTask(){
				@Override
				protected void onPostExecute(Song song) {
					if (song != null) {
						mPlayer.setNextDataSource(song.getLinkPlay());
					}
				}
    		};
    		songTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, song);
    	} else {
    		mPlayer.setNextDataSource(song.getLinkPlay());
    	}
	}
	
    private void prepareAndPlayCurrent() {
    	if (mPlayList == null || mPlayPos < 0) {
            return;
        }
        stop(false);
        cancelRunningTask();
        Song song = mPlayList[mPlayPos];
    	if (!song.isQueried()) {
    		songTask = new QuerySongTask() {
				@Override
				protected void onPostExecute(Song song) {
					if (song != null) {
						notifyChange(META_CHANGED);
						updateNotification();
						playFile(song.getLinkPlay());
					}
				}
    		};
    		songTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, song);
    	} else {
			notifyChange(META_CHANGED);
			updateNotification();
    		playFile(song.getLinkPlay());
    	}
	}
	
    /**
     * Plays the specified file.
     *
     * @param path The full path of the file to be played.
     */
	public void playFile(final String path) {
		Log.d(LOGTAG, "Play file path = " + path);
        if (mPlayList == null || mPlayPos < 0) {
            ContentResolver resolver = getContentResolver();
            Uri uri;
            String where;
            String selectionArgs[];
            if (path.startsWith("content://media/")) {
                uri = Uri.parse(path);
                where = null;
                selectionArgs = null;
            } else {
               uri = MediaStore.Audio.Media.getContentUriForPath(path);
               where = MediaStore.Audio.Media.DATA + "=?";
               selectionArgs = new String[] { path };
            }
            
            try {
            	Cursor c = resolver.query(uri, new String[] {MediaStore.Audio.Media._ID}, where, selectionArgs, null);
                if  (c != null) {
                    if (c.getCount() > 0) {
                        c.moveToNext();
                        ensurePlayListCapacity(1);
                        mPlayListLen = 1;
                        mPlayOrder = new int[] {0};
                        mPlayList[0] = SongFactory.newSong(HostType.LOCAL, c.getString(0));
                        mPlayPos = 0;
                    }
                    c.close();
                    c = null;
                }
            } catch (UnsupportedOperationException ex) {
            }
        }
        mFileToPlay = path;
    	mPlayer.setPreparedListener(new OnPreparedListener() {
			
			@Override
			public void onPrepared(MediaPlayer mp) {
				if (mOpenFailedCounter > 10) {
					// reload queue
					long seekpos = mPreferences.getLong("seekpos", 0);
					seek(seekpos >= 0 && seekpos < duration() ? seekpos : 0);

					if (DEBUG) {
						Log.d(LOGTAG, "restored queue, currently at position "
			                    + position() + "/" + duration()
			                    + " (requested " + seekpos + ")");
					}
				}
				mOpenFailedCounter = 0;
				play();
				setNextTrack();
				notifyChange(META_CHANGED);
				updateNotification();
			}
		});
    	mPlayer.setErrorListener(new OnErrorListener() {
			@Override
			public boolean onError(MediaPlayer mp, int what, int extra) {
				// if we get here then opening the file failed. We're
				// either going to create a new one next, or stop trying
				if (mOpenFailedCounter < 10 &&  mPlayListLen > 1) {
					mOpenFailedCounter++;
					Log.w(LOGTAG, "Failed to open file for playback. Try count: " + mOpenFailedCounter);
					gotoNext(false);
				} else {
					if (mOpenFailedCounter > 10) {
						// couldn't restore the saved state when reload queue
						mPlayListLen = 0;
						mPlayOrder = new int[0];
					}
					mOpenFailedCounter = 0;
					stop(true);
				}
				return true;
			}
		});
    	mPlayer.setDataSource(mFileToPlay);
	}
	
	private class QuerySongTask extends AsyncTask<Song, Void, Song> {
		
		@Override
		protected Song doInBackground(Song... params) {
			if (params == null || params.length == 0 || params[0] == null) {
				return null;
			}
			params[0].query(getApplicationContext());
			return params[0];
		}
	}
	
	
	private void cancelRunningTask() {
		if (songTask != null && songTask.getStatus() != AsyncTask.Status.FINISHED) {
			songTask.cancel(true);
		}
		songTask = null;
	}
	
    private void shuffleArray(int[] a) {
        int n = a.length;
        Random random = new Random();
        random.nextInt();
        for (int i = 0; i < n; i++) {
          int change = i + random.nextInt(n - i);
          int helper = a[i];
          a[i] = a[change];
          a[change] = helper;
        }
      }
    
    private int[] generateArray(int offset, int len) {
    	int[] a = new int[len];
    	for (int i = 0; i < len; i++) {
    		a[i] = i + offset;
    	}
    	return a;
    }
    
    private int getPreShuffledPos() {
    	for (int i = 0; i < mPlayOrder.length; i++) {
    		if (mPlayPos == mPlayOrder[i]) {
    			return i;
    		}
    	}
    	return -1;
    }
    
    static int[] concat(int[]... arrays) {
        int lengh = 0;
        for (int[] array : arrays) {
            lengh += array.length;
        }
        int[] result = new int[lengh];
        int pos = 0;
        for (int[] array : arrays) {
            for (int element : array) {
                result[pos] = element;
                pos++;
            }
        }
        return result;
    }
    
    private int[] removeArray(int first, int last) {
    	int[] a = new int[mPlayOrder.length - last + first - 1];
    	int index = 0;
    	for (int i = 0; i < mPlayOrder.length; i++) {
    		if (mPlayOrder[i] > last) {
    			a[index] = mPlayOrder[i] - last + first - 1;
    			index++;
    		} else if (mPlayOrder[i] < first) {
    			a[index] = mPlayOrder[i];
    			index++;
    		}
    	}
    	return a;
    }
    
	public void shuffleAll() {
		Cursor cursor = SongLoader.makeSongCursor(getApplicationContext());
		final Song[] mTrackList = MusicUtils.getSongListForCursor(cursor);
		final int position = 0;
		if (mTrackList.length == 0) {
			return;
		}
		setShuffleMode(MediaPlaybackService.SHUFFLE_NORMAL);
		final String mCurrentId = getAudioId();
		final int mCurrentQueuePosition = getQueuePosition();
		if (position != -1 && mCurrentQueuePosition == position
				&& mTrackList[position].getId().equals(mCurrentId)) {
			final Song[] mPlaylist = getQueue();
			if (Arrays.equals(mTrackList, mPlaylist)) {
				play();
				return;
			}
		}
		open(mTrackList, -1);
		cursor.close();
		cursor = null;
	}
}
