package com.example.voicerecorder;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.ohoussein.playpause.PlayPauseView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;


public class RecordingsActivity extends AppCompatActivity {
    //tag for logging
    private static final String TAG = "Notes";

    //to check whether recording is being played
    private boolean isPlaying = false;
    //to check if player is paused
    private boolean isPlayerPaused = false;

    private RecyclerView recordingsList;
    private PlayPauseView playPauseBtn;
    private AppCompatSeekBar seekBar;
    private TextView currentRecordingText;

    private RecordingListAdapter adapter;
    //handler to post UI updates from SQL thread to main thread
    private Handler handler;
    //DB helper to do SQL transactions
    private RecordingDBHelper recordingDBHelper;

    //interface to get click events from RecordingListAdapter
    private RecordingClickedListener recordingClickedListener;
    //media player and listeners
    private MediaPlayer mediaPlayer;
    private MediaPlayer.OnCompletionListener onCompletionListener;
    private MediaPlayer.OnPreparedListener onPreparedListener;
    private MediaPlayer.OnErrorListener onErrorListener;
    //currently playing recording
    private Recording currentPlayingRecording;
    //thread that updates seekbar to current position
    private Thread playBackTimeThread;
    //if activity was switched while playback was ongoing, this is the current progress of playback
    private int playbackPausedPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recordings);


        recordingsList = findViewById(R.id.recordings_list);
        playPauseBtn = findViewById(R.id.play_pause_btn);
        seekBar = findViewById(R.id.seek_bar);
        currentRecordingText = findViewById(R.id.current_recording_name);

        recordingsList.setLayoutManager(new LinearLayoutManager(this));

        //set custom fonts for the recording name textviews
        Typeface nunitoReg = Typeface.createFromAsset(getAssets(), "NunitoSans-Regular.ttf");
        currentRecordingText.setTypeface(nunitoReg);

        handler = new Handler();

        recordingDBHelper = new RecordingDBHelper(this);

        //adapter = new RecordingListAdapter(new ArrayList<>(),);
        recordingsList.setAdapter(null);
        //get recordings from SQL database and create the list
        loadRecordingsList();

        //listener for when a recording list item is clicked
        recordingClickedListener = new RecordingClickedListener() {
            @Override
            public void onClicked(Recording recording) {
                //reset mediaplayer
                mediaPlayer.reset();
                //change UI
                playPauseBtn.change(false);
                currentRecordingText.setText(recording.getName());

                currentPlayingRecording = recording;
                //prepare clicked recording
                String recordingPath = getVoiceNotesDir().getAbsolutePath() + "/" + recording.getName();
                try {
                    FileInputStream inputStream = new FileInputStream(recordingPath);
                    mediaPlayer.setDataSource(inputStream.getFD());
                    mediaPlayer.prepareAsync();
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(),"Error on playback",Toast.LENGTH_LONG).show();
                }
            }
        };

        //click listener for play/pause button
        playPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(isPlaying){

                    if(!isPlayerPaused) {
                        mediaPlayer.pause();
                        playPauseBtn.change(true);
                        isPlayerPaused = true;
                        playBackTimeThread.interrupt();
                    }
                    else{
                        mediaPlayer.start();
                        playPauseBtn.change(false);
                        isPlayerPaused = false;
                        startPlaybackTimeThread();
                    }
                }

            }
        });
        //seekbar listener
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                mediaPlayer.seekTo(i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        defineMediaPlayerListeners();
    }

    //initialise MediaPlayer listeners
    private void defineMediaPlayerListeners(){
        onCompletionListener = new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                playBackTimeThread.interrupt();
                seekBar.setProgress(seekBar.getMax());
                isPlaying = false;
                playPauseBtn.change(true);
                currentRecordingText.setText("");
            }
        };

        onErrorListener = new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                Toast.makeText(getApplicationContext(),"MediaPlayer Error",Toast.LENGTH_LONG).show();
                return true;
            }
        };

        onPreparedListener = new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                if(!isPlayerPaused){
                    //set mediaplayer progress
                    seekBar.setProgress(0);
                    seekBar.setMax(mediaPlayer.getDuration());
                    //start playback
                    mediaPlayer.start();
                    //start thread to update seekbar with mediaplayer progress
                    startPlaybackTimeThread();
                    isPlaying = true;
                }
                //if the player was paused before preparing, it means that the activity was switched
                else{
                    seekBar.setMax(mediaPlayer.getDuration());
                    seekBar.setProgress(playbackPausedPosition);
                    mediaPlayer.seekTo(playbackPausedPosition);
                    isPlaying = true;
                    playbackPausedPosition = 0;
                }
            }
        };
    }

    private void loadRecordingsList(){

         Thread getRecordingsThread = new Thread(new Runnable() {
            @Override
            public void run() {

                SQLiteDatabase db = recordingDBHelper.getReadableDatabase();
                //get the entire table
                Cursor cursor = db.rawQuery("SELECT * FROM " + RecordingDBHelper.RecordingDBSchema.TABLE_NAME,null);

                ArrayList<Recording> recordingArrayList = new ArrayList<>();
                //list of ids of files that are not present. these will be deleted
                ArrayList<String> deleteList = new ArrayList<>();

                //create objects and add them to the arraylist
                while(cursor.moveToNext()){

                    //check if the file actually exists, only then add it to the list
                    String filePath = getVoiceNotesDir() + "/" + cursor.getString(cursor.getColumnIndex(RecordingDBHelper.RecordingDBSchema.COLUMN_NAME));
                    File f = new File(filePath);

                    if(f != null && f.exists()) {
                        Recording r = new Recording();
                        r.setId(cursor.getLong(cursor.getColumnIndex(BaseColumns._ID)));
                        r.setName(cursor.getString(cursor.getColumnIndex(RecordingDBHelper.RecordingDBSchema.COLUMN_NAME)));
                        r.setDurationSec(cursor.getInt(cursor.getColumnIndex(RecordingDBHelper.RecordingDBSchema.COLUMN_DURATION_SEC)));
                        r.setSizeMB(cursor.getFloat(cursor.getColumnIndex(RecordingDBHelper.RecordingDBSchema.COLUMN_SIZE_MB)));
                        r.setTimeMilis(cursor.getLong(cursor.getColumnIndex(RecordingDBHelper.RecordingDBSchema.COLUMN_TIME_MILIS)));

                        recordingArrayList.add(r);
                    }
                    //if the file doesn't exist, add it to a list of row ids for deletion
                    else{
                        deleteList.add(String.valueOf(cursor.getLong(cursor.getColumnIndex(BaseColumns._ID))));
                    }

                }
                cursor.close();

                //add them to recyclerview
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        adapter = new RecordingListAdapter(recordingArrayList,RecordingsActivity.this,recordingClickedListener);
                        recordingsList.setAdapter(adapter);
                    }
                });
                // if there are db rows where the file doesn't exist, delete those rows
                if(!deleteList.isEmpty()) {
                    //create query from deleteList
                    String deleteQuery = "DELETE FROM " + RecordingDBHelper.RecordingDBSchema.TABLE_NAME + " WHERE ";
                    for (String id : deleteList) {
                        deleteQuery += " _id=" + id;

                        if (deleteList.indexOf(id) != (deleteList.size() - 1)) {
                            deleteQuery += " OR";
                        }
                    }
                    //log query just in case, for debugging
                    Log.d(TAG, "run: NON-EXISTING FILE DELETE QUERY: " + deleteQuery);
                    Cursor c = db.rawQuery(deleteQuery, null);
                    c.close();
                }
                //close the db
                db.close();
            }
        });

         getRecordingsThread.start();
    }

    //start a thread that updates the seekbar to the current position of the playback
    private void startPlaybackTimeThread(){

        playBackTimeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(!Thread.currentThread().isInterrupted() && isPlaying && !isPlayerPaused) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            seekBar.setProgress(mediaPlayer.getCurrentPosition());
                        }
                    });
                }
            }
        });
        playBackTimeThread.start();
    }

    @Override
    protected void onDestroy() {
        //close DB helper
        recordingDBHelper.close();
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
        //release and nullify the mediaplayer so other apps can use it
        mediaPlayer.release();
        mediaPlayer = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        //if playback is ongoing, save the recording and progress details
        if(isPlaying || isPlayerPaused){
            //if the user paused the playback, the UI changes have already happened
            if(!isPlayerPaused){
                mediaPlayer.pause();
                playPauseBtn.change(true);
                isPlayerPaused = true;
                playBackTimeThread.interrupt();
            }

            //store file name and progress
            SharedPreferences pref = getApplicationContext().getSharedPreferences("NOTE_PAUSED_PREFS",MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            editor.putBoolean("NOTE_PAUSED",true);

            //use gson to convert Recording object into string
            Gson gson = new Gson();
            String obj = gson.toJson(currentPlayingRecording);
            editor.putString("NOTE_OBJ",obj);
            //store current position
            editor.putInt("NOTE_PROG",mediaPlayer.getCurrentPosition());
            editor.commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //initialise mediaplayer in onResume because it has to be re-initialised every time the user comes back to the activity
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        //set the listeners(defined in defineMediaPlayerListeners)
        mediaPlayer.setOnPreparedListener(onPreparedListener);
        mediaPlayer.setOnCompletionListener(onCompletionListener);
        mediaPlayer.setOnErrorListener(onErrorListener);

        //if a previous playback was paused, load it and set the progress
        SharedPreferences pref = getApplicationContext().getSharedPreferences("NOTE_PAUSED_PREFS",MODE_PRIVATE);
        if(pref.getBoolean("NOTE_PAUSED",false)){

            //use gson to get the stored Recording object
            Gson gson = new Gson();
            String obj = pref.getString("NOTE_OBJ","");
            currentPlayingRecording = gson.fromJson(obj,Recording.class);

            //prepare mediaplayer
            if(currentPlayingRecording != null) {
                String recordingPath = getVoiceNotesDir().getAbsolutePath() + "/" + currentPlayingRecording.getName();
                try {
                    FileInputStream inputStream = new FileInputStream(recordingPath);
                    mediaPlayer.setDataSource(inputStream.getFD());
                    mediaPlayer.prepareAsync();
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), "Error on playback", Toast.LENGTH_LONG).show();
                }
            }

            //save the current progress
            playbackPausedPosition = pref.getInt("NOTE_PROG",0);

            isPlayerPaused = true;
            isPlaying = true;

            //clear the SharedPreferences
            SharedPreferences.Editor editor = pref.edit();
            editor.remove("NOTE_PAUSED");
            editor.remove("NOTE_OBJ");
            editor.remove("NOTE_PROG");
            editor.commit();
        }

    }



    //get the directory where voice notes are stored
    private File getVoiceNotesDir(){
        return new File(getExternalFilesDir(null),"Voice Notes");
    }


}
