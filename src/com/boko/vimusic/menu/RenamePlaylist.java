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

package com.boko.vimusic.menu;

import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio;

import com.boko.vimusic.R;
import com.boko.vimusic.format.Capitalize;
import com.boko.vimusic.utils.MusicUtils;

/**
 * Alert dialog used to rename playlits.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class RenamePlaylist extends BasePlaylistDialog {

    private String mOriginalName;

    private String mRenameId;

    /**
     * @param id The Id of the playlist to rename
     * @return A new instance of this dialog.
     */
    public static RenamePlaylist getInstance(final String id) {
        final RenamePlaylist frag = new RenamePlaylist();
        final Bundle args = new Bundle();
        args.putString("rename", id);
        frag.setArguments(args);
        return frag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSaveInstanceState(final Bundle outcicle) {
        outcicle.putString("defaultname", mPlaylist.getText().toString());
        outcicle.putString("rename", mRenameId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initObjects(final Bundle savedInstanceState) {
        mRenameId = savedInstanceState != null ? savedInstanceState.getString("rename")
                : getArguments().getString("rename");
        mOriginalName = getPlaylistNameFromId(mRenameId);
        mDefaultname = savedInstanceState != null ? savedInstanceState.getString("defaultname")
                : mOriginalName;
        if (mRenameId != null || mOriginalName == null || mDefaultname == null) {
            getDialog().dismiss();
            return;
        }
        final String promptformat = getString(R.string.create_playlist_prompt);
        mPrompt = String.format(promptformat, mOriginalName, mDefaultname);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSaveClick() {
        final String playlistName = mPlaylist.getText().toString();
        if (playlistName != null && playlistName.length() > 0) {
            final ContentResolver resolver = getActivity().getContentResolver();
            final ContentValues values = new ContentValues(1);
            values.put(Audio.Playlists.NAME, Capitalize.capitalize(playlistName));
            resolver.update(Audio.Playlists.EXTERNAL_CONTENT_URI, values,
                    MediaStore.Audio.Playlists._ID + "=?", new String[] {
                        mRenameId
                    });
            closeKeyboard();
            getDialog().dismiss();
        }
    }

    @Override
    public void onTextChangedListener() {
        final String playlistName = mPlaylist.getText().toString();
        mSaveButton = mPlaylistDialog.getButton(Dialog.BUTTON_POSITIVE);
        if (mSaveButton == null) {
            return;
        }
        if (playlistName.trim().length() == 0) {
            mSaveButton.setEnabled(false);
        } else {
            mSaveButton.setEnabled(true);
            if (MusicUtils.getIdForPlaylist(getActivity(), playlistName) != null) {
                mSaveButton.setText(R.string.overwrite);
            } else {
                mSaveButton.setText(R.string.save);
            }
        }
    }

    /**
     * @param id The Id of the playlist
     * @return The name of the playlist
     */
    private String getPlaylistNameFromId(final String id) {
        Cursor cursor = getActivity().getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, new String[] {
                    MediaStore.Audio.Playlists.NAME
                }, MediaStore.Audio.Playlists._ID + "=?", new String[] {
                    id
                }, MediaStore.Audio.Playlists.NAME);
        String playlistName = null;
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                playlistName = cursor.getString(0);
            }
        }
        cursor.close();
        cursor = null;
        return playlistName;
    }

}
