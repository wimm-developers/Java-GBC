package org.bostwickenator.javagbc;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Database Wrapper
 * 
 * DB Schema: ------------------------------------ Create table tblData ( _id
 * int primary key autoincrement, alert_text text, alert_date varchar(20),
 * alert_time varchar(20) );
 */
public class RomDatabase {

	/*******************************************************
	 * These constants refer to the fields in the database. _id is Android's
	 * naming convention for ID fields. Our database has 3 fields, so we have 3
	 * constants.
	 ********************************************************/

	public static final String KEY_ROM_NAME = "rom_name";
	public static final String KEY_ROM_PATH = "rom_path";
	public static final String KEY_STATE_NAME = "state_name";
	public static final String KEY_STATE_IMAGE = "state_image";
	public static final String KEY_ROMID = "rom_id";
	public static final String KEY_ROWID = "_id";
	public static final String KEY_SROWID = "_sid";
	public static final String[] allVals1 = new String[] { KEY_ROWID,
			KEY_ROM_NAME, KEY_ROM_PATH };
	public static final String[] allVals2 = new String[] { KEY_ROWID,
			KEY_STATE_NAME, KEY_STATE_IMAGE, KEY_ROMID };
	public static final String[] mergedVals = new String[] { KEY_ROWID,
			KEY_ROM_NAME, KEY_ROM_PATH, KEY_STATE_IMAGE, KEY_SROWID };

	/*******************************************************
	 * This is not needed for the database. It is here to help us tag our
	 * logging messages
	 *******************************************************/
	public static final String TAG = "AlertDB";

	private DatabaseHelper dbHelper;
	private SQLiteDatabase db;

	private Context mCtx = null;

	/*******************************************************
	 * Some other constants related to our database's information. They should
	 * be self explanatory
	 ********************************************************/
	private static final String DATABASE_NAME = "rom_db";
	private static final String DATABASE_TABLE1 = "roms";
	private static final String DATABASE_TABLE2 = "states";
	private static final int DATABASE_VERSION = 6;
	private static final String DATABASE_CREATE1 = "create table roms ( "
			+ "_id integer primary key autoincrement, "
			+ "rom_name varchar(60), " + "rom_path varchar(60)" + ");";
	private static final String DATABASE_CREATE2 = "create table states ( "
			+ "_id integer primary key autoincrement, "
			+ "state_name varchar(60), " + "state_image blob, "
			+ "rom_id integer" + ");";

	/********************************************************
	 * Think of this as a driver for your database interaction. You can just
	 * copy and paste this part in all your database interaction classes.
	 *********************************************************/
	private static class DatabaseHelper extends SQLiteOpenHelper {
		DatabaseHelper(Context ctx) {
			super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE1);
			db.execSQL(DATABASE_CREATE2);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("DROP TABLE IF EXISTS " + DATABASE_TABLE1);
			db.execSQL("DROP TABLE IF EXISTS " + DATABASE_TABLE2);
			onCreate(db);
		}
	}

	/** Constructor */
	public RomDatabase(Context ctx) {
		mCtx = ctx;
	}

	/********************************************************
	 * This opens a connection to the database but if something goes wrong, it
	 * will throw an exception.
	 ********************************************************/
	public RomDatabase open() throws SQLException {
		dbHelper = new DatabaseHelper(mCtx);
		db = dbHelper.getWritableDatabase();
		return this;
	}

	/** Closes a database connection */
	public void close() {
		dbHelper.close();
	}

	public Cursor fetchAllRoms() {
		try {
			return db
					.rawQuery(
							"SELECT states._id AS _sid, roms._id, roms.rom_name, roms.rom_path, states.state_image, states.state_name, states.rom_id FROM roms LEFT JOIN states ON roms._id=states.rom_id WHERE states.state_name='temp' ORDER BY roms.rom_name",
							null);

		} catch (Exception e) {
			Log.e("DBLayer", e.getMessage());
			return null;
		}
	}

	public Cursor fetchStates(int romId) {
		try {
			return db.query(DATABASE_TABLE2, allVals2, KEY_ROMID + "=" + romId
					+ " AND " + KEY_STATE_NAME + "!='temp'", null, null, null,
					KEY_STATE_NAME);
		} catch (Exception e) {
			Log.e("DBLayer", e.getMessage());
			return null;
		}
	}

	public long addRom(String romName, String romPath) {
		ContentValues vals = new ContentValues();
		vals.put(KEY_ROM_NAME, romName);
		vals.put(KEY_ROM_PATH, romPath);
		return db.insert(DATABASE_TABLE1, null, vals);
	}

	public long addState(int romId, String stateName) {
		// Make sure save state with same name hasn't already been added to
		// database
		Cursor checkIfExists = db.query(DATABASE_TABLE2, allVals2, KEY_ROMID
				+ "=" + romId + " AND " + KEY_STATE_NAME + "='" + stateName
				+ "'", null, null, null, null);
		if (checkIfExists.getCount() > 0) {
			checkIfExists.moveToFirst();
			return checkIfExists.getInt(checkIfExists
					.getColumnIndex(RomDatabase.KEY_ROWID));
		}

		// If this is a new state then add it to database
		ContentValues vals = new ContentValues();
		vals.put(KEY_ROMID, romId);
		vals.put(KEY_STATE_NAME, stateName);
		return db.insert(DATABASE_TABLE2, null, vals);
	}

	public boolean updateRomPath(int id, int romPath) {
		ContentValues vals = new ContentValues();
		vals.put(KEY_ROM_PATH, romPath);
		int success = db.update(DATABASE_TABLE1, vals, KEY_ROWID + "=" + id,
				null);
		return success > 0;
	}

	public boolean updateStateName(int id, int stateName) {
		ContentValues vals = new ContentValues();
		vals.put(KEY_STATE_NAME, stateName);
		int success = db.update(DATABASE_TABLE2, vals, KEY_ROWID + "=" + id,
				null);
		return success > 0;
	}

	public int updateStateImage(int romId, String stateName, int[] stateImage) {
		String data = KiddGBC.pack(stateImage);
		ContentValues vals = new ContentValues();
		vals.put(KEY_STATE_IMAGE, data);
		int success = db.update(DATABASE_TABLE2, vals, KEY_STATE_NAME + "='"
				+ stateName + "' AND " + KEY_ROMID + "=" + romId, null);
		Cursor cursor = db.query(DATABASE_TABLE2, new String[] { KEY_ROWID },
				KEY_STATE_NAME + "='" + stateName + "' AND " + KEY_ROMID + "="
						+ romId, null, null, null, null);
		cursor.moveToFirst();
		int id = cursor.getInt(cursor.getColumnIndex(KEY_ROWID));
		cursor.close();
		return id;
	}

	public boolean deleteRom(long rowId) {
		return db.delete(DATABASE_TABLE1, KEY_ROWID + "=" + rowId, null) > 0;
	}

	public boolean deleteState(long rowId) {
		return db.delete(DATABASE_TABLE2, KEY_ROWID + "=" + rowId, null) > 0;
	}

	public boolean deleteAll() {
		boolean result1 = db
				.delete(DATABASE_TABLE1, KEY_ROWID + ">" + -1, null) > 0;
		boolean result2 = db
				.delete(DATABASE_TABLE2, KEY_ROWID + ">" + -1, null) > 0;
		return result1 & result2;
	}

}