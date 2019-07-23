package com.example.voicerecorder;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    //log tag
    private static final String TAG = "Notes";
    //request code for requesting permissions
    private static final int REQ_PERMS = 2000;
    //notification and channel id for showing notifications during recording
    private static final String CHANNEL_ID = "VOICE_NOTES";
    private static final int notificationId = 2500;

    private boolean isRecording = false;
    //to check whether app has all necessary permissions, and storage is accessible
    private boolean readyToRecord = false;
    //to check whether the MediaRecorder object is in the correct state and ready to record
    private boolean isRecorderReady = false;

    private MediaRecorder recorder;

    private FloatingActionButton newRecordingBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //initialise media recorder
        recorder = new MediaRecorder();

        // initialise fab and disable it until the app is ready to record
        newRecordingBtn = findViewById(R.id.new_recording_fab);
        enableNewRecordingBtn(false);
        //create a notification channel for Android O and above
        createNotificationChannel();

        //if the app has all needed permissions, enable the FAB and setup the recorder, else request permissions
        if(!hasAllPermissions()) requestPermissions();

        else{

            if(isExternalStorageWritable()){
                setupRecorder();
                isRecorderReady = true;

                enableNewRecordingBtn(true);
                readyToRecord = true;
            }
        }


        newRecordingBtn.setOnClickListener(view -> {
            if(readyToRecord) {
                //change UI of FAB
                isRecording = !isRecording;
                if (isRecording) newRecordingBtn.setImageDrawable(getDrawable(R.drawable.ic_mic_off_white_40dp));
                else newRecordingBtn.setImageDrawable(getDrawable(R.drawable.ic_mic_white_40dp));

                if(isRecording){
                    //create name of note file based on current time
                    Calendar cal = Calendar.getInstance();
                    String time = cal.get(Calendar.MONTH) + "-" + cal.get(Calendar.DAY_OF_MONTH) + ":" + cal.get(Calendar.HOUR_OF_DAY) + "-" + cal.get(Calendar.MINUTE) + "-" + cal.get(Calendar.SECOND);
                    String filename = "Note " + time + ".3gp";

                    if(isExternalStorageWritable()){

                        //if the voice notes directory doesn't exist, create it
                        if(!voiceNotesDirExists()) {
                            createVoiceNotesDir();
                            Log.d(TAG, "onCreate: Notes Dir created");
                        }
                        else Log.d(TAG, "onCreate: Notes Dir exists");

                        //complete path of voice note file
                        String recordingPath = getVoiceNotesDir().getAbsolutePath() + "/" + filename;
                        Log.d(TAG, "onCreate: Recording path: " + recordingPath);

                        //start recording audio and show a toast
                        try {
                            //check if the recorder is in the correct state to record
                            if(!isRecorderReady) setupRecorder();
                            //start recording
                            recorder.setOutputFile(recordingPath);
                            recorder.prepare();
                            recorder.start();
                            Toast.makeText(getApplicationContext(),"Recording Started",Toast.LENGTH_SHORT).show();
                            //show a notification for the
                            showNotification(filename);

                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(getApplicationContext(),"Error while trying to record audio",Toast.LENGTH_LONG).show();
                        }

                    }

                }
                //stop audio recording and show a toast
                else{
                    //stopRecording(false);
                    recorder.stop();
                    recorder.reset();
                    isRecorderReady = false;
                    //make a toast, and remove the notification
                    Toast.makeText(getApplicationContext(),"Recording Stopped",Toast.LENGTH_SHORT).show();
                    NotificationManagerCompat.from(this).cancel(notificationId);
                }
            }
        });

    }

    //set the recorder source, output format etc so its in the correct state to record
    private void setupRecorder(){
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
    }

    //show a persistent notification when recording is going on
    private void showNotification(String recordingName){
        // Create an empty intent so tapping the notification does nothing
        Intent emptyIntent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, emptyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(recordingName)
                .setContentText("Recording Note")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setOngoing(true)                           //so it can't be dismissed while recording
                .setAutoCancel(false);                      //so it can't be dismissed by clicking on it

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        notificationManager.notify(notificationId, builder.build());


    }

    //stop the audio recording
    private void stopRecording(boolean updateIsRecording){
        //if the user presses on the stop button in the notification, then isRecording hasn't been set to false yet, so set it to false here
        if(updateIsRecording){
            isRecording = false;
        }

        if(!isRecording && recorder != null) {
            recorder.stop();
            recorder.release();
        }

        Toast.makeText(getApplicationContext(),"Recording Stopped",Toast.LENGTH_SHORT).show();
        NotificationManagerCompat.from(this).cancel(notificationId);
    }

    //enable/disable FAB and change its color and icon
    private void enableNewRecordingBtn(boolean enable){
        newRecordingBtn.setEnabled(enable);
        if(!enable) {
            newRecordingBtn.setImageDrawable(getDrawable(R.drawable.ic_mic_off_white_40dp));
            newRecordingBtn.setBackgroundTintList(ColorStateList.valueOf(Color.GRAY));
        }
        else{
            newRecordingBtn.setImageDrawable(getDrawable(R.drawable.ic_mic_white_40dp));
            newRecordingBtn.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorAccent)));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == REQ_PERMS) {
            //if the array is empty, there has been an error
            if (grantResults.length > 0) {
                //check if all permissions have been granted
                boolean allGranted = false;
                for (int result : grantResults) {
                    if (result == PackageManager.PERMISSION_GRANTED) allGranted = true;
                    else allGranted = false;
                }

                //if all permissions have not been granted, show a snackbar with a message and a button to let the user grant permissions
                if (!allGranted) {
                    enableNewRecordingBtn(false);
                    readyToRecord = false;
                    Snackbar.make(findViewById(R.id.frame_layout), "App needs Storage and Audio permissions to record audio", Snackbar.LENGTH_INDEFINITE).setAction("GRANT", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            requestPermissions();
                        }
                    }).show();
                }

                //if all permissions have been granted, check if the storage is writable. If it is, enable the new recording button and setup the recorder
                else {
                    if (isExternalStorageWritable()) {
                        setupRecorder();
                        isRecorderReady = true;

                        enableNewRecordingBtn(true);
                        readyToRecord = true;
                    }
                }
            }
            //if there was an error, show a toast
            else {
                Toast.makeText(getApplicationContext(), "Error while requesting permissions", Toast.LENGTH_LONG).show();
            }
        }
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    //check if app has all the necessary permissions
    private boolean hasAllPermissions(){
        if( ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED){
            return true;
        }
        else{
            return false;
        }
    }

    //request any permissions that have not been granted
    private void requestPermissions(){
        if(!hasAllPermissions()) {
            ArrayList<String> permissions = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
            if (!permissions.isEmpty()) {
                ActivityCompat.requestPermissions(this, permissions.toArray(new String[]{}), REQ_PERMS);
            }
        }
    }
    //check if voice notes directory exists
    private boolean voiceNotesDirExists(){
        File f = getVoiceNotesDir();
        //File f = new File(Environment.getExternalStorageDirectory().getPath() + "/Voice Notes");
        Log.d(TAG, "createVoiceNotesDir: " + f.getAbsolutePath());
        if(f != null) return f.exists() && f.isDirectory();
        return false;
    }
    //create voice notes directory
    private boolean createVoiceNotesDir(){
        File f = getVoiceNotesDir();
        //File f = new File(Environment.getExternalStorageDirectory().getPath() + "/Voice Notes");
        Log.d(TAG, "createVoiceNotesDir: " + f.getAbsolutePath());
        return f.mkdirs();
    }

    //get the directory where voice notes are stored
    private File getVoiceNotesDir(){
        return new File(getExternalFilesDir(null),"Voice Notes");
    }


    //create notification channel for Android O and above
    //this method is called everytime the app starts
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Voice Notes", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Persistent notification shown when note is being recorded");
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }


}
