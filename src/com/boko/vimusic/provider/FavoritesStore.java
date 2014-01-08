package com.boko.vimusic.provider;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * This class is used to to create the database used to make the Favorites
 * playlist.
 * 
 */
public class FavoritesStore extends SQLiteOpenHelper {

    /* Version constant to increment when the database should be rebuilt */
    private static final int VERSION = 1;

    /* Name of database file */
    public static final String DATABASENAME = "favorites.db";

    private static FavoritesStore sInstance = null;

    /**
     * Constructor of <code>FavoritesStore</code>
     * 
     * @param context The {@link Context} to use
     */
    public FavoritesStore(final Context context) {
        super(context, DATABASENAME, null, VERSION);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + FavoritesTable.TABLE_NAME + " ("
        		+ FavoritesTable.SID + " TEXT NOT NULL,"				// Song ID
        		+ FavoritesTable.HOST + " TEXT NOT NULL,"				// Song Host
        		+ FavoritesTable.NAME + " TEXT NOT NULL,"				// Song Name
                + FavoritesTable.ALBUM + " TEXT NOT NULL,"				// Song Album
        		+ FavoritesTable.ARTIST + " TEXT NOT NULL,"				// Song Artist
                + FavoritesTable.PLAY_COUNT + " LONG NOT NULL);");		// Song Play count
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + FavoritesTable.TABLE_NAME);
        onCreate(db);
    }

    /**
     * @param context The {@link Context} to use
     * @return A new instance of this class
     */
    public static final synchronized FavoritesStore getInstance(final Context context) {
        if (sInstance == null) {
            sInstance = new FavoritesStore(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Used to store song in our database
     * 
     * @param songId The song ID
     * @param songHost The song host
     * @param songName The song name
     * @param albumName The album name
     * @param artistName The artist name
     */
    public void addSong(final String songId, final String songHost, final String songName, final String albumName,
            final String artistName) {
        if (songId == null || songHost == null || songName == null || albumName == null || artistName == null) {
            return;
        }

        final Long playCount = getPlayCount(songId, songHost);
        final SQLiteDatabase database = getWritableDatabase();
        final ContentValues values = new ContentValues(6);

        database.beginTransaction();

        values.put(FavoritesTable.SID, songId);
        values.put(FavoritesTable.HOST, songHost);
        values.put(FavoritesTable.NAME, songName);
        values.put(FavoritesTable.ALBUM, albumName);
        values.put(FavoritesTable.ARTIST, artistName);
        values.put(FavoritesTable.PLAY_COUNT, playCount != 0 ? playCount + 1 : 1);

        database.delete(FavoritesTable.TABLE_NAME, FavoritesTable.SID + " = ? AND " + FavoritesTable.HOST + " = ?", new String[] {
        	songId,
            songHost
        });
        database.insert(FavoritesTable.TABLE_NAME, null, values);
        database.setTransactionSuccessful();
        database.endTransaction();
    }
    
    /**
     * @param item The song Id to remove
     */
    public void removeSong(final String songId, final String songHost) {
        final SQLiteDatabase database = getReadableDatabase();
        database.delete(FavoritesTable.TABLE_NAME, FavoritesTable.SID + " = ? AND " + FavoritesTable.HOST + " = ?", new String[] {
        	songId,
            songHost
        });
    }

    /**
     * Used to retrieve the play count
     * 
     * @param songId The song Id to reference
     * @param songHost The song Host to reference
     * @return The play count for a song
     */
    public Long getPlayCount(final String songId, final String songHost) {
        if (songId == null || songHost == null) {
            return null;
        }

        final SQLiteDatabase database = getReadableDatabase();
        final String[] projection = new String[] {
        	FavoritesTable.PLAY_COUNT
        };
        final String selection = FavoritesTable.SID + " = ? AND " + FavoritesTable.HOST + " = ?";
        final String[] having = new String[] {
        	songId,
            songHost
        };
        Cursor cursor = database.query(FavoritesTable.TABLE_NAME, projection, selection, having, null,
                null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            final Long playCount = cursor.getLong(0);
            cursor.close();
            cursor = null;
            return playCount;
        }
        if (cursor != null) {
            cursor.close();
            cursor = null;
        }

        return (long)0;
    }
    
    /**
     * Toggle the current song as favorite
     */
    public void toggleSong(final String songId, final String songHost, final String songName, final String albumName,
            final String artistName) {
        if (isFavoriteSong(songId, songHost)) {
            addSong(songId, songHost, songName, albumName, artistName);
        } else {
            removeSong(songId, songHost);
        }
    }
    
    /**
     * Used to check if a single song is favorite
     * 
     * @param songId The song Id to reference
     * @param songHost The song Host to reference
     * @return Favorite or not
     */
    public boolean isFavoriteSong(final String songId, final String songHost) {
        if (songId == null || songHost == null) {
            return false;
        }

        final SQLiteDatabase database = getReadableDatabase();
        final String[] projection = new String[] {
        	FavoritesTable.SID
        };
        final String selection = FavoritesTable.SID + " = ? AND " + FavoritesTable.HOST + " = ?";
        final String[] having = new String[] {
        	songId,
            songHost
        };
        Cursor cursor = database.query(FavoritesTable.TABLE_NAME, projection, selection, having, null,
                null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            final String id = cursor.getString(0);
            cursor.close();
            cursor = null;
            if (id != null) {
            	return true;
            }
        }
        if (cursor != null) {
            cursor.close();
            cursor = null;
        }
        return false;
    }

    /**
     * Clear the cache.
     * 
     * @param context The {@link Context} to use.
     */
    public static void deleteDatabase(final Context context) {
        context.deleteDatabase(DATABASENAME);
    }

    public interface FavoritesTable {
        /* Table name */
        public static final String TABLE_NAME = "favorites";

        /* Song IDs column */
        public static final String SID = "sId";
        
        /* Song Host column */
        public static final String HOST = "host";

        /* Song name column */
        public static final String NAME = "name";

        /* Album name column */
        public static final String ALBUM = "album";

        /* Artist name column */
        public static final String ARTIST = "artist";

        /* Play count column */
        public static final String PLAY_COUNT = "playcount";
    }

}
