package com.boko.vimusic.provider;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

/**
 * Keep a cache of the album ID, name, and time it was played to be retrieved later.
 * 
 */
public class RecentStore extends SQLiteOpenHelper {

    /* Version constant to increment when the database should be rebuilt */
    private static final int VERSION = 1;

    /* Name of database file */
    public static final String DATABASENAME = "recent.db";

    private static RecentStore sInstance = null;

    /**
     * Constructor of <code>RecentStore</code>
     * 
     * @param context The {@link Context} to use
     */
    public RecentStore(final Context context) {
        super(context, DATABASENAME, null, VERSION);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + RecentTable.TABLE_NAME + " ("
                + RecentTable.AID + " TEXT NOT NULL,"				// Album ID
                + RecentTable.HOST + " TEXT NOT NULL,"				// Album Host
        		+ RecentTable.NAME + " TEXT NOT NULL,"				// Album Name
        		+ RecentTable.ARTIST + " TEXT NOT NULL,"			// Album Artist
                + RecentTable.SONG_COUNT + " LONG NOT NULL,"		// Album Song count
                + RecentTable.YEAR + " TEXT,"						// Album Year
                + RecentTable.TIME_PLAYED + " LONG NOT NULL);");	// Album Time played
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + RecentTable.TABLE_NAME);
        onCreate(db);
    }

    /**
     * @param context The {@link Context} to use
     * @return A new instance of this class.
     */
    public static final synchronized RecentStore getInstance(final Context context) {
        if (sInstance == null) {
            sInstance = new RecentStore(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Used to store album in the database.
     * 
     * @param albumId The album's ID.
     * @param albumHost The album host
     * @param albumName The album name.
     * @param artistName The artist album name.
     * @param songCount The number of tracks for the album.
     * @param albumYear The year the album was released.
     */
    public void addAlbum(final String albumId, final String albumHost, final String albumName, final String artistName,
            final Long songCount, final String albumYear) {
        if (albumId == null || albumHost == null || albumName == null || artistName == null || songCount == null) {
            return;
        }

        final SQLiteDatabase database = getWritableDatabase();
        final ContentValues values = new ContentValues(7);

        database.beginTransaction();

        values.put(RecentTable.AID, albumId);
        values.put(RecentTable.HOST, albumHost);
        values.put(RecentTable.NAME, albumName);
        values.put(RecentTable.ARTIST, artistName);
        values.put(RecentTable.SONG_COUNT, songCount);
        values.put(RecentTable.YEAR, albumYear);
        values.put(RecentTable.TIME_PLAYED, System.currentTimeMillis());

        database.delete(RecentTable.TABLE_NAME, RecentTable.AID + " = ? AND " + RecentTable.HOST + " = ?", new String[] {
        		albumId,
        		albumHost
        });
        database.insert(RecentTable.TABLE_NAME, null, values);
        database.setTransactionSuccessful();
        database.endTransaction();
    }
    
    /**
     * @param item The album Id to remove.
     */
    public void removeAlbum(final String albumId, final String albumHost) {
        final SQLiteDatabase database = getReadableDatabase();
        database.delete(RecentTable.TABLE_NAME, RecentTable.AID + " = ? AND " + RecentTable.HOST + " = ?", new String[] {
        		albumId,
        		albumHost
        });
    }

    /**
     * Used to retrieve the most recently listened album for an artist.
     * 
     * @param artistName Artist name
     * @return The most recently listened album for an artist.
     */
    public String getMostPlayedAlbumName(final String artistName) {
        if (TextUtils.isEmpty(artistName)) {
            return null;
        }
        final SQLiteDatabase database = getReadableDatabase();
        final String[] projection = new String[] {
                RecentTable.NAME
        };
        final String selection = RecentTable.ARTIST + "=?";
        final String[] having = new String[] {
        		artistName
        };
        Cursor cursor = database.query(RecentTable.TABLE_NAME, projection, selection, having,
                null, null, RecentTable.TIME_PLAYED + " DESC", null);
        if (cursor != null && cursor.moveToFirst()) {
            final String album = cursor.getString(0);
            cursor.close();
            cursor = null;
            return album;
        }
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
            cursor = null;
        }

        return null;
    }

    /**
     * Clear the cache.
     */
    public void deleteDatabase() {
        final SQLiteDatabase database = getReadableDatabase();
        database.delete(RecentTable.TABLE_NAME, null, null);
    }

    public interface RecentTable {

        /* Table name */
        public static final String TABLE_NAME = "recent";

        /* Album IDs column */
        public static final String AID = "aId";
        
        /* Album Host column */
        public static final String HOST = "host";

        /* Album name column */
        public static final String NAME = "name";

        /* Artist name column */
        public static final String ARTIST = "artist";

        /* Album song count column */
        public static final String SONG_COUNT = "songcount";

        /* Album year column. It's okay for this to be null */
        public static final String YEAR = "year";

        /* Time played column */
        public static final String TIME_PLAYED = "timeplayed";
    }
}
