package com.ssb.droidsound;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.ssb.droidsound.SongDatabase.ScanCallback;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.provider.BaseColumns;
import android.util.Log;

public class CSDBParser implements SongDatabase.DataSource {
	private static final String TAG = CSDBParser.class.getSimpleName();

	public static final String DUMP_NAME = "CSDB.DUMP";
	
	
	private static String hvsc = null;
	
	static Map<String, Integer> events = new HashMap<String, Integer>();
	static HashMap<Integer, String> groups = null;
	static String pathName = null;
	
	public static void setPath(String p) {
		pathName = p;
	}
		
	
	static boolean parseCSDB(File file, SQLiteDatabase db, SongDatabase.ScanCallback scanCallback) {
		
		boolean ok = false;
		
		db.execSQL("DROP TABLE IF EXISTS RELEASES;");
		db.execSQL("DROP TABLE IF EXISTS GROUPS;");
		db.execSQL("DROP TABLE IF EXISTS EVENTS;");
		db.execSQL("DROP TABLE IF EXISTS RELEASESIDS;");
		
		db.execSQL("CREATE TABLE IF NOT EXISTS " + "RELEASES" + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY," +
				"ID INTEGER," +
				"NAME TEXT," +
				"GROUPID INTEGER," +
				"TYPE TEXT," +
				"DATE INTEGER," +
				"EVENTID INTEGER," +
				"RATING INTEGER," +
				"PLACE INTEGER" + ");");

		db.execSQL("CREATE TABLE IF NOT EXISTS " + "GROUPS" + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY," +
				"ID" + " INTEGER," +
				"NAME" + " TEXT" + ");");

		db.execSQL("CREATE TABLE IF NOT EXISTS " + "EVENTS" + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY," +
				"ID" + " INTEGER," +
				"DATE" + " INTEGER," +
				"NAME" + " TEXT" + ");");

		db.execSQL("CREATE TABLE IF NOT EXISTS " + "RELEASESIDS" + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY," +
				"RELEASEID" + " INTEGER," +
				"PATH" + " TEXT," +
				"FILENAME" + " TEXT" + ");");

		
		BufferedReader reader;
		db.beginTransaction();
		try {
			int place = -1;
			Log.v(TAG, "OPENING CSDB");			
			reader = new BufferedReader(new FileReader(file));				
			String line = reader.readLine();
			int count = 0;
			int total = line.length()+1;
			int fileSize = (int) file.length();
			while(line != null) {
				
				count++;
				if((count % 300) == 0) {
					if(scanCallback != null) {
						scanCallback.notifyScan(null, total * 100 / fileSize);
					}
					db.yieldIfContendedSafely();
				}

				if(line.equals("[Releases]")) {
					Log.v(TAG, "RELEASES");
					place = 0;
				} else
				if(line.equals("[Groups]")) {
					Log.v(TAG, "GROUPS");
					place = 1;
				} else
				if(line.equals("[Events]")) {
					Log.v(TAG, "EVENTS");
					place = 2;
				} else {
					String[] args = line.split("\t");
					ContentValues values = new ContentValues();
					if(place == 0) {
						// (id, name, gid, type, y, eid, compo, place, ','.join(fnames))						
						
						values.put("ID", Integer.parseInt(args[0]));
						values.put("NAME", args[1]);
						if(args[2].length() > 0)
							values.put("GROUPID", Integer.parseInt(args[2])); 
						values.put("TYPE", args[3]); 
						if(args[4].length() > 0)
							values.put("DATE", Integer.parseInt(args[4])); 
						if(args[5].length() > 0)
							values.put("EVENTID", Integer.parseInt(args[5]));
						if(args[6].length() > 0)
							values.put("PLACE", Integer.parseInt(args[6]));
						if(args[7].length() > 0)
							values.put("RATING", Integer.parseInt(args[7]));
						
						db.insert("RELEASES", "ID", values);
						
						String [] sids = args[8].split(",");
						if(sids.length > 0) {
							ContentValues values2 = new ContentValues();
							for(String s : sids) {
								values2.put("RELEASEID", Integer.parseInt(args[0]));
								File f = new File(s);
								values2.put("FILENAME", f.getName());
								values2.put("PATH", f.getParent());
								db.insert("RELEASESIDS", "PATH", values2);
							}
						}
					} else if(place == 1) {
						//Log.v(TAG, String.format("Group (%s) %s \n", args[0], args[1]));
						values.put("ID", Integer.parseInt(args[0]));
						values.put("NAME", args[1]);
						db.insert("GROUPS", "ID", values);
					} else if(place == 2) {
						//Log.v(TAG, String.format("Event (%s) %s \n", args[0], args[1]));
						values.put("ID", Integer.parseInt(args[0]));
						values.put("DATE", Integer.parseInt(args[1]));
						values.put("NAME", args[2]);
						db.insert("EVENTS", "ID", values);
					}
				}
				line = reader.readLine();
				if(line != null) {
					total += (line.length()+1);
				}
			}
			db.setTransactionSuccessful();
			ok = true;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			db.endTransaction();
		}
		Log.v(TAG, "DONE");
		
		return ok;
	}

	/*
	 * CSDB:EVENTS/X2008/EdgeOfDisgrace/  song.sid
	 * CSDB:RELEASES/EdgeOfDisgrace/ song.sid
	 * CSDB:RELEASES/SomeCollection/ song.sid
	 * CSDB:GROUPS/BoozeDesign/EdgeOfDisgrace/ song.sid
	 * 
	 * select * from releasesids where group=booze release=eod
	 * 
	 */
	
	static class DirWrapper extends CursorWrapper {

		public DirWrapper(Cursor cursor) {
			super(cursor);
		}
		
		@Override
		public int getColumnIndex(String columnName) {
			if(columnName.equals("TYPE")) {
				return 99;
			}
			return super.getColumnIndex(columnName);
		}
		
		@Override
		public int getInt(int columnIndex) {
			if(columnIndex == 99) {
				return SongDatabase.TYPE_DIR;
			}
			return super.getInt(columnIndex);
		}
	};

	static class SidCursor extends CursorWrapper {
		private int pathIndex;

		public SidCursor(Cursor cursor) {
			super(cursor);
			pathIndex = cursor.getColumnIndex("PATH");
		}
		
		@Override
		public int getColumnIndex(String columnName) {
			if(columnName.equals("PATH")) {
				return 99;
			} else
			if(columnName.equals("SUBTITLE")) {
				return 98;
			}
			return super.getColumnIndex(columnName);
		}
		
		@Override
		public String getString(int columnIndex) {
			if(columnIndex == 99) {
				String path = getString(pathIndex);
				return hvsc + path;
			} else
			if(columnIndex == 98) {
				return getString(pathIndex);
			}
			return super.getString(columnIndex);
		}
		
	}
	
	/* static class PathWrapper extends CursorWrapper {

		public PathWrapper(Cursor cursor) {
			super(cursor);
		}
		
		@Override
		public int getColumnIndex(String columnName) {
			if(columnName.equals("PATH")) {
				return 99;
			} else
			if(columnName.equals("FILENAME")) {
				return 98;
			}
			return super.getColumnIndex(columnName);
		}
		
		@Override
		public String getString(int columnIndex) {
			if(columnIndex == 99) {
				return 
			}
		}
	} */
	
	static class ReleaseCursor extends CursorWrapper {
		private int gidIndex;
		private int nameIndex;
		private int typeIndex;
		private int placeIndex;
		private int ratingIndex;
		private String pathName;

		public ReleaseCursor(Cursor cursor, String path) {
			super(cursor);
			pathName = path;
			gidIndex = cursor.getColumnIndex("GROUPID");
			nameIndex = cursor.getColumnIndex("NAME");
			typeIndex = cursor.getColumnIndex("TYPE");
			placeIndex = cursor.getColumnIndex("PLACE");
			ratingIndex = cursor.getColumnIndex("RATING");
		}
		
		public ReleaseCursor(Cursor cursor) {
			super(cursor);
			gidIndex = cursor.getColumnIndex("GROUPID");
			nameIndex = cursor.getColumnIndex("NAME");
			typeIndex = cursor.getColumnIndex("TYPE");
			placeIndex = cursor.getColumnIndex("PLACE");
			ratingIndex = cursor.getColumnIndex("RATING");
		}
		
		@Override
		public int getColumnIndex(String columnName) {
			if(columnName.equals("TYPE")) {
				return 97;
			}
			if(columnName.equals("TITLE")) {
				return 99;
			}
			if(columnName.equals("SUBTITLE")) {
				return 98;
			}
			if(pathName != null && columnName.equals("PATH")) {
				return 96;
			}
			return super.getColumnIndex(columnName);
		}
		
		@Override
		public String getString(int columnIndex) {
			
			if(columnIndex == 99) {
				String group = groups.get(getInt(gidIndex));
				if(group != null) {
					return String.format("%s / %s", getString(nameIndex), group);
				} else {
					return getString(nameIndex);
				}
			} else
			if(columnIndex == 98) {
				if(placeIndex >= 0) {
					return String.format("%s #%d", getString(typeIndex), getInt(placeIndex));
				} else {
					int r = getInt(ratingIndex);
					if(r > 0) {
						return String.format("%s (%02.2f)", getString(typeIndex), ((float)r)/100.0);
					} else {
						return getString(typeIndex);
					}
				}
			} else if(columnIndex == 96) {
				return pathName + "/SEARCH";
			}
			return super.getString(columnIndex);
		}
		
		@Override
		public int getInt(int columnIndex) {
			if(columnIndex == 97) {
				return SongDatabase.TYPE_DIR;
			}
			return super.getInt(columnIndex);
		}
	}

	public static Cursor getPath(String path, SQLiteDatabase rdb) {
		
		String [] parts = path.split("/");
		int n = parts.length;
		boolean found = false;

		for(int i=0; i<n; i++) {
			Log.v(TAG, String.format("PART %d: '%s'", i, parts[i]));
		}

		for(int i=0; i<n; i++) {
			if(parts[i].toUpperCase().equals(DUMP_NAME)) {
				for(int j=0; j<(n-i); j++) {
					parts[j] = parts[i+j];
				}
				found = true;
				n -= i;
				break;
			}
		}

		if(!found) {
			return null;
		}

		for(int i=0; i<n; i++) {
			Log.v(TAG, String.format("PART %d: '%s'", i, parts[i]));
		}

		if(groups == null) {
			 groups = new HashMap<Integer, String>();
			 Cursor c = rdb.rawQuery("select id, name from groups", null);
			 c.moveToPosition(-1);
			 while(c.moveToNext()) {
				 groups.put(c.getInt(0), c.getString(1));
			 }
			 c.close();
		}
		
		if(hvsc == null) {
			Cursor c = rdb.rawQuery("select path from files where path like '%C64Music'", null);			
			if(c.getCount() > 0) {
				c.moveToFirst();
				hvsc = c.getString(0);
			} else {
				File extFile = Environment.getExternalStorageDirectory();
				if(extFile != null) {
					hvsc = extFile + "/C64Music.zip/C64Music";
				} else {
					hvsc = "/sdcard/MODS/C64Music.zip/C64Music";
				}
			}
			c.close();
		}
		
		if(n == 1) {			
			MatrixCursor cursor = new MatrixCursor(new String [] {"NAME", "ID", "TYPE"});
			cursor.addRow(new Object [] { "EVENTS", 0, SongDatabase.TYPE_DIR} );
			cursor.addRow(new Object [] { "GROUPS", 1, SongDatabase.TYPE_DIR} );
			cursor.addRow(new Object [] { "TOP DEMOS", 2, SongDatabase.TYPE_DIR} );
			cursor.addRow(new Object [] { "TOP MUSIC", 3, SongDatabase.TYPE_DIR} );
			cursor.addRow(new Object [] { "TOP RELEASES", 4, SongDatabase.TYPE_DIR} );
			cursor.addRow(new Object [] { "LATEST RELEASES", 5, SongDatabase.TYPE_DIR} );
			return cursor;
		} else if(n == 2) {
			if(parts[1].equals("SEARCH")) {
				return null;
			} else
			if(parts[1].equals("EVENTS")) {
				return new DirWrapper(rdb.rawQuery("select name, date, id from events order by date desc", null));
			} else
			if(parts[1].equals("GROUPS")) {
				return new DirWrapper(rdb.rawQuery("select name, id from groups order by name", null));
			} else
			if(parts[1].equals("TOP DEMOS")) {
				return new ReleaseCursor(rdb.rawQuery("select name, type, groupid, rating from releases where type='C64 Demo' order by rating desc limit 500", null));			
			} else
			if(parts[1].equals("TOP MUSIC")) {
				return new ReleaseCursor(rdb.rawQuery("select name, type, groupid, rating from releases where type='C64 Music' order by rating desc limit 250", null));			
			} else
			if(parts[1].equals("TOP RELEASES")) {
				return new ReleaseCursor(rdb.rawQuery("select name, type, groupid, rating from releases order by rating desc limit 2000", null));			
			} else
			if(parts[1].equals("LATEST RELEASES")) {
				return new ReleaseCursor(rdb.rawQuery("select name, type, groupid, rating from releases order by date desc limit 2000", null));			
			} else {
				return null;
			}
		} else if(n == 3) {
			if(parts[1].equals("EVENTS")) {
				/*Integer evid = events.get(parts[2]);
				if(evid == null) {
					Cursor c = rdb.rawQuery("select id from events where name=?", new String[]  { parts[2] });
					c.moveToFirst();
					evid = c.getInt(0);
				} */			
				//return new ReleaseCursor(rdb.rawQuery("select name, type, groupid, place, rating from releases where eventid=? order by type, place", new String[] { evid.toString() }));
				return new ReleaseCursor(rdb.rawQuery("select name, type, groupid, place, rating from releases where eventid in (select id from events where name=?) order by type, place", new String[] { parts[2] }));
			} else if(parts[1].equals("GROUPS")) {
				return new ReleaseCursor(rdb.rawQuery("select name, type, groupid, rating from releases where groupid in (select id from groups where name=?) order by rating desc", new String[] { parts[2] }));				
			}
			else {
				return new SidCursor(rdb.rawQuery("select path,filename from releasesids where releaseid in (select id from releases where name=?)", new String[] { parts[2] }));
			}
		} else {
			return new SidCursor(rdb.rawQuery("select path,filename from releasesids where releaseid in (select id from releases where name=?)", new String[] { parts[3] })); 
			//return new SidWrapper("select releaseid, path, filename from releasesids where releaseid=?
		}
		//return rdb.rawQuery("select releases.name, groups.name from releases,groups where releases.groupid=groups.id and releases.eventid=1577", null);
		//return rdb.query("RELEASES", new String[] { "NAME AS TITLE, GROUP AS COMPOSER" }, "EVENTID=?", new String[] { "1577" }, null, null, "NAME");
	}

	public static Cursor search(SQLiteDatabase db, String query) {
		Log.v(TAG, "QUERY:" + query);
		return new ReleaseCursor(db.rawQuery("select name, type, groupid, rating from releases where name like ? limit 250", new String [] {"%" + query + "%"} ), "CSDB:");		
	}


	@Override
	public String getTitle() {
		return "CSDb";
	}


	@Override
	public boolean parseDump(File dump, SQLiteDatabase scanDb, ScanCallback scanCallback) {
		return parseCSDB(dump, scanDb, scanCallback);
	}


	@Override
	public Cursor getCursorFromPath(File file, SQLiteDatabase rdb) {
		return getPath(file.getPath(), rdb);
	}


	@Override
	public String getPathTitle(File file) {
		return null;
	}


	@Override
	public void createIndex(int mode, SQLiteDatabase db) {
		switch(mode) {
		case SongDatabase.INDEX_NONE:
			db.execSQL("DROP INDEX IF EXISTS relindex ;");		
			db.execSQL("DROP INDEX IF EXISTS grpindex ;");
			db.execSQL("DROP INDEX IF EXISTS evtindex ;");
			break;
		case SongDatabase.INDEX_BASIC:
			db.execSQL("DROP INDEX IF EXISTS relindex ;");		
			db.execSQL("DROP INDEX IF EXISTS grpindex ;");
			db.execSQL("DROP INDEX IF EXISTS evtindex ;");
			break;
		case SongDatabase.INDEX_FULL:
			db.execSQL("CREATE INDEX IF NOT EXISTS relindex ON RELEASES (NAME) ;");		
			db.execSQL("CREATE INDEX IF NOT EXISTS grpindex ON GROUPS (NAME) ;");
			db.execSQL("CREATE INDEX IF NOT EXISTS evtindex ON EVENTS (NAME) ;");
			break;
		}		
	}


	@Override
	public Cursor search(String query, String fromPath, SQLiteDatabase db) {
		return search(db, query);
	}	

}
