package com.example.voicerecorder;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.provider.BaseColumns;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;


public class RecordingsActivity extends AppCompatActivity {

    private RecyclerView recordingsList;
    private RecordingListAdapter adapter;
    //handler to post UI updates from SQL thread to main thread
    private Handler handler;
    //DB helper to do SQL transactions
    private RecordingDBHelper recordingDBHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recordings);


        recordingsList = findViewById(R.id.recordings_list);
        recordingsList.setLayoutManager(new LinearLayoutManager(this));

        handler = new Handler();

        recordingDBHelper = new RecordingDBHelper(this);

        //adapter = new RecordingListAdapter(new ArrayList<>(),);
        recordingsList.setAdapter(null);
        //get recordings from SQL database and create the list
        loadRecordingsList();
    }

    private void loadRecordingsList(){

         Thread getRecordingsThread = new Thread(new Runnable() {
            @Override
            public void run() {

                SQLiteDatabase db = recordingDBHelper.getReadableDatabase();
                //get the entire table
                Cursor cursor = db.rawQuery("select * from " + RecordingDBHelper.RecordingDBSchema.TABLE_NAME,null);

                ArrayList<Recording> recordingArrayList = new ArrayList<>();
                //create objects and add them to the arraylist
                while(cursor.moveToNext()){

                    Recording r = new Recording();
                    r.setId(cursor.getLong(cursor.getColumnIndex(BaseColumns._ID)));
                    r.setName(cursor.getString(cursor.getColumnIndex(RecordingDBHelper.RecordingDBSchema.COLUMN_NAME)));
                    r.setDurationSec(cursor.getInt(cursor.getColumnIndex(RecordingDBHelper.RecordingDBSchema.COLUMN_DURATION_SEC)));
                    r.setSizeMB(cursor.getFloat(cursor.getColumnIndex(RecordingDBHelper.RecordingDBSchema.COLUMN_SIZE_MB)));
                    r.setTimeMilis(cursor.getLong(cursor.getColumnIndex(RecordingDBHelper.RecordingDBSchema.COLUMN_TIME_MILIS)));

                    recordingArrayList.add(r);


                }

                cursor.close();

                db.close();
                //add them to recyclerview
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        adapter = new RecordingListAdapter(recordingArrayList,RecordingsActivity.this);
                        recordingsList.setAdapter(adapter);
                    }
                });
            }
        });

         getRecordingsThread.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //hide nav bar
        View decorView = getWindow().getDecorView();
        // Hide both the navigation bar and the status bar.
        // SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
        // a general rule, you should design your app to hide the status bar whenever you
        // hide the navigation bar.
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

    }

    @Override
    protected void onDestroy() {
        //close DB helper
        recordingDBHelper.close();
        super.onDestroy();
    }

}
