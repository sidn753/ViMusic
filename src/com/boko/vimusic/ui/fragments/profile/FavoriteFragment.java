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

package com.boko.vimusic.ui.fragments.profile;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

import com.boko.vimusic.R;
import com.boko.vimusic.adapters.ProfileSongAdapter;
import com.boko.vimusic.loaders.FavoritesLoader;
import com.boko.vimusic.menu.CreateNewPlaylist;
import com.boko.vimusic.menu.DeleteDialog;
import com.boko.vimusic.menu.FragmentMenuItems;
import com.boko.vimusic.model.Song;
import com.boko.vimusic.provider.FavoritesStore;
import com.boko.vimusic.recycler.RecycleHolder;
import com.boko.vimusic.utils.MusicUtils;
import com.boko.vimusic.utils.NavUtils;
import com.boko.vimusic.widgets.ProfileTabCarousel;
import com.boko.vimusic.widgets.VerticalScrollListener;

import java.util.List;

/**
 * This class is used to display all of the songs in {@link FavoritesStore
 * }.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class FavoriteFragment extends Fragment implements
		LoaderCallbacks<List<Song>>, OnItemClickListener {

	/**
	 * Used to keep context menu items from bleeding into other fragments
	 */
	private static final int GROUP_ID = 6;

	/**
	 * LoaderCallbacks identifier
	 */
	private static final int LOADER = 0;

	/**
	 * Fragment UI
	 */
	private ViewGroup mRootView;

	/**
	 * The adapter for the list
	 */
	private ProfileSongAdapter mAdapter;

	/**
	 * The list view
	 */
	private ListView mListView;

	/**
	 * Represents a song
	 */
	private Song mSong;

	/**
	 * Position of a context menu item
	 */
	private int mSelectedPosition;

	/**
	 * Id of a context menu item
	 */
	private Song mSelectedId;

	/**
	 * Artist name used in the context menu
	 */
	private String mArtistName;

	/**
	 * Profile header
	 */
	private ProfileTabCarousel mProfileTabCarousel;

	/**
	 * Empty constructor as per the {@link Fragment} documentation
	 */
	public FavoriteFragment() {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onAttach(final Activity activity) {
		super.onAttach(activity);
		mProfileTabCarousel = (ProfileTabCarousel) activity
				.findViewById(R.id.acivity_profile_base_tab_carousel);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Create the adpater
		mAdapter = new ProfileSongAdapter(getActivity(),
				R.layout.list_item_simple,
				ProfileSongAdapter.DISPLAY_PLAYLIST_SETTING);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public View onCreateView(final LayoutInflater inflater,
			final ViewGroup container, final Bundle savedInstanceState) {
		// The View for the fragment's UI
		mRootView = (ViewGroup) inflater.inflate(R.layout.list_base, null);
		// Initialize the list
		mListView = (ListView) mRootView.findViewById(R.id.list_base);
		// Set the data behind the list
		mListView.setAdapter(mAdapter);
		// Release any references to the recycled Views
		mListView.setRecyclerListener(new RecycleHolder());
		// Listen for ContextMenus to be created
		mListView.setOnCreateContextMenuListener(this);
		// Play the selected song
		mListView.setOnItemClickListener(this);
		// To help make scrolling smooth
		mListView.setOnScrollListener(new VerticalScrollListener(null,
				mProfileTabCarousel, 0));
		// Remove the scrollbars and padding for the fast scroll
		mListView.setVerticalScrollBarEnabled(false);
		mListView.setFastScrollEnabled(false);
		mListView.setPadding(0, 0, 0, 0);
		return mRootView;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		// Enable the options menu
		setHasOptionsMenu(true);
		// Start the loader
		getLoaderManager().initLoader(LOADER, null, this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onCreateContextMenu(final ContextMenu menu, final View v,
			final ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		// Get the position of the selected item
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		mSelectedPosition = info.position - 1;
		// Creat a new song
		mSong = mAdapter.getItem(mSelectedPosition);
		mSelectedId = mSong;
		mArtistName = mSong.mArtistName;

		// Play the song
		menu.add(GROUP_ID, FragmentMenuItems.PLAY_SELECTION, Menu.NONE,
				getString(R.string.context_menu_play_selection));

		// Play next
		menu.add(GROUP_ID, FragmentMenuItems.PLAY_NEXT, Menu.NONE,
				getString(R.string.context_menu_play_next));

		// Add the song to the queue
		menu.add(GROUP_ID, FragmentMenuItems.ADD_TO_QUEUE, Menu.NONE,
				getString(R.string.add_to_queue));

		// Add the song to a playlist
		final SubMenu subMenu = menu.addSubMenu(GROUP_ID,
				FragmentMenuItems.ADD_TO_PLAYLIST, Menu.NONE,
				R.string.add_to_playlist);
		MusicUtils.makePlaylistMenu(getActivity(), GROUP_ID, subMenu, false);

		// View more content by the song artist
		menu.add(GROUP_ID, FragmentMenuItems.MORE_BY_ARTIST, Menu.NONE,
				getString(R.string.context_menu_more_by_artist));

		// Make the song a ringtone
		menu.add(GROUP_ID, FragmentMenuItems.USE_AS_RINGTONE, Menu.NONE,
				getString(R.string.context_menu_use_as_ringtone));

		// Remove from favorites
		menu.add(GROUP_ID, FragmentMenuItems.REMOVE_FROM_FAVORITES, Menu.NONE,
				getString(R.string.remove_from_favorites));

		// Delete the song
		menu.add(GROUP_ID, FragmentMenuItems.DELETE, Menu.NONE,
				getString(R.string.context_menu_delete));
	}

	@Override
	public boolean onContextItemSelected(final android.view.MenuItem item) {
		if (item.getGroupId() == GROUP_ID) {
			switch (item.getItemId()) {
			case FragmentMenuItems.PLAY_SELECTION:
				MusicUtils.playAll(getActivity(), new Song[] { mSelectedId },
						0, false);
				return true;
			case FragmentMenuItems.PLAY_NEXT:
				MusicUtils.playNext(new Song[] { mSelectedId });
				return true;
			case FragmentMenuItems.ADD_TO_QUEUE:
				MusicUtils
						.addToQueue(getActivity(), new Song[] { mSelectedId });
				return true;
			case FragmentMenuItems.NEW_PLAYLIST:
				CreateNewPlaylist.getInstance(new Song[] { mSelectedId }).show(
						getFragmentManager(), "CreatePlaylist");
				return true;
			case FragmentMenuItems.PLAYLIST_SELECTED:
				final String mPlaylistId = item.getIntent().getStringExtra(
						"playlist");
				MusicUtils.addToPlaylist(getActivity(),
						new Song[] { mSelectedId }, mPlaylistId);
				return true;
			case FragmentMenuItems.MORE_BY_ARTIST:
				NavUtils.openArtistProfile(getActivity(), mArtistName);
				return true;
			case FragmentMenuItems.USE_AS_RINGTONE:
				MusicUtils.setRingtone(getActivity(), mSelectedId.getId());
				return true;
			case FragmentMenuItems.REMOVE_FROM_FAVORITES:
				FavoritesStore.getInstance(getActivity()).removeSong(
						mSelectedId.getId(), mSelectedId.getHost());
				getLoaderManager().restartLoader(LOADER, null, this);
				return true;
			case FragmentMenuItems.DELETE:
				DeleteDialog.newInstance(mSong.getName(),
						new Song[] { mSelectedId }, null).show(
						getFragmentManager(), "DeleteDialog");
				SystemClock.sleep(10);
				mAdapter.notifyDataSetChanged();
				getLoaderManager().restartLoader(LOADER, null, this);
				return true;
			default:
				break;
			}
		}
		return super.onContextItemSelected(item);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onItemClick(final AdapterView<?> parent, final View view,
			final int position, final long id) {
		if (position == 0) {
			return;
		}
		Cursor cursor = FavoritesLoader.makeFavoritesCursor(getActivity());
		final Song[] list = MusicUtils.getSongListForCursor(cursor);
		MusicUtils.playAll(getActivity(), list, position - 1, false);
		cursor.close();
		cursor = null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Loader<List<Song>> onCreateLoader(final int id, final Bundle args) {
		return new FavoritesLoader(getActivity());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onLoadFinished(final Loader<List<Song>> loader,
			final List<Song> data) {
		// Check for any errors
		if (data.isEmpty()) {
			// Set the empty text
			final TextView empty = (TextView) mRootView
					.findViewById(R.id.empty);
			empty.setText(getString(R.string.empty_favorite));
			mListView.setEmptyView(empty);
			return;
		}

		// Start fresh
		mAdapter.unload();
		// Return the correct count
		mAdapter.setCount(data);
		// Add the data to the adpater
		for (final Song song : data) {
			mAdapter.add(song);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onLoaderReset(final Loader<List<Song>> loader) {
		// Clear the data in the adapter
		mAdapter.unload();
	}

}
