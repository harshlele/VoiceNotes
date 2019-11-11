package com.harshall.voicerecorder;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

//recordings database helper

public class RecordingDBHelper extends SQLiteOpenHelper {
    //version and name
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "Recordings.db";

    public RecordingDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {

        String SQL_CREATE_TABLE = "CREATE TABLE " + RecordingDBSchema.TABLE_NAME + " (" +
                                    RecordingDBSchema._ID + " INTEGER PRIMARY KEY," +
                                    RecordingDBSchema.COLUMN_NAME + " TEXT," +
                                    RecordingDBSchema.COLUMN_SIZE_MB + " FLOAT," +
                                    RecordingDBSchema.COLUMN_DURATION_SEC + " INTEGER," +
                                    RecordingDBSchema.COLUMN_TIME_MILIS + " INTEGER)";


        sqLiteDatabase.execSQL(SQL_CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        onCreate(sqLiteDatabase);
        //TODO: DO SOMETHING ABOUT THIS!
    }

    //the schema of the database
    static final class RecordingDBSchema implements BaseColumns{
        static final String TABLE_NAME = "recordings";                  //table name
        static final String COLUMN_NAME = "name";                       //name of the recording wav file(only name, not absolute path)
        static final String COLUMN_SIZE_MB = "size_mb";                 //size in mb
        static final String COLUMN_DURATION_SEC = "duration_sec";       //duration in seconds
        static final String COLUMN_TIME_MILIS = "time_msec";            //recording start time in milliseconds since epoch

    }


}
