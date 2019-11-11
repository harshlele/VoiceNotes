package com.harshall.voicerecorder;

import android.content.DialogInterface;
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.ohoussein.playpause.PlayPauseView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;


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
    private TextView currentRecordingText,playbackProgressText;


    private RecordingListAdapter adapter;
    //handler to post UI updates from SQL thread to main thread
    private Handler handler;
    //DB helper to do SQL transactions
    private RecordingDBHelper recordingDBHelper;

    //interface to get click events from RecordingListAdapter
    private RecordingListListener recordingListListener;
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
        playbackProgressText = findViewById(R.id.playback_prog_text);

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
        recordingListListener = new RecordingListListener() {
            @Override
            public void onClicked(Recording recording) {
                Log.d(TAG, "onClicked: clicked!: " + recording.getName() );
                //reset mediaplayer
                mediaPlayer.reset();
                //change UI
                playPauseBtn.change(false);
                currentRecordingText.setText(recording.getName());

                currentPlayingRecording = recording;
                //prepare clicked recording
                String recordingPath = getVoiceNotesDir().getAbsolutePath() + "/" + recording.getName();
                Log.d(TAG, "onClicked: path: " + recordingPath);
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

            //listener for when the delete button on a recording is clicked
            @Override
            public void onDeleteBtnClicked(Recording recording) {
                //show a dialog asking for confirmation from the user
                AlertDialog.Builder deleteDialogBuilder = new AlertDialog.Builder(RecordingsActivity.this,R.style.DialogTheme);
                deleteDialogBuilder.setMessage("Delete " + recording.getName() + " ?" );
                //positive button
                deleteDialogBuilder.setPositiveButton("yes",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface arg0, int arg1) {
                                        //remove the recording from the list and get its position
                                        int i = adapter.removeRecording(recording.getId());
                                        //animate item removal
                                        if(i != -1) adapter.notifyItemRemoved(i);
                                        //delete the actual recording file
                                        deleteRecording(recording);
                                    }
                                });
                //if the user presses no, just dismiss the dialog
                deleteDialogBuilder.setNegativeButton("No",new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                AlertDialog alertDialog = deleteDialogBuilder.create();
                alertDialog.show();
            }

            //listener for when edit button is clicked
            @Override
            public void onEditBtnClicked(Recording recording) {

                //show a dialog with a edittext
                AlertDialog.Builder renameDialogBuilder = new AlertDialog.Builder(RecordingsActivity.this,R.style.DialogTheme);
                renameDialogBuilder.setTitle("Rename " + recording.getName());
                //add edittext
                EditText input = new EditText(RecordingsActivity.this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT);
                //set input to a single line
                input.setMaxLines(1);
                input.setLines(1);
                input.setSingleLine();
                //change edittext colour
                DrawableCompat.setTint(input.getBackground(), ContextCompat.getColor(RecordingsActivity.this, R.color.colorOn));

                input.setLayoutParams(lp);
                renameDialogBuilder.setView(input);

                //ok button
                renameDialogBuilder.setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface arg0, int arg1) {
                                String name = input.getText().toString();
                                //if there's no .wav extension, add one
                                if(name.length() > 4){
                                    String last4 = name.substring(name.length() - 4);
                                    if(!last4.equals(".wav")){
                                        name = name + ".wav";
                                    }
                                }
                                else{
                                    name = name + ".wav";
                                }
                                //store the old name
                                String oldName = recording.getName();

                                //edit list item
                                int i = adapter.editRecordingItem(recording.getId(),name);
                                //animate item change
                                if(i != -1) adapter.notifyItemChanged(i);
                                //editRecording actually edits the filename and database entry
                                editRecording(recording,oldName,name);
                            }
                        });
                //if the user presses cancel, just dismiss the dialog
                renameDialogBuilder.setNegativeButton("CANCEL",new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                AlertDialog alertDialog = renameDialogBuilder.create();
                alertDialog.show();

            }
            //if the list becomes empty, show a "No Notes" TextView
            @Override
            public void onListEmpty() {
                findViewById(R.id.no_rec_text).setVisibility(View.VISIBLE);
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
                //seek the mediaplayer only if the user was the one that changed the seekbar position
                if(b) {
                    if (mediaPlayer != null) mediaPlayer.seekTo(i);
                    //set the progress text
                    String progress = String.format(Locale.US, "%02d:%02d",
                            TimeUnit.MILLISECONDS.toMinutes(i),
                            TimeUnit.MILLISECONDS.toSeconds(i) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(i))
                    );
                    playbackProgressText.setText(progress);
                }
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
                if(playBackTimeThread!= null && playBackTimeThread.isAlive())playBackTimeThread.interrupt();
                seekBar.setProgress(seekBar.getMax());
                isPlaying = false;
                playPauseBtn.change(true);
                currentRecordingText.setText("");
                playbackProgressText.setText("");
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
                    Log.d(TAG, "onPrepared: prepared!");
                    //set mediaplayer progress
                    seekBar.setProgress(0);
                    seekBar.setMax(mediaPlayer.getDuration());
                    //set progress text
                    playbackProgressText.setText("00:00");
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
                    //format and set the progress
                    String currentProgress = String.format(Locale.US,"%02d:%02d",
                            TimeUnit.MILLISECONDS.toMinutes(playbackPausedPosition),
                            TimeUnit.MILLISECONDS.toSeconds(playbackPausedPosition) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(playbackPausedPosition))
                    );
                    playbackProgressText.setText(currentProgress);

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

                SQLiteDatabase db = recordingDBHelper.getWritableDatabase();
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
                        //textview to show if there are no recordings
                        TextView noRecText = findViewById(R.id.no_rec_text);
                        if(recordingArrayList.size() > 0) {
                            adapter = new RecordingListAdapter(recordingArrayList, RecordingsActivity.this, recordingListListener);
                            recordingsList.setAdapter(adapter);
                            noRecText.setVisibility(View.GONE);
                        }
                        else{
                            noRecText.setVisibility(View.VISIBLE);
                        }
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
                    db.execSQL(deleteQuery);

                }
                //close the db
                db.close();
            }
        });

         getRecordingsThread.start();
    }

    //delete a recording file
    //the database entry will be removed the next time the list is loaded
    private void deleteRecording(Recording r){
        //if the file is being played, stop playback
        if(currentPlayingRecording!= null && currentPlayingRecording.getId() == r.getId()){
            if(mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.reset();
        }
        //delete file
        File f = new File(getVoiceNotesDir().getAbsolutePath() + "/" + r.getName());
        if(f != null && f.exists()) {
            boolean b = f.delete();
            Log.d(TAG, "deleteRecording: Delete successful: " + b);
        }
    }

    //edit the recording wav file, and edit the database entry
    private void editRecording(Recording r,String oldName,String newName){

        Thread renameThread = new Thread(new Runnable() {
            @Override
            public void run() {

                boolean renameSuccess = false;

                //rename the file
                File f = new File(getVoiceNotesDir().getAbsolutePath() + "/" + oldName);
                if(f != null && f.exists()){
                    File f2 = new File(getVoiceNotesDir().getAbsolutePath() + "/" + newName);
                    renameSuccess = f.renameTo(f2);
                }
                else{
                    Log.d(TAG, "run: editRecording: FILE DOESNT EXIST: " + f.getAbsolutePath() );
                }

                //edit the database entry only if the file rename was successful
                // (otherwise it will get deleted while loading the recordings list)
                if(renameSuccess){
                    Log.d(TAG, "run: rename success!");
                    SQLiteDatabase db = recordingDBHelper.getWritableDatabase();

                    String query = "UPDATE " + RecordingDBHelper.RecordingDBSchema.TABLE_NAME + " SET name=\"" + newName + "\" WHERE _id=" + r.getId();

                    db.execSQL(query);
                    db.close();

                }

                else {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(),"Rename failed", Toast.LENGTH_LONG).show();
                        }
                    });
                }

            }
        });

        renameThread.start();
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
                            int progress = 0;
                            if(mediaPlayer != null) progress = mediaPlayer.getCurrentPosition();
                            //set seekbar
                            seekBar.setProgress(progress);
                            //set progress text
                            String progressText = String.format(Locale.US,"%02d:%02d",
                                    TimeUnit.MILLISECONDS.toMinutes(progress),
                                    TimeUnit.MILLISECONDS.toSeconds(progress) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(progress))
                            );
                            playbackProgressText.setText(progressText);
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
