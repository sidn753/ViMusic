/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.boko.vimusic.ui.activities;

import static com.boko.vimusic.utils.MusicUtils.mService;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

import com.boko.vimusic.MusicStateListener;
import com.boko.vimusic.R;
import com.boko.vimusic.service.IMediaPlaybackService;
import com.boko.vimusic.service.MediaPlaybackService;
import com.boko.vimusic.utils.CommonUtils;
import com.boko.vimusic.utils.Lists;
import com.boko.vimusic.utils.MusicUtils;
import com.boko.vimusic.utils.MusicUtils.ServiceToken;
import com.boko.vimusic.utils.NavUtils;
import com.boko.vimusic.utils.ThemeUtils;
import com.boko.vimusic.widgets.PlayPauseButton;
import com.boko.vimusic.widgets.RepeatButton;
import com.boko.vimusic.widgets.ShuffleButton;

/**
 * A base {@link FragmentActivity} used to update the bottom bar and bind to
 * Apollo's service.
 * <p>
 * {@link HomeActivity} extends from this skeleton.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public abstract class BaseActivity extends FragmentActivity implements
		ServiceConnection {
	
	public static final String REFRESH_REQUESTED = "com.boko.vimusic.event.REFRESH_REQUESTED";

	/**
	 * Playstate and meta change listener
	 */
	private final ArrayList<MusicStateListener> mMusicStateListener = Lists
			.newArrayList();

	/**
	 * The service token
	 */
	private ServiceToken mToken;

	/**
	 * Play and pause button (BAB)
	 */
	private PlayPauseButton mPlayPauseButton;

	/**
	 * Repeat button (BAB)
	 */
	private RepeatButton mRepeatButton;

	/**
	 * Shuffle button (BAB)
	 */
	private ShuffleButton mShuffleButton;

	/**
	 * Track name (BAB)
	 */
	private TextView mTrackName;

	/**
	 * Artist name (BAB)
	 */
	private TextView mArtistName;

	/**
	 * Album art (BAB)
	 */
	private ImageView mAlbumArt;

	/**
	 * Broadcast receiver
	 */
	private PlaybackStatus mPlaybackStatus;

	/**
	 * Keeps track of the back button being used
	 */
	private boolean mIsBackPressed = false;

	/**
	 * Theme resources
	 */
	protected ThemeUtils mResources;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Initialze the theme resources
		mResources = new ThemeUtils(this);

		// Set the overflow style
		mResources.setOverflowStyle(this);

		// Fade it in
		overridePendingTransition(android.R.anim.fade_in,
				android.R.anim.fade_out);

		// Control the media volume
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		// Bind Apollo's service
		mToken = MusicUtils.bindToService(this, this);

		// Initialize the broadcast receiver
		mPlaybackStatus = new PlaybackStatus(this);

		// Theme the action bar
		mResources.themeActionBar(getActionBar(), getString(R.string.app_name));

		// Set the layout
		setContentView(setContentView());

		// Initialze the bottom action bar
		initBottomActionBar();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onServiceConnected(final ComponentName name,
			final IBinder service) {
		mService = IMediaPlaybackService.Stub.asInterface(service);
		// Set the playback drawables
		updatePlaybackControls();
		// Current info
		updateBottomActionBarInfo();
		// Update the favorites icon
		invalidateOptionsMenu();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onServiceDisconnected(final ComponentName name) {
		mService = null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		// Search view
		getMenuInflater().inflate(R.menu.search, menu);
		// Settings
		getMenuInflater().inflate(R.menu.activity_base, menu);
		// Theme the search icon
		mResources.setSearchIcon(menu);

		final SearchView searchView = (SearchView) menu.findItem(
				R.id.menu_search).getActionView();
		// Add voice search
		final SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
		final SearchableInfo searchableInfo = searchManager
				.getSearchableInfo(getComponentName());
		searchView.setSearchableInfo(searchableInfo);
		// Perform the search
		searchView.setOnQueryTextListener(new OnQueryTextListener() {

			@Override
			public boolean onQueryTextSubmit(final String query) {
				// Open the search activity
				NavUtils.openSearch(BaseActivity.this, query);
				return true;
			}

			@Override
			public boolean onQueryTextChange(final String newText) {
				// Nothing to do
				return false;
			}
		});
		return super.onCreateOptionsMenu(menu);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_settings:
			// Settings
			NavUtils.openSettings(this);
			return true;

		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onResume() {
		super.onResume();
		// Set the playback drawables
		updatePlaybackControls();
		// Current info
		updateBottomActionBarInfo();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onStart() {
		super.onStart();
		final IntentFilter filter = new IntentFilter();
		// Play and pause changes
		filter.addAction(MediaPlaybackService.EVENT_PLAYSTATE_CHANGED);
		// Shuffle and repeat changes
		filter.addAction(MediaPlaybackService.EVENT_SHUFFLEMODE_CHANGED);
		filter.addAction(MediaPlaybackService.EVENT_REPEATMODE_CHANGED);
		// Track changes
		filter.addAction(MediaPlaybackService.EVENT_META_CHANGED);
		// Update a list, probably the playlist fragment's
		filter.addAction(REFRESH_REQUESTED);
		registerReceiver(mPlaybackStatus, filter);
		MusicUtils.notifyForegroundStateChanged(this, true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onStop() {
		super.onStop();
		MusicUtils.notifyForegroundStateChanged(this, false);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
		// Unbind from the service
		if (mToken != null) {
			MusicUtils.unbindFromService(mToken);
			mToken = null;
		}

		// Unregister the receiver
		try {
			unregisterReceiver(mPlaybackStatus);
		} catch (final Throwable e) {
			//$FALL-THROUGH$
		}

		// Remove any music status listeners
		mMusicStateListener.clear();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onBackPressed() {
		super.onBackPressed();
		mIsBackPressed = true;
	}

	/**
	 * Initializes the items in the bottom action bar.
	 */
	private void initBottomActionBar() {
		// Play and pause button
		mPlayPauseButton = (PlayPauseButton) findViewById(R.id.action_button_play);
		// Shuffle button
		mShuffleButton = (ShuffleButton) findViewById(R.id.action_button_shuffle);
		// Repeat button
		mRepeatButton = (RepeatButton) findViewById(R.id.action_button_repeat);
		// Track name
		mTrackName = (TextView) findViewById(R.id.bottom_action_bar_line_one);
		// Artist name
		mArtistName = (TextView) findViewById(R.id.bottom_action_bar_line_two);
		// Album art
		mAlbumArt = (ImageView) findViewById(R.id.bottom_action_bar_album_art);
		// Open to the currently playing album profile
		mAlbumArt.setOnClickListener(mOpenCurrentAlbumProfile);
		// Bottom action bar
		final LinearLayout bottomActionBar = (LinearLayout) findViewById(R.id.bottom_action_bar);
		// Display the now playing screen or shuffle if this isn't anything
		// playing
		bottomActionBar.setOnClickListener(mOpenNowPlaying);
	}

	/**
	 * Sets the track name, album name, and album art.
	 */
	private void updateBottomActionBarInfo() {
		// Set the track name
		mTrackName.setText(MusicUtils.getTrackName());
		// Set the artist name
		mArtistName.setText(MusicUtils.getArtistName());
		// Set the album art
		CommonUtils.getImageFetcher(this).loadCurrentArtwork(mAlbumArt);
	}

	/**
	 * Sets the correct drawable states for the playback controls.
	 */
	private void updatePlaybackControls() {
		// Set the play and pause image
		mPlayPauseButton.updateState();
		// Set the shuffle image
		mShuffleButton.updateShuffleState();
		// Set the repeat image
		mRepeatButton.updateRepeatState();
	}

	/**
	 * Opens the album profile of the currently playing album
	 */
	private final View.OnClickListener mOpenCurrentAlbumProfile = new View.OnClickListener() {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onClick(final View v) {
			if (MusicUtils.getCurrentAudioId() != null) {
				NavUtils.openAlbumProfile(BaseActivity.this,
						MusicUtils.getAlbumName(), MusicUtils.getArtistName(),
						MusicUtils.getCurrentAlbumId());
			} else {
				MusicUtils.shuffleAll(BaseActivity.this);
			}
			if (BaseActivity.this instanceof ProfileActivity) {
				finish();
			}
		}
	};

	/**
	 * Opens the now playing screen
	 */
	private final View.OnClickListener mOpenNowPlaying = new View.OnClickListener() {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onClick(final View v) {
			if (MusicUtils.getCurrentAudioId() != null) {
				NavUtils.openAudioPlayer(BaseActivity.this);
			} else {
				MusicUtils.shuffleAll(BaseActivity.this);
			}
		}
	};

	/**
	 * Used to monitor the state of playback
	 */
	private final static class PlaybackStatus extends BroadcastReceiver {

		private final WeakReference<BaseActivity> mReference;

		/**
		 * Constructor of <code>PlaybackStatus</code>
		 */
		public PlaybackStatus(final BaseActivity activity) {
			mReference = new WeakReference<BaseActivity>(activity);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (action.equals(MediaPlaybackService.EVENT_META_CHANGED)) {
				// Current info
				mReference.get().updateBottomActionBarInfo();
				// Update the favorites icon
				mReference.get().invalidateOptionsMenu();
				// Let the listener know to the meta chnaged
				for (final MusicStateListener listener : mReference.get().mMusicStateListener) {
					if (listener != null) {
						listener.onMetaChanged();
					}
				}
			} else if (action.equals(MediaPlaybackService.EVENT_PLAYSTATE_CHANGED)) {
				// Set the play and pause image
				mReference.get().mPlayPauseButton.updateState();
			} else if (action.equals(MediaPlaybackService.EVENT_REPEATMODE_CHANGED)
					|| action.equals(MediaPlaybackService.EVENT_SHUFFLEMODE_CHANGED)) {
				// Set the repeat image
				mReference.get().mRepeatButton.updateRepeatState();
				// Set the shuffle image
				mReference.get().mShuffleButton.updateShuffleState();
			} else if (action.equals(REFRESH_REQUESTED)) {
				// Let the listener know to update a list
				for (final MusicStateListener listener : mReference.get().mMusicStateListener) {
					if (listener != null) {
						listener.restartLoader();
					}
				}
			}
		}
	}

	/**
	 * @param status
	 *            The {@link MusicStateListener} to use
	 */
	public void setMusicStateListenerListener(final MusicStateListener status) {
		if (status != null) {
			mMusicStateListener.add(status);
		}
	}

	/**
	 * @return The resource ID to be inflated.
	 */
	public abstract int setContentView();
}
