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

package com.boko.vimusic.ui.fragments.zing;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.boko.vimusic.R;
import com.boko.vimusic.adapters.PagerAdapter;
import com.boko.vimusic.ui.fragments.AlbumFragment;
import com.boko.vimusic.ui.fragments.ArtistFragment;
import com.boko.vimusic.ui.fragments.SongFragment;
import com.boko.vimusic.utils.MusicUtils;
import com.boko.vimusic.utils.NavUtils;
import com.boko.vimusic.utils.PreferenceUtils;
import com.boko.vimusic.utils.SortOrder;
import com.boko.vimusic.utils.ThemeUtils;
import com.viewpagerindicator.TitlePageIndicator;
import com.viewpagerindicator.TitlePageIndicator.OnCenterItemClickListener;

/**
 * This class is used to hold the {@link ViewPager} used for swiping between the
 * playlists, recent, artists, albums, songs, and genre {@link Fragment}
 * s for phones.
 * 
 * @NOTE: The reason the sort orders are taken care of in this fragment rather
 *        than the individual fragments is to keep from showing all of the menu
 *        items on tablet interfaces. That being said, I have a tablet interface
 *        worked out, but I'm going to keep it in the Play Store version of
 *        Apollo for a couple of weeks or so before merging it with CM.
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class MusicBrowserZingFragment extends Fragment implements
        OnCenterItemClickListener {

    /**
     * Pager
     */
    private ViewPager mViewPager;

    /**
     * VP's adapter
     */
    private PagerAdapter mPagerAdapter;

    /**
     * Theme resources
     */
    private ThemeUtils mResources;

    private PreferenceUtils mPreferences;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public MusicBrowserZingFragment() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get the preferences
        mPreferences = PreferenceUtils.getInstance(getActivity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        // The View for the fragment's UI
        final ViewGroup rootView = (ViewGroup)inflater.inflate(
                R.layout.fragment_music_browser_phone, container, false);

        // Initialize the adapter
        mPagerAdapter = new PagerAdapter(getActivity());
        final MusicFragments[] mFragments = MusicFragments.values();
        for (final MusicFragments mFragment : mFragments) {
            mPagerAdapter.add(mFragment.getFragmentClass(), null);
        }

        // Initialize the ViewPager
        mViewPager = (ViewPager)rootView.findViewById(R.id.fragment_home_phone_pager);
        // Attch the adapter
        mViewPager.setAdapter(mPagerAdapter);
        // Offscreen pager loading limit
        mViewPager.setOffscreenPageLimit(mPagerAdapter.getCount() - 1);
        // Start on the last page the user was on
        mViewPager.setCurrentItem(mPreferences.getStartPage());

        // Initialze the TPI
        final TitlePageIndicator pageIndicator = (TitlePageIndicator)rootView
                .findViewById(R.id.fragment_home_phone_pager_titles);
        // Attach the ViewPager
        pageIndicator.setViewPager(mViewPager);
        // Scroll to the current artist, album, or song
        pageIndicator.setOnCenterItemClickListener(this);
        return rootView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Initialze the theme resources
        mResources = new ThemeUtils(getActivity());
        // Enable the options menu
        setHasOptionsMenu(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();
        // Save the last page the use was on
        mPreferences.setStartPage(mViewPager.getCurrentItem());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        mResources.setFavoriteIcon(menu);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        // Favorite action
        inflater.inflate(R.menu.favorite, menu);
        // Shuffle all
        inflater.inflate(R.menu.shuffle, menu);
        // Sort orders
        if (isRecentPage()) {
            inflater.inflate(R.menu.view_as, menu);
        } else if (isArtistPage()) {
            inflater.inflate(R.menu.artist_sort_by, menu);
            inflater.inflate(R.menu.view_as, menu);
        } else if (isAlbumPage()) {
            inflater.inflate(R.menu.album_sort_by, menu);
            inflater.inflate(R.menu.view_as, menu);
        } else if (isSongPage()) {
            inflater.inflate(R.menu.song_sort_by, menu);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_shuffle:
                // Shuffle all the songs
                MusicUtils.shuffleAll(getActivity());
                return true;
            case R.id.menu_favorite:
                // Toggle the current track as a favorite and update the menu
                // item
                MusicUtils.toggleFavorite();
                getActivity().invalidateOptionsMenu();
                return true;
            case R.id.menu_sort_by_az:
                if (isArtistPage()) {
                    mPreferences.setArtistSortOrder(SortOrder.ArtistSortOrder.ARTIST_A_Z);
                    getArtistFragment().refresh();
                } else if (isAlbumPage()) {
                    mPreferences.setAlbumSortOrder(SortOrder.AlbumSortOrder.ALBUM_A_Z);
                    getAlbumFragment().refresh();
                } else if (isSongPage()) {
                    mPreferences.setSongSortOrder(SortOrder.SongSortOrder.SONG_A_Z);
                    getSongFragment().refresh();
                }
                return true;
            case R.id.menu_sort_by_za:
                if (isArtistPage()) {
                    mPreferences.setArtistSortOrder(SortOrder.ArtistSortOrder.ARTIST_Z_A);
                    getArtistFragment().refresh();
                } else if (isAlbumPage()) {
                    mPreferences.setAlbumSortOrder(SortOrder.AlbumSortOrder.ALBUM_Z_A);
                    getAlbumFragment().refresh();
                } else if (isSongPage()) {
                    mPreferences.setSongSortOrder(SortOrder.SongSortOrder.SONG_Z_A);
                    getSongFragment().refresh();
                }
                return true;
            case R.id.menu_sort_by_artist:
                if (isAlbumPage()) {
                    mPreferences.setAlbumSortOrder(SortOrder.AlbumSortOrder.ALBUM_ARTIST);
                    getAlbumFragment().refresh();
                } else if (isSongPage()) {
                    mPreferences.setSongSortOrder(SortOrder.SongSortOrder.SONG_ARTIST);
                    getSongFragment().refresh();
                }
                return true;
            case R.id.menu_sort_by_album:
                if (isSongPage()) {
                    mPreferences.setSongSortOrder(SortOrder.SongSortOrder.SONG_ALBUM);
                    getSongFragment().refresh();
                }
                return true;
            case R.id.menu_sort_by_year:
                if (isAlbumPage()) {
                    mPreferences.setAlbumSortOrder(SortOrder.AlbumSortOrder.ALBUM_YEAR);
                    getAlbumFragment().refresh();
                } else if (isSongPage()) {
                    mPreferences.setSongSortOrder(SortOrder.SongSortOrder.SONG_YEAR);
                    getSongFragment().refresh();
                }
                return true;
            case R.id.menu_sort_by_duration:
                if (isSongPage()) {
                    mPreferences.setSongSortOrder(SortOrder.SongSortOrder.SONG_DURATION);
                    getSongFragment().refresh();
                }
                return true;
            case R.id.menu_sort_by_number_of_songs:
                if (isArtistPage()) {
                    mPreferences
                            .setArtistSortOrder(SortOrder.ArtistSortOrder.ARTIST_NUMBER_OF_SONGS);
                    getArtistFragment().refresh();
                } else if (isAlbumPage()) {
                    mPreferences.setAlbumSortOrder(SortOrder.AlbumSortOrder.ALBUM_NUMBER_OF_SONGS);
                    getAlbumFragment().refresh();
                }
                return true;
            case R.id.menu_sort_by_number_of_albums:
                if (isArtistPage()) {
                    mPreferences
                            .setArtistSortOrder(SortOrder.ArtistSortOrder.ARTIST_NUMBER_OF_ALBUMS);
                    getArtistFragment().refresh();
                }
                return true;
            case R.id.menu_sort_by_filename:
                if(isSongPage()) {
                    mPreferences.setSongSortOrder(SortOrder.SongSortOrder.SONG_FILENAME);
                    getSongFragment().refresh();
                }
                return true;
            case R.id.menu_view_as_simple:
                if (isRecentPage()) {
                    mPreferences.setRecentLayout("simple");
                } else if (isArtistPage()) {
                    mPreferences.setArtistLayout("simple");
                } else if (isAlbumPage()) {
                    mPreferences.setAlbumLayout("simple");
                }
                NavUtils.goHome(getActivity());
                return true;
            case R.id.menu_view_as_detailed:
                if (isRecentPage()) {
                    mPreferences.setRecentLayout("detailed");
                } else if (isArtistPage()) {
                    mPreferences.setArtistLayout("detailed");
                } else if (isAlbumPage()) {
                    mPreferences.setAlbumLayout("detailed");
                }
                NavUtils.goHome(getActivity());
                return true;
            case R.id.menu_view_as_grid:
                if (isRecentPage()) {
                    mPreferences.setRecentLayout("grid");
                } else if (isArtistPage()) {
                    mPreferences.setArtistLayout("grid");
                } else if (isAlbumPage()) {
                    mPreferences.setAlbumLayout("grid");
                }
                NavUtils.goHome(getActivity());
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
    public void onCenterItemClick(final int position) {
        // If on the artist fragment, scrolls to the current artist
        if (position == 2) {
            getArtistFragment().scrollToCurrentArtist();
            // If on the album fragment, scrolls to the current album
        } else if (position == 3) {
            getAlbumFragment().scrollToCurrentAlbum();
            // If on the song fragment, scrolls to the current song
        } else if (position == 4) {
            getSongFragment().scrollToCurrentSong();
        }
    }

    private boolean isArtistPage() {
        return mViewPager.getCurrentItem() == 2;
    }

    private ArtistFragment getArtistFragment() {
        return (ArtistFragment)mPagerAdapter.getFragment(2);
    }

    private boolean isAlbumPage() {
        return mViewPager.getCurrentItem() == 3;
    }

    private AlbumFragment getAlbumFragment() {
        return (AlbumFragment)mPagerAdapter.getFragment(3);
    }

    private boolean isSongPage() {
        return mViewPager.getCurrentItem() == 4;
    }

    private SongFragment getSongFragment() {
        return (SongFragment)mPagerAdapter.getFragment(4);
    }

    private boolean isRecentPage() {
        return mViewPager.getCurrentItem() == 1;
    }
    
    /**
     * An enumeration of all the main fragments supported.
     */
    private enum MusicFragments {
//        /**
//         * The playlist fragment
//         */
//        PLAYLIST(PlaylistFragment.class),
//        /**
//         * The recent fragment
//         */
//        RECENT(RecentFragment.class),
//        /**
//         * The artist fragment
//         */
//        ARTIST(ArtistFragment.class),
//        /**
//         * The album fragment
//         */
//        ALBUM(AlbumFragment.class),
        /**
         * The song fragment
         */
        SONG(ZingSongFragment.class);
//        /**
//         * The genre fragment
//         */
//        GENRE(GenreFragment.class);

        private Class<? extends Fragment> mFragmentClass;

        /**
         * Constructor of <code>MusicFragments</code>
         * 
         * @param fragmentClass The fragment class
         */
        private MusicFragments(final Class<? extends Fragment> fragmentClass) {
            mFragmentClass = fragmentClass;
        }

        /**
         * Method that returns the fragment class.
         * 
         * @return Class<? extends Fragment> The fragment class.
         */
        public Class<? extends Fragment> getFragmentClass() {
            return mFragmentClass;
        }

    }
}
