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
import java.util.LinkedList;
import java.util.Random;
import java.util.TreeSet;
import java.util.Vector;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
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
import android.media.RemoteControlClient;
import android.media.RemoteControlClient.MetadataEditor;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
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
import android.widget.Toast;

import com.boko.vimusic.MediaButtonIntentReceiver;
import com.boko.vimusic.NotificationHelper;
import com.boko.vimusic.R;
import com.boko.vimusic.appwidgets.AppWidgetLarge;
import com.boko.vimusic.appwidgets.AppWidgetLargeAlternate;
import com.boko.vimusic.appwidgets.AppWidgetSmall;
import com.boko.vimusic.appwidgets.RecentWidgetProvider;
import com.boko.vimusic.cache.ImageCache;
import com.boko.vimusic.cache.ImageFetcher;
import com.boko.vimusic.model.Song;
import com.boko.vimusic.provider.FavoritesStore;
import com.boko.vimusic.provider.RecentStore;
import com.boko.vimusic.utils.ApolloUtils;
import com.boko.vimusic.utils.MusicUtils;

/**
 * Provides "background" audio playback capabilities, allowing the
 * user to switch between activities without stopping playback.
 */
/**
 * @author Phung Duc Kien
 *
 */
@SuppressLint("NewApi")
public class MediaPlaybackService extends Service {
	private static final boolean DEBUG = false;
    /** used to specify whether enqueue() should start playing
     * the new list of files right away, next or once all the currently
     * queued files have been played
     */
    public static final int NOW = 1;
    public static final int NEXT = 2;
    public static final int LAST = 3;
    public static final int PLAYBACKSERVICE_STATUS = 1;
    
    public static final int SHUFFLE_NONE = 0;
    public static final int SHUFFLE_NORMAL = 1;
    public static final int SHUFFLE_AUTO = 2;
    
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
	public static final String SHUTDOWN = SERVICECMD + ".shutdown";
	public static final String UPDATE_LOCKSCREEN = SERVICECMD + ".updatelockscreen";

    private static final int TRACK_ENDED = 1;
    private static final int RELEASE_WAKELOCK = 2;
    private static final int SERVER_DIED = 3;
    private static final int FOCUSCHANGE = 4;
    private static final int FADEDOWN = 5;
    private static final int FADEUP = 6;
    private static final int TRACK_WENT_TO_NEXT = 7;
    private static final int MAX_HISTORY_SIZE = 100;
    
    private MultiPlayer mPlayer;
    private String mFileToPlay;
    private int mShuffleMode = SHUFFLE_NONE;
    private int mRepeatMode = REPEAT_NONE;
    private int mMediaMountedCount = 0;
    private Song [] mAutoShuffleList = null;
    private Song [] mPlayList = null;
    private int mPlayListLen = 0;
    private Vector<Integer> mHistory = new Vector<Integer>(MAX_HISTORY_SIZE);
    private int mPlayPos = -1;
    private int mNextPlayPos = -1;
    private static final String LOGTAG = MediaPlaybackService.class.getSimpleName();
    private final Shuffler mRand = new Shuffler();
    private int mOpenFailedCounter = 0;
    private BroadcastReceiver mUnmountReceiver = null;
    private WakeLock mWakeLock;
    private int mServiceStartId = -1;
    private boolean mServiceInUse = false;
    private boolean mIsSupposedToBePlaying = false;
    private boolean mQuietMode = false;
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
    
	
	// Alarm intent for removing the notification when nothing is playing for
	// some time
	private AlarmManager mAlarmManager;
	private PendingIntent mShutdownIntent;
	private boolean mShutdownScheduled;
    
    private final IBinder mBinder = new ServiceStub(this);
    
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
			mMediaplayerHandler.obtainMessage(FOCUSCHANGE, focusChange, 0)
					.sendToTarget();
		}
	};
	
    public MediaPlaybackService() {
    }
	
	@Override
	public void onCreate() {
		if (DEBUG)
			Log.d(LOGTAG, "Creating service");
		super.onCreate();
		
		// Initialize the audio manager and register any headset controls for
		// playback
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

		// Flags for the media transport control that this client supports.
        int flags = RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS
                | RemoteControlClient.FLAG_KEY_MEDIA_NEXT
                | RemoteControlClient.FLAG_KEY_MEDIA_PLAY
                | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
                | RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
                | RemoteControlClient.FLAG_KEY_MEDIA_STOP;

		if (ApolloUtils.hasJellyBeanMR2()) {
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

		// Initialize the preferences
		mPreferences = getSharedPreferences("Service", 0);
		mCardId = getCardId();

		registerExternalStorageListener();

		// Start up the thread running the service. Note that we create a
		// separate thread because the service normally runs in the process's
		// main thread, which we don't want to block. We also make it
		// background priority so CPU-intensive work will not disrupt the UI.
		final HandlerThread thread = new HandlerThread("MusicPlayerHandler",
				android.os.Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();

		// Initialize the handler
		mMediaplayerHandler = new MusicPlayerHandler(this, thread.getLooper());
		
		// Initialize the media player
		mPlayer = new MultiPlayer(this);
		mPlayer.setHandler(mMediaplayerHandler);
		
		// Bring the queue back
		reloadQueue();
		notifyChange(QUEUE_CHANGED);
		notifyChange(META_CHANGED);

		// Initialize the intent filter and each action
        IntentFilter commandFilter = new IntentFilter();
        commandFilter.addAction(SERVICECMD);
        commandFilter.addAction(TOGGLEPAUSE_ACTION);
        commandFilter.addAction(PAUSE_ACTION);
        commandFilter.addAction(NEXT_ACTION);
        commandFilter.addAction(PREVIOUS_ACTION);
		commandFilter.addAction(STOP_ACTION);
		commandFilter.addAction(REPEAT_ACTION);
		commandFilter.addAction(SHUFFLE_ACTION);
		// Attach the broadcast listener
		registerReceiver(mIntentReceiver, commandFilter);

		// Initialize the wake lock
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
        mWakeLock.setReferenceCounted(false);

		// Initialize the favorites and recents databases
		mRecentsCache = RecentStore.getInstance(this);
		mFavoritesCache = FavoritesStore.getInstance(this);

		// Initialize the notification helper
		mNotificationHelper = new NotificationHelper(this);

		// Initialize the image fetcher
		mImageFetcher = ImageFetcher.getInstance(this);
		// Initialize the image cache
		mImageFetcher.setImageCache(ImageCache.getInstance(this));

		// Initialize the delayed shutdown intent
		final Intent shutdownIntent = new Intent(this,
				MediaPlaybackService.class);
		shutdownIntent.setAction(SHUTDOWN);

		mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		mShutdownIntent = PendingIntent.getService(this, 0, shutdownIntent, 0);

		// Listen for the idle state
		scheduleDelayedShutdown();
	}

	@Override
	public void onDestroy() {
		if (DEBUG)
			Log.d(LOGTAG, "Destroying service");
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
		mMediaplayerHandler.removeCallbacksAndMessages(null);

        unregisterReceiver(mIntentReceiver);
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
            mUnmountReceiver = null;
        }
        mWakeLock.release();
        
		// remove any pending alarms
		mAlarmManager.cancel(mShutdownIntent);
        super.onDestroy();
	}
	
	private void saveQueue(final boolean full) {
        if (!mQueueIsSaveable) {
            return;
        }

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
                // In shuffle mode we need to save the history too
                len = mHistory.size();
                q.setLength(0);
                for (int i = 0; i < len; i++) {
                    int n = mHistory.get(i);
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
                ed.putString("history", q.toString());
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
				if (parts != null && parts.length > 0) {
					ensurePlayListCapacity(plen + 1);
					if (parts.length == 1 || parts[1] == null) {
						mPlayList[plen] = new Song(parts[0], "", null, null, 0);
					} else {
						mPlayList[plen] = new Song(parts[0], parts[1], null,
								null, 0);
					}
					plen++;
				}
			}
			mPlayListLen = plen;
			final int pos = mPreferences.getInt("curpos", 0);
			if (pos < 0 || pos >= mPlayListLen) {
				// The saved playlist is bogus, discard it
				mPlayListLen = 0;
				return;
			}
			mPlayPos = pos;
			
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
			synchronized (this) {
				mOpenFailedCounter = 20;
				mQuietMode = true;
				openCurrentAndNext();
				mQuietMode = false;
			}
			if (!mPlayer.isInitialized()) {
				// couldn't restore the saved state
				mPlayListLen = 0;
				return;
			}

			long seekpos = mPreferences.getLong("seekpos", 0);
			seek(seekpos >= 0 && seekpos < duration() ? seekpos : 0);

			if (DEBUG) {
				Log.d(LOGTAG, "restored queue, currently at position "
	                    + position() + "/" + duration()
	                    + " (requested " + seekpos + ")");
			}

			int repmode = mPreferences.getInt("repeatmode", REPEAT_NONE);
            if (repmode != REPEAT_ALL && repmode != REPEAT_CURRENT) {
                repmode = REPEAT_NONE;
            }
            mRepeatMode = repmode;

            int shufmode = mPreferences.getInt("shufflemode", SHUFFLE_NONE);
            if (shufmode != SHUFFLE_AUTO && shufmode != SHUFFLE_NORMAL) {
                shufmode = SHUFFLE_NONE;
            }
			if (shufmode != SHUFFLE_NONE) {
				// in shuffle mode we need to restore the history too
				q = mPreferences.getString("history", "");
				qlen = q != null ? q.length() : 0;
				if (qlen > 1) {
					plen = 0;
					n = 0;
					shift = 0;
					mHistory.clear();
					for (int i = 0; i < qlen; i++) {
						final char c = q.charAt(i);
						if (c == ';') {
							if (n >= mPlayListLen) {
								mHistory.clear();
								break;
							}
							mHistory.add(n);
							n = 0;
							shift = 0;
						} else {
							if (c >= '0' && c <= '9') {
								n += c - '0' << shift;
							} else if (c >= 'a' && c <= 'f') {
								n += 10 + c - 'a' << shift;
							} else {
								mHistory.clear();
								break;
							}
							shift += 4;
						}
					}
				}
			}
            if (shufmode == SHUFFLE_AUTO) {
                if (! makeAutoShuffleList()) {
                    shufmode = SHUFFLE_NONE;
                }
            }
            mShuffleMode = shufmode;
		}
	}

	@Override
	public IBinder onBind(final Intent intent) {
		if (DEBUG)
			Log.d(LOGTAG, "Service bound, intent = " + intent);
		cancelShutdown();
        mServiceInUse = true;
        return mBinder;
	}
	
	@Override
	public void onRebind(final Intent intent) {
		cancelShutdown();
		mServiceInUse = true;
	}
	
	@Override
	public int onStartCommand(final Intent intent, final int flags,
			final int startId) {
		if (DEBUG)
			Log.d(LOGTAG, "Got new intent " + intent + ", startId = " + startId);
		mServiceStartId = startId;
		cancelShutdown();

		if (intent != null) {
			String action = intent.getAction();

			if (intent.hasExtra(NOW_IN_FOREGROUND)) {
				mAnyActivityInForeground = intent.getBooleanExtra(
						NOW_IN_FOREGROUND, false);
				updateNotification();
			}

			if (SHUTDOWN.equals(action)) {
				mShutdownScheduled = false;
				releaseServiceUiAndStop();
				return START_NOT_STICKY;
			}

			handleCommandIntent(intent);
		}

        // make sure the service will shut down on its own if it was
        // just started but not bound to and nothing is playing
		scheduleDelayedShutdown();
		return START_STICKY;
	}
	
	private void handleCommandIntent(Intent intent) {
		String action = intent.getAction();
		String cmd = intent.getStringExtra(CMDNAME);
		if (DEBUG)
			Log.d(LOGTAG, "mIntentReceiver.onReceive " + action + " / " + cmd);

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
			releaseServiceUiAndStop();
		} else if (REPEAT_ACTION.equals(action)) {
			cycleRepeat();
		} else if (SHUFFLE_ACTION.equals(action)) {
			cycleShuffle();
		}
	}

	@Override
	public boolean onUnbind(final Intent intent) {
		if (DEBUG)
			Log.d(LOGTAG, "Service unbound");
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
        	scheduleDelayedShutdown();
            return true;
        }
        
        // No active playlist, OK to stop the service right now
        stopSelf(mServiceStartId);
        return true;
	}

	private void releaseServiceUiAndStop() {
        // Check again to make sure nothing is playing right now
        if (isPlaying() || mPausedByTransientLossOfFocus
                || mMediaplayerHandler.hasMessages(TRACK_ENDED)) {
            return;
        }

		if (DEBUG)
			Log.d(LOGTAG, "Nothing is playing anymore, releasing notification");
		mNotificationHelper.killNotification();
		mAudioManager.abandonAudioFocus(mAudioFocusListener);

		if (!mServiceInUse) {
            // save the queue again, because it might have changed
            // since the user exited the music app (because of
            // party-shuffle or because the play-position changed)
			saveQueue(true);
			stopSelf(mServiceStartId);
		}
	}
	
    /**
     * Called when we receive a ACTION_MEDIA_EJECT notification.
     *
     * @param storagePath path to mount point for the removed media
     */
    public void closeExternalStorageFiles(String storagePath) {
        // stop playback and clean up if the SD card is going to be unmounted.
        stop(true);
        notifyChange(QUEUE_CHANGED);
        notifyChange(META_CHANGED);
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
                        saveQueue(true);
                        mQueueIsSaveable = false;
                        closeExternalStorageFiles(intent.getData().getPath());
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                        mMediaMountedCount++;
                        mCardId = getCardId();
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
		if (DEBUG)
			Log.d(LOGTAG, "notifyChange: what = " + what);

		Intent i = new Intent(what);
		i.putExtra("id", getAudioId());
        i.putExtra("artist", getArtistName());
        i.putExtra("album",getAlbumName());
        i.putExtra("track", getTrackName());
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
			if (!mFavoritesCache.isFavoriteSong(getAudioId(), "")) {
				mFavoritesCache.addSong(getAudioId(), "", getTrackName(),
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
                    openCurrentAndNext();
                    play();
                    notifyChange(META_CHANGED);
                    return;
                }
            }
            if (mPlayPos < 0) {
                mPlayPos = 0;
                openCurrentAndNext();
                play();
                notifyChange(META_CHANGED);
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
            if (mShuffleMode == SHUFFLE_AUTO) {
                mShuffleMode = SHUFFLE_NORMAL;
            }
            String oldId = getAudioId();
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
                mPlayPos = mRand.nextInt(mPlayListLen);
            }
            mHistory.clear();

            openCurrentAndNext();
            if (oldId != getAudioId()) {
                notifyChange(META_CHANGED);
            }
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
     * @return An array of integers containing the IDs of the tracks in the play list
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
	
    private void openCurrentAndNext() {
    	openCurrentAndMaybeNext(true);
	}
    
    private void openCurrentAndMaybeNext(boolean prepareNext) {
        synchronized (this) {
            if (mPlayListLen == 0) {
                return;
            }
            stop(false);

            while(true) {
				if (open(mPlayList[mPlayPos].getLinkPlay())) {
					break;
				}
				// if we get here then opening the file failed. We're
				// either going to create a new one next, or stop trying
				if (mOpenFailedCounter++ < 10 &&  mPlayListLen > 1) {
					int pos = getNextPosition(false);
					if (pos < 0) {
						scheduleDelayedShutdown();
                        if (mIsSupposedToBePlaying) {
                            mIsSupposedToBePlaying = false;
                            notifyChange(PLAYSTATE_CHANGED);
                        }
                        return;
					}
                    mPlayPos = pos;
                    stop(false);
                    mPlayPos = pos;
				} else {
					mOpenFailedCounter = 0;
                    if (!mQuietMode) {
                        Toast.makeText(this, R.string.playback_failed, Toast.LENGTH_SHORT).show();
                    }
                    Log.w(LOGTAG, "Failed to open file for playback");
					scheduleDelayedShutdown();
					if (mIsSupposedToBePlaying) {
						mIsSupposedToBePlaying = false;
						notifyChange(PLAYSTATE_CHANGED);
					}
					return;
				}
			}
            if (prepareNext) {
            	setNextTrack();
            }
		}
	}
    
	private void setNextTrack() {
		mNextPlayPos = getNextPosition(false);
		if (DEBUG)
			Log.d(LOGTAG, "setNextTrack: next play position = " + mNextPlayPos);
		if (mPlayList != null && mNextPlayPos >= 0) {
			final Song song = mPlayList[mNextPlayPos];
			mPlayer.setNextDataSource(song.getLinkPlay());
		} else {
			mPlayer.setNextDataSource(null);
		}
	}
	
    /**
     * Opens the specified file and readies it for playback.
     *
     * @param path The full path of the file to be opened.
     */
	public boolean open(final String path) {
		if (DEBUG)
			Log.d(LOGTAG, "open: path = " + path);
        synchronized (this) {
            if (path == null) {
                return false;
            }
            
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
                            mPlayList[0] = new Song(c.getString(0), "", null,
									null, 0);
                            mPlayPos = 0;
                        }
                        c.close();
                        c = null;
                    }
                } catch (UnsupportedOperationException ex) {
                }
            }
            mFileToPlay = path;
            mPlayer.setDataSource(mFileToPlay);
            if (mPlayer.isInitialized()) {
                mOpenFailedCounter = 0;
                return true;
            }
            stop(true);
            return false;
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

			updateNotification();
            if (!mIsSupposedToBePlaying) {
                mIsSupposedToBePlaying = true;
                notifyChange(PLAYSTATE_CHANGED);
            }
			cancelShutdown();
        } else if (mPlayListLen <= 0) {
            // This is mostly so that if you press 'play' on a bluetooth headset
            // without every having played anything before, it will still play
            // something.
        	setShuffleMode(SHUFFLE_AUTO);
		}
	}

	private void updateNotification() {
		if (!mAnyActivityInForeground && isPlaying()) {
			mNotificationHelper.buildNotification(getAlbumName(),
					getArtistName(), getTrackName(), getAlbumId(),
					getAlbumArt(), isPlaying());
		} else if (mAnyActivityInForeground) {
			mNotificationHelper.killNotification();
		}
	}
	
	private void stop(final boolean goToIdle) {
		if (DEBUG)
			Log.d(LOGTAG, "Stopping playback, goToIdle = " + goToIdle);
        if (mPlayer != null && mPlayer.isInitialized()) {
            mPlayer.stop();
        }
        mFileToPlay = null;
        if (goToIdle) {
        	scheduleDelayedShutdown();
        } else {
            stopForeground(false);
        }
        if (goToIdle) {
            mIsSupposedToBePlaying = false;
        }
	}
	
    /**
     * Stops playback.
     */
    public void stop() {
        stop(true);
    }
    
    /**
     * Pauses playback (call play() to resume)
     */
	public void pause() {
		if (DEBUG)
			Log.d(LOGTAG, "Pausing playback");
		synchronized (this) {
			mMediaplayerHandler.removeMessages(FADEUP);
			if (isPlaying()) {
				mPlayer.pause();
				scheduleDelayedShutdown();
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
		if (DEBUG)
			Log.d(LOGTAG, "Going to previous track");
		synchronized (this) {
            if (mShuffleMode == SHUFFLE_NORMAL) {
                // go to previously-played track and remove it from the history
                int histsize = mHistory.size();
                if (histsize == 0) {
                    // prev is a no-op
                    return;
                }
                Integer pos = mHistory.remove(histsize - 1);
                mPlayPos = pos.intValue();
            } else {
                if (mPlayPos > 0) {
                    mPlayPos--;
                } else {
                    mPlayPos = mPlayListLen - 1;
                }
            }
            stop(false);
            openCurrentAndNext();
            play();
            notifyChange(META_CHANGED);
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
       } else if (mShuffleMode == SHUFFLE_NORMAL) {
           // Pick random next track from the not-yet-played ones
           // TODO: make it work right after adding/removing items in the queue.

           // Store the current file in the history, but keep the history at a
           // reasonable size
           if (mPlayPos >= 0) {
               mHistory.add(mPlayPos);
           }
           if (mHistory.size() > MAX_HISTORY_SIZE) {
               mHistory.removeElementAt(0);
           }

           int numTracks = mPlayListLen;
           int[] tracks = new int[numTracks];
           for (int i=0;i < numTracks; i++) {
               tracks[i] = i;
           }

           int numHistory = mHistory.size();
           int numUnplayed = numTracks;
           for (int i=0;i < numHistory; i++) {
               int idx = mHistory.get(i).intValue();
               if (idx < numTracks && tracks[idx] >= 0) {
                   numUnplayed--;
                   tracks[idx] = -1;
               }
           }

           // 'numUnplayed' now indicates how many tracks have not yet
           // been played, and 'tracks' contains the indices of those
           // tracks.
           if (numUnplayed <=0) {
               // everything's already been played
               if (mRepeatMode == REPEAT_ALL || force) {
                   //pick from full set
                   numUnplayed = numTracks;
                   for (int i=0;i < numTracks; i++) {
                       tracks[i] = i;
                   }
               } else {
                   // all done
                   return -1;
               }
           }
           int skip = mRand.nextInt(numUnplayed);
           int cnt = -1;
           while (true) {
               while (tracks[++cnt] < 0)
                   ;
               skip--;
               if (skip < 0) {
                   break;
               }
           }
           return cnt;
       } else if (mShuffleMode == SHUFFLE_AUTO) {
           doAutoShuffleUpdate();
           return mPlayPos + 1;
       } else {
           if (mPlayPos >= mPlayListLen - 1) {
               // we're at the end of the list
               if (mRepeatMode == REPEAT_NONE && !force) {
                   // all done
                   return -1;
               } else if (mRepeatMode == REPEAT_ALL || force) {
                   return 0;
               }
               return -1;
           } else {
               return mPlayPos + 1;
           }
       }
   }
   
   public void gotoNext(boolean force) {
		if (DEBUG)
			Log.d(LOGTAG, "Going to next track");
		synchronized (this) {
			if (mPlayListLen <= 0) {
				if (DEBUG)
					Log.d(LOGTAG, "No play queue");
				scheduleDelayedShutdown();
				return;
			}

            int pos = getNextPosition(force);
            if (pos < 0) {
				scheduleDelayedShutdown();
                if (mIsSupposedToBePlaying) {
                    mIsSupposedToBePlaying = false;
                    notifyChange(PLAYSTATE_CHANGED);
                }
                return;
			}
			mPlayPos = pos;
			stop(false);
			mPlayPos = pos;
			openCurrentAndNext();
			play();
			notifyChange(META_CHANGED);
		}
	}
   
   private void scheduleDelayedShutdown() {
		if (DEBUG)
			Log.d(LOGTAG, "Scheduling shutdown in " + IDLE_DELAY + " ms");
		mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime() + IDLE_DELAY, mShutdownIntent);
		mShutdownScheduled = true;
	}
   
   // Make sure there are at least 5 items after the currently playing item
   // and no more than 10 items before.
	private void doAutoShuffleUpdate() {
        boolean notify = false;

        // remove old entries
        if (mPlayPos > 10) {
            removeTracks(0, mPlayPos - 9);
            notify = true;
        }
        // add new entries if needed
        int to_add = 7 - (mPlayListLen - (mPlayPos < 0 ? -1 : mPlayPos));
        for (int i = 0; i < to_add; i++) {
            // pick something at random from the list

            int lookback = mHistory.size();
            int idx = -1;
            while(true) {
                idx = mRand.nextInt(mAutoShuffleList.length);
                if (!wasRecentlyUsed(idx, lookback)) {
                    break;
                }
                lookback /= 2;
            }
            mHistory.add(idx);
            if (mHistory.size() > MAX_HISTORY_SIZE) {
                mHistory.remove(0);
            }
            ensurePlayListCapacity(mPlayListLen + 1);
            mPlayList[mPlayListLen++] = mAutoShuffleList[idx];
            notify = true;
        }
        if (notify) {
            notifyChange(QUEUE_CHANGED);
        }
	}
	
    // check that the specified idx is not in the history (but only look at at
    // most lookbacksize entries in the history)
	private boolean wasRecentlyUsed(final int idx, int lookbacksize) {

        // early exit to prevent infinite loops in case idx == mPlayPos
        if (lookbacksize == 0) {
            return false;
        }

        int histsize = mHistory.size();
        if (histsize < lookbacksize) {
            if (DEBUG) Log.d(LOGTAG, "lookback too big");
            lookbacksize = histsize;
        }
        int maxidx = histsize - 1;
        for (int i = 0; i < lookbacksize; i++) {
            long entry = mHistory.get(maxidx - i);
            if (entry == idx) {
                return true;
            }
        }
        return false;
	}
	
	private boolean makeAutoShuffleList() {
        ContentResolver res = getContentResolver();
        Cursor c = null;
        try {
            c = res.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[] {MediaStore.Audio.Media._ID}, MediaStore.Audio.Media.IS_MUSIC + "=1",
                    null, null);
            if (c == null || c.getCount() == 0) {
                return false;
            }
            int len = c.getCount();
            Song [] list = new Song[len];
            for (int i = 0; i < len; i++) {
                c.moveToNext();
                list[i] = new Song(c.getString(0), "", null, null, 0);
            }
            mAutoShuffleList = list;
            return true;
        } catch (RuntimeException ex) {
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
        return false;
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

			if (gotonext) {
				if (mPlayListLen == 0) {
					stop(true);
					mPlayPos = -1;
				} else {
                    if (mPlayPos >= mPlayListLen) {
                        mPlayPos = 0;
                    }
                    boolean wasPlaying = isPlaying();
                    stop(false);
                    openCurrentAndNext();
                    if (wasPlaying) {
                        play();
                    }
				}
				notifyChange(META_CHANGED);
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
            mShuffleMode = shufflemode;
            if (mShuffleMode == SHUFFLE_AUTO) {
				if (makeAutoShuffleList()) {
					mPlayListLen = 0;
					doAutoShuffleUpdate();
					mPlayPos = 0;
					openCurrentAndNext();
					play();
					notifyChange(META_CHANGED);
					return;
				} else {
					// failed to build a list of files to shuffle
					mShuffleMode = SHUFFLE_NONE;
				}
			}
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
	 * @return A card ID used to save and restore playlists, i.e., the queue.
	 */
	private int getCardId() {
		final ContentResolver resolver = getContentResolver();
		Cursor cursor = resolver.query(
				Uri.parse("content://media/external/fs_id"), null, null, null,
				null);
		int mCardId = -1;
		if (cursor != null && cursor.moveToFirst()) {
			mCardId = cursor.getInt(0);
			cursor.close();
			cursor = null;
		}
		return mCardId;
	}



	private void cancelShutdown() {
		if (DEBUG)
			Log.d(LOGTAG, "Cancelling delayed shutdown, scheduled = "
					+ mShutdownScheduled);
		if (mShutdownScheduled) {
			mAlarmManager.cancel(mShutdownIntent);
			mShutdownScheduled = false;
		}
	}











	/**
	 * Updates the lockscreen controls.
	 * 
	 * @param what
	 *            The broadcast
	 */
	private void updateRemoteControlClient(final String what) {
		int playState = isPlaying() ? RemoteControlClient.PLAYSTATE_PLAYING : RemoteControlClient.PLAYSTATE_PAUSED;

		if (ApolloUtils.hasJellyBeanMR2()
				&& (what.equals(PLAYSTATE_CHANGED) || what
						.equals(POSITION_CHANGED))) {
			mRemoteControlClient.setPlaybackState(playState, position(), 1.0f);
		} else if (what.equals(PLAYSTATE_CHANGED)) {
			mRemoteControlClient.setPlaybackState(playState);
		} else if (what.equals(META_CHANGED) || what.equals(QUEUE_CHANGED)) {
            RemoteControlClient.MetadataEditor ed = mRemoteControlClient.editMetadata(true);
            ed.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, getTrackName());
            ed.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, getAlbumName());
            ed.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, getArtistName());
            ed.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, duration());
            ed.putString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, getAlbumArtistName());
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

			if (ApolloUtils.hasJellyBeanMR2()) {
				mRemoteControlClient.setPlaybackState(playState, position(), 1.0f);
			}
		}
	}

	

	/**
	 * Returns the audio session ID
	 * 
	 * @return The current media player audio session ID
	 */
	public int getAudioSessionId() {
		synchronized (this) {
			return mPlayer.getAudioSessionId();
		}
	}





	/**
	 * Returns the position in the queue
	 * 
	 * @return the current position in the queue
	 */
	public int getQueuePosition() {
		synchronized (this) {
			return mPlayPos;
		}
	}

	/**
	 * Returns the path to current song
	 * 
	 * @return The path to the current song
	 */
	public String getPath() {
		synchronized (this) {
			if (mPlayPos >= 0 && mPlayer.isInitialized()) {
				return mPlayList[mPlayPos].getLinkPlay();
			}
		}
		return null;
	}

	/**
	 * Returns the album name
	 * 
	 * @return The current song album Name
	 */
	public String getAlbumName() {
		synchronized (this) {
			if (mPlayPos >= 0 && mPlayer.isInitialized()) {
				return mPlayList[mPlayPos].mAlbumName;
			}
		}
		return null;
	}

	/**
	 * Returns the song name
	 * 
	 * @return The current song name
	 */
	public String getTrackName() {
		synchronized (this) {
			if (mPlayPos >= 0 && mPlayer.isInitialized()) {
				return mPlayList[mPlayPos].getName();
			}
		}
		return null;
	}

	/**
	 * Returns the artist name
	 * 
	 * @return The current song artist name
	 */
	public String getArtistName() {
		synchronized (this) {
			if (mPlayPos >= 0 && mPlayer.isInitialized()) {
				return mPlayList[mPlayPos].mArtistName;
			}
		}
		return null;
	}

	/**
	 * Returns the artist name
	 * 
	 * @return The current song artist name
	 */
	public String getAlbumArtistName() {
		synchronized (this) {
			if (mPlayPos >= 0 && mPlayer.isInitialized()) {
				return null;
			}
		}
		return null;
	}

	/**
	 * Returns the album ID
	 * 
	 * @return The current song album ID
	 */
	public String getAlbumId() {
		synchronized (this) {
			if (mPlayPos >= 0 && mPlayer.isInitialized()) {
				return null;
			}
		}
		return null;
	}

	/**
	 * Returns the artist ID
	 * 
	 * @return The current song artist ID
	 */
	public String getArtistId() {
		synchronized (this) {
			if (mPlayPos >= 0 && mPlayer.isInitialized()) {
				return null;
			}
		}
		return null;
	}

	/**
	 * Returns the current audio ID
	 * 
	 * @return The current track ID
	 */
	public String getAudioId() {
		synchronized (this) {
			if (mPlayPos >= 0 && mPlayer.isInitialized()) {
				return mPlayList[mPlayPos].getId();
			}
		}
		return null;
	}

	/**
	 * Seeks the current track to a specific time
	 * 
	 * @param position
	 *            The time to seek to
	 * @return The time to play the track at
	 */
	public long seek(long position) {
		if (mPlayer.isInitialized()) {
			if (position < 0) {
				position = 0;
			} else if (position > mPlayer.duration()) {
				position = mPlayer.duration();
			}
			long result = mPlayer.seek(position);
			notifyChange(POSITION_CHANGED);
			return result;
		}
		return -1;
	}

	/**
	 * Returns the current position in time of the currenttrack
	 * 
	 * @return The current playback position in miliseconds
	 */
	public long position() {
		if (mPlayer.isInitialized()) {
			return mPlayer.position();
		}
		return -1;
	}

	/**
	 * Returns the full duration of the current track
	 * 
	 * @return The duration of the current track in miliseconds
	 */
	public long duration() {
		if (mPlayer.isInitialized()) {
			return mPlayer.duration();
		}
		return -1;
	}


	/**
	 * True if the current track is a "favorite", false otherwise
	 */
	public boolean isFavorite() {
		if (mFavoritesCache != null) {
			synchronized (this) {
				return mFavoritesCache.isFavoriteSong(getAudioId(), "");
			}
		}
		return false;
	}







	/**
	 * Toggles the current song as a favorite.
	 */
	public void toggleFavorite() {
		if (mFavoritesCache != null) {
			synchronized (this) {
				mFavoritesCache.toggleSong(getAudioId(), "", getTrackName(),
						getAlbumName(), getArtistName());
			}
		}
	}





	/**
	 * Sets the position of a track in the queue
	 * 
	 * @param index
	 *            The position to place the track
	 */
	public void setQueuePosition(final int index) {
		synchronized (this) {
			stop(false);
			mPlayPos = index;
			openCurrentAndNext();
			play();
			notifyChange(META_CHANGED);
			if (mShuffleMode == SHUFFLE_AUTO) {
				doAutoShuffleUpdate();
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
		} else if (mShuffleMode == SHUFFLE_NORMAL
				|| mShuffleMode == SHUFFLE_AUTO) {
			setShuffleMode(SHUFFLE_NONE);
		}
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

	/**
	 * Called when one of the lists should refresh or requery.
	 */
	public void refresh() {
		notifyChange(REFRESH);
	}



	private static final class MusicPlayerHandler extends Handler {
		private final WeakReference<MediaPlaybackService> mService;
		private float mCurrentVolume = 1.0f;

		/**
		 * Constructor of <code>MusicPlayerHandler</code>
		 * 
		 * @param service
		 *            The service to use.
		 * @param looper
		 *            The thread to run on.
		 */
		public MusicPlayerHandler(final MediaPlaybackService service,
				final Looper looper) {
			super(looper);
			mService = new WeakReference<MediaPlaybackService>(service);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void handleMessage(final Message msg) {
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
					service.openCurrentAndNext();
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

    // A simple variation of Random that makes sure that the
    // value it returns is not equal to the values it returned
    // previously, unless the interval is 1.
	private static final class Shuffler {
        private int mPrevious;
        private Random mRandom = new Random();
		private final LinkedList<Integer> mHistory = new LinkedList<Integer>();
		public int nextInt(int interval) {
            int ret;
            do {
                ret = mRandom.nextInt(interval);
			} while (ret == mPrevious && !mHistory.contains(Integer.valueOf(ret)) && interval > 1);
			mPrevious = ret;
			mHistory.add(mPrevious);
			cleanUpHistory();
			return ret;
		}

		// Removes old tracks and cleans up the history preparing for new tracks
		// to be added to the mapping
		private void cleanUpHistory() {
			if (!mHistory.isEmpty()
					&& mHistory.size() >= MAX_HISTORY_SIZE) {
				for (int i = 0; i < Math.max(1, MAX_HISTORY_SIZE / 2); i++) {
					mHistory.removeFirst();
				}
			}
		}
	};

	private static final class MultiPlayer implements
			MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {

		private final WeakReference<MediaPlaybackService> mService;

		private MediaPlayer mCurrentMediaPlayer = new MediaPlayer();

		private MediaPlayer mNextMediaPlayer;

		private Handler mHandler;

		private boolean mIsInitialized = false;

		/**
		 * Constructor of <code>MultiPlayer</code>
		 */
		public MultiPlayer(final MediaPlaybackService service) {
			mService = new WeakReference<MediaPlaybackService>(service);
			mCurrentMediaPlayer.setWakeMode(mService.get(),
					PowerManager.PARTIAL_WAKE_LOCK);
		}

		/**
		 * @param path
		 *            The path of the file, or the http/rtsp URL of the stream
		 *            you want to play
		 */
		public void setDataSource(final String path) {
			mIsInitialized = setDataSourceImpl(mCurrentMediaPlayer, path);
			if (mIsInitialized) {
				setNextDataSource(null);
			}
		}

		/**
		 * @param player
		 *            The {@link MediaPlayer} to use
		 * @param path
		 *            The path of the file, or the http/rtsp URL of the stream
		 *            you want to play
		 * @return True if the <code>player</code> has been prepared and is
		 *         ready to play, false otherwise
		 */
		private boolean setDataSourceImpl(final MediaPlayer player,
				final String path) {
			try {
				player.reset();
				player.setOnPreparedListener(null);
				if (path.startsWith("content://")) {
					player.setDataSource(mService.get(), Uri.parse(path));
				} else {
					player.setDataSource(path);
				}
				player.setAudioStreamType(AudioManager.STREAM_MUSIC);
				player.prepare();
			} catch (final IOException todo) {
				// TODO: notify the user why the file couldn't be opened
				return false;
			} catch (final IllegalArgumentException todo) {
				// TODO: notify the user why the file couldn't be opened
				return false;
			}
			player.setOnCompletionListener(this);
			player.setOnErrorListener(this);
			final Intent intent = new Intent(
					AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
			intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION,
					getAudioSessionId());
			intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, mService.get()
					.getPackageName());
			mService.get().sendBroadcast(intent);
			return true;
		}

		/**
		 * Set the MediaPlayer to start when this MediaPlayer finishes playback.
		 * 
		 * @param path
		 *            The path of the file, or the http/rtsp URL of the stream
		 *            you want to play
		 */
		public void setNextDataSource(final String path) {
			try {
				mCurrentMediaPlayer.setNextMediaPlayer(null);
			} catch (IllegalArgumentException e) {
				Log.i(LOGTAG, "Next media player is current one, continuing");
			} catch (IllegalStateException e) {
				Log.e(LOGTAG, "Media player not initialized!");
				return;
			}
			if (mNextMediaPlayer != null) {
				mNextMediaPlayer.release();
				mNextMediaPlayer = null;
			}
			if (path == null) {
				return;
			}
			mNextMediaPlayer = new MediaPlayer();
			mNextMediaPlayer.setWakeMode(mService.get(),
					PowerManager.PARTIAL_WAKE_LOCK);
			mNextMediaPlayer.setAudioSessionId(getAudioSessionId());
			if (setDataSourceImpl(mNextMediaPlayer, path)) {
				mCurrentMediaPlayer.setNextMediaPlayer(mNextMediaPlayer);
			} else {
				if (mNextMediaPlayer != null) {
					mNextMediaPlayer.release();
					mNextMediaPlayer = null;
				}
			}
		}

		/**
		 * Sets the handler
		 * 
		 * @param handler
		 *            The handler to use
		 */
		public void setHandler(final Handler handler) {
			mHandler = handler;
		}

		/**
		 * @return True if the player is ready to go, false otherwise
		 */
		public boolean isInitialized() {
			return mIsInitialized;
		}

		/**
		 * Starts or resumes playback.
		 */
		public void start() {
			mCurrentMediaPlayer.start();
		}

		/**
		 * Resets the MediaPlayer to its uninitialized state.
		 */
		public void stop() {
			mCurrentMediaPlayer.reset();
			mIsInitialized = false;
		}

		/**
		 * Releases resources associated with this MediaPlayer object.
		 */
		public void release() {
			stop();
			mCurrentMediaPlayer.release();
		}

		/**
		 * Pauses playback. Call start() to resume.
		 */
		public void pause() {
			mCurrentMediaPlayer.pause();
		}

		/**
		 * Gets the duration of the file.
		 * 
		 * @return The duration in milliseconds
		 */
		public long duration() {
			return mCurrentMediaPlayer.getDuration();
		}

		/**
		 * Gets the current playback position.
		 * 
		 * @return The current position in milliseconds
		 */
		public long position() {
			return mCurrentMediaPlayer.getCurrentPosition();
		}

		/**
		 * Gets the current playback position.
		 * 
		 * @param whereto
		 *            The offset in milliseconds from the start to seek to
		 * @return The offset in milliseconds from the start to seek to
		 */
		public long seek(final long whereto) {
			mCurrentMediaPlayer.seekTo((int) whereto);
			return whereto;
		}

		/**
		 * Sets the volume on this player.
		 * 
		 * @param vol
		 *            Left and right volume scalar
		 */
		public void setVolume(final float vol) {
			mCurrentMediaPlayer.setVolume(vol, vol);
		}

		/**
		 * Sets the audio session ID.
		 * 
		 * @param sessionId
		 *            The audio session ID
		 */
		public void setAudioSessionId(final int sessionId) {
			mCurrentMediaPlayer.setAudioSessionId(sessionId);
		}

		/**
		 * Returns the audio session ID.
		 * 
		 * @return The current audio session ID.
		 */
		public int getAudioSessionId() {
			return mCurrentMediaPlayer.getAudioSessionId();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean onError(final MediaPlayer mp, final int what,
				final int extra) {
			switch (what) {
			case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
				mIsInitialized = false;
				mCurrentMediaPlayer.release();
				mCurrentMediaPlayer = new MediaPlayer();
				mCurrentMediaPlayer.setWakeMode(mService.get(),
						PowerManager.PARTIAL_WAKE_LOCK);
				mHandler.sendMessageDelayed(
						mHandler.obtainMessage(SERVER_DIED), 2000);
				return true;
			default:
				break;
			}
			return false;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onCompletion(final MediaPlayer mp) {
			if (mp == mCurrentMediaPlayer && mNextMediaPlayer != null) {
				mCurrentMediaPlayer.release();
				mCurrentMediaPlayer = mNextMediaPlayer;
				mNextMediaPlayer = null;
				mHandler.sendEmptyMessage(TRACK_WENT_TO_NEXT);
			} else {
				mService.get().mWakeLock.acquire(30000);
				mHandler.sendEmptyMessage(TRACK_ENDED);
				mHandler.sendEmptyMessage(RELEASE_WAKELOCK);
			}
		}
	}

	private static final class ServiceStub extends IService.Stub {

		private final WeakReference<MediaPlaybackService> mService;

		private ServiceStub(final MediaPlaybackService service) {
			mService = new WeakReference<MediaPlaybackService>(service);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void openFile(final String path) throws RemoteException {
			mService.get().open(path);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void open(final Song[] list, final int position)
				throws RemoteException {
			mService.get().open(list, position);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void stop() throws RemoteException {
			mService.get().stop();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void pause() throws RemoteException {
			mService.get().pause();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void play() throws RemoteException {
			mService.get().play();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void prev() throws RemoteException {
			mService.get().prev();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void next() throws RemoteException {
			mService.get().gotoNext(true);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void enqueue(final Song[] list, final int action)
				throws RemoteException {
			mService.get().enqueue(list, action);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void setQueuePosition(final int index) throws RemoteException {
			mService.get().setQueuePosition(index);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void setShuffleMode(final int shufflemode)
				throws RemoteException {
			mService.get().setShuffleMode(shufflemode);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void setRepeatMode(final int repeatmode) throws RemoteException {
			mService.get().setRepeatMode(repeatmode);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void moveQueueItem(final int from, final int to)
				throws RemoteException {
			mService.get().moveQueueItem(from, to);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void toggleFavorite() throws RemoteException {
			mService.get().toggleFavorite();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void refresh() throws RemoteException {
			mService.get().refresh();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean isFavorite() throws RemoteException {
			return mService.get().isFavorite();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean isPlaying() throws RemoteException {
			return mService.get().isPlaying();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Song[] getQueue() throws RemoteException {
			return mService.get().getQueue();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public long duration() throws RemoteException {
			return mService.get().duration();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public long position() throws RemoteException {
			return mService.get().position();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public long seek(final long position) throws RemoteException {
			return mService.get().seek(position);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getAudioId() throws RemoteException {
			return mService.get().getAudioId();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getArtistId() throws RemoteException {
			return mService.get().getArtistId();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getAlbumId() throws RemoteException {
			return mService.get().getAlbumId();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getArtistName() throws RemoteException {
			return mService.get().getArtistName();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getTrackName() throws RemoteException {
			return mService.get().getTrackName();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getAlbumName() throws RemoteException {
			return mService.get().getAlbumName();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getPath() throws RemoteException {
			return mService.get().getPath();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int getQueuePosition() throws RemoteException {
			return mService.get().getQueuePosition();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int getShuffleMode() throws RemoteException {
			return mService.get().getShuffleMode();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int getRepeatMode() throws RemoteException {
			return mService.get().getRepeatMode();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int removeTracks(final int first, final int last)
				throws RemoteException {
			return mService.get().removeTracks(first, last);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int removeTrack(final Song song) throws RemoteException {
			return mService.get().removeTrack(song);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int getMediaMountedCount() throws RemoteException {
			return mService.get().getMediaMountedCount();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int getAudioSessionId() throws RemoteException {
			return mService.get().getAudioSessionId();
		}

	}

}
