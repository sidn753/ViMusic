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

package com.boko.vimusic.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.boko.vimusic.R;
import com.boko.vimusic.model.Song;
import com.boko.vimusic.ui.MusicHolder;
import com.boko.vimusic.ui.fragments.profile.AlbumSongFragment;
import com.boko.vimusic.ui.fragments.profile.ArtistSongFragment;
import com.boko.vimusic.ui.fragments.profile.FavoriteFragment;
import com.boko.vimusic.ui.fragments.profile.GenreSongFragment;
import com.boko.vimusic.ui.fragments.profile.LastAddedFragment;
import com.boko.vimusic.ui.fragments.profile.PlaylistSongFragment;
import com.boko.vimusic.utils.Lists;
import com.boko.vimusic.utils.MusicUtils;

import java.util.List;

/**
 * This {@link ArrayAdapter} is used to display the songs for a particular
 * artist, album, playlist, or genre for {@link ArtistSongFragment},
 * {@link AlbumSongFragment},{@link PlaylistSongFragment},
 * {@link GenreSongFragment},{@link FavoriteFragment},{@link LastAddedFragment}.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ProfileSongAdapter extends ArrayAdapter<Song> {

    /**
     * Default display setting: title/album
     */
    public static final int DISPLAY_DEFAULT_SETTING = 0;

    /**
     * Playlist display setting: title/artist-album
     */
    public static final int DISPLAY_PLAYLIST_SETTING = 1;

    /**
     * Album display setting: title/duration
     */
    public static final int DISPLAY_ALBUM_SETTING = 2;

    /**
     * The header view
     */
    private static final int ITEM_VIEW_TYPE_HEADER = 0;

    /**
     * * The data in the list.
     */
    private static final int ITEM_VIEW_TYPE_MUSIC = 1;

    /**
     * Number of views (ImageView, TextView, header)
     */
    private static final int VIEW_TYPE_COUNT = 3;

    /**
     * LayoutInflater
     */
    private final LayoutInflater mInflater;

    /**
     * Fake header
     */
    private final View mHeader;

    /**
     * The resource Id of the layout to inflate
     */
    private final int mLayoutId;

    /**
     * Display setting for the second line in a song fragment
     */
    private final int mDisplaySetting;

    /**
     * Separator used for separating album/artist strings
     */
    private final String SEPARATOR_STRING = " - ";

    /**
     * Used to set the size of the data in the adapter
     */
    private List<Song> mCount = Lists.newArrayList();

    /**
     * Constructor of <code>ProfileSongAdapter</code>
     * 
     * @param context The {@link Context} to use
     * @param layoutId The resource Id of the view to inflate.
     * @param setting defines the content of the second line
     */
    public ProfileSongAdapter(final Context context, final int layoutId, final int setting) {
        super(context, 0);
        // Used to create the custom layout
        mInflater = LayoutInflater.from(context);
        // Cache the header
        mHeader = mInflater.inflate(R.layout.faux_carousel, null);
        // Get the layout Id
        mLayoutId = layoutId;
        // Know what to put in line two
        mDisplaySetting = setting;
    }

    /**
     * Constructor of <code>ProfileSongAdapter</code>
     * 
     * @param context The {@link Context} to use
     * @param layoutId The resource Id of the view to inflate.
     */
    public ProfileSongAdapter(final Context context, final int layoutId) {
        this(context, layoutId, DISPLAY_DEFAULT_SETTING);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {

        // Return a faux header at position 0
        if (position == 0) {
            return mHeader;
        }

        // Recycle MusicHolder's items
        MusicHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(mLayoutId, parent, false);
            holder = new MusicHolder(convertView);
            // Hide the third line of text
            holder.mLineThree.get().setVisibility(View.GONE);
            convertView.setTag(holder);
        } else {
            holder = (MusicHolder)convertView.getTag();
        }

        // Retrieve the album
        final Song song = getItem(position - 1);

        // Set each track name (line one)
        holder.mLineOne.get().setText(song.getName());
        // Set the line two
        switch (mDisplaySetting) {
            // show duration if on album fragment
            case DISPLAY_ALBUM_SETTING:
                holder.mLineTwo.get().setText(
                        MusicUtils.makeTimeString(getContext(), song.mDuration));
                break;
            case DISPLAY_PLAYLIST_SETTING:
                final StringBuilder sb = new StringBuilder(song.mArtistName);
                sb.append(SEPARATOR_STRING);
                sb.append(song.mAlbumName);
                holder.mLineTwo.get().setText(sb.toString());
                break;
            case DISPLAY_DEFAULT_SETTING:
            default:
                holder.mLineTwo.get().setText(song.mAlbumName);
                break;
        }
        return convertView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasStableIds() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        final int size = mCount.size();
        return size == 0 ? 0 : size + 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getItemId(final int position) {
        if (position == 0) {
            return -1;
        }
        return position - 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getItemViewType(final int position) {
        if (position == 0) {
            return ITEM_VIEW_TYPE_HEADER;
        }
        return ITEM_VIEW_TYPE_MUSIC;
    }

    /**
     * Method that unloads and clears the items in the adapter
     */
    public void unload() {
        clear();
    }

    /**
     * @param data The {@link List} used to return the count for the adapter.
     */
    public void setCount(final List<Song> data) {
        mCount = data;
    }

}
