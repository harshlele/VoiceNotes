package com.example.voicerecorder;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.provider.BaseColumns;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
                //if something is playing, stop it
                if(isPlaying){
                    isPlaying = false;
                    mediaPlayer.stop();
                }
                //reset mediaplayer
                mediaPlayer.reset();

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
                seekBar.setProgress(0);
                seekBar.setMax(mediaPlayer.getDuration());

                mediaPlayer.start();

                startPlaybackTimeThread();
                isPlaying = true;

            }
        };
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
                        adapter = new RecordingListAdapter(recordingArrayList,RecordingsActivity.this,recordingClickedListener);
                        recordingsList.setAdapter(adapter);
                    }
                });
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
    protected void onResume() {
        super.onResume();
        //initialise mediaplayer in onResume because it has to be re-initialised every time the user comes back to the activity
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        //set the listeners(defined in onCreate)
        mediaPlayer.setOnPreparedListener(onPreparedListener);
        mediaPlayer.setOnCompletionListener(onCompletionListener);
        mediaPlayer.setOnErrorListener(onErrorListener);
    }


    //get the directory where voice notes are stored
    private File getVoiceNotesDir(){
        return new File(getExternalFilesDir(null),"Voice Notes");
    }


}
