package com.example.voicerecorder;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity {

    //log tag
    private static final String TAG = "Notes";
    //request code for requesting permissions
    private static final int REQ_PERMS = 2000;
    //notification and channel id for showing notifications during recording
    private static final String CHANNEL_ID = "VOICE_NOTES";
    private static final int notificationId = 2500;

    //to check if the app is currently recording
    private boolean isRecording = false;
    //to check whether app has all necessary permissions, and storage is accessible
    private boolean readyToRecord = false;
    //to check whether the MediaRecorder object is in the correct state and ready to record
    private boolean isRecorderReady = false;


    //calendar to hold the start time
    private Calendar recordingStartTime;
    //thread that will record the time passed since recording was started, and a handler to update the notification from the thread
    private Thread recordingTimeUpdateThread;
    private Handler recordingTimeHandler;
    //filename of the current ongoing recording
    private String currentRecordingName = "";
    //full path of current recording
    private String recordingPath;

    //private MediaRecorder recorder

    //AsyncTask to record audio
    private RecordWaveTask recordTask = null;

    private FloatingActionButton newRecordingBtn;

    //Broadcast reciever to recieve messages from the notification
    private BroadcastReceiver stopRecordingReciever;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //initialise media recorder
        //recorder = new MediaRecorder();

        //initialise handler
        recordingTimeHandler = new Handler();

        // initialise fab and disable it until the app is ready to record
        newRecordingBtn = findViewById(R.id.new_recording_fab);
        enableNewRecordingBtn(false);
        //create a notification channel for Android 8(O) and above
        createNotificationChannel();


        //if the app has all needed permissions, enable the FAB and setup the recorder, else request permissions
        if(!hasAllPermissions()) requestPermissions();

        else{

            if(isExternalStorageWritable()){
                //setupRecorder();
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
                    String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(cal.getTime());
                    String filename = "Note " + time + ".wav";

                    if(isExternalStorageWritable()){

                        //if the voice notes directory doesn't exist, create it
                        if(!voiceNotesDirExists()) {
                            createVoiceNotesDir();
                            Log.d(TAG, "onCreate: Notes Dir created");
                        }
                        else Log.d(TAG, "onCreate: Notes Dir exists");

                        //complete path of voice note file
                        recordingPath = getVoiceNotesDir().getAbsolutePath() + "/" + filename;
                        Log.d(TAG, "onCreate: Recording path: " + recordingPath);

                        //start recording audio and show a toast
                        try {
                            //check if the recorder is in the correct state to record
                            //if(!isRecorderReady) setupRecorder();

                            //start recording
                            //recorder.setOutputFile(recordingPath);
                            //recorder.prepare();
                            //recorder.start();

                            launchTask(recordingPath);

                            //record current time to calculate time passed since recording began
                            recordingStartTime = Calendar.getInstance();

                            //show a notification for the recording
                            showNotification(filename,"Recording Note");
                            currentRecordingName = filename;

                            //start a thread to record the time passed since recording began
                            startRecordingTimeThread();
                            //show a toast to notify the user
                            Toast.makeText(getApplicationContext(),"Recording Started",Toast.LENGTH_SHORT).show();

                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(getApplicationContext(),"Error while trying to record audio",Toast.LENGTH_LONG).show();
                        }

                    }

                }
                //stop audio recording and show a toast
                else{
                    stopRecording();

                }
            }
        });

        //setup and register the broadcast reciever to get messages from the notification
        stopRecordingReciever = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getBooleanExtra("recording_stop",false) == true) {
                    Log.d(TAG, "onReceive: got the broadcast");
                    isRecording = false;
                    newRecordingBtn.setImageDrawable(getDrawable(R.drawable.ic_mic_white_40dp));
                    stopRecording();
                }
            }
        };


        IntentFilter filter = new IntentFilter();
        filter.addAction(String.valueOf(notificationId));

        registerReceiver(stopRecordingReciever,filter);
    }


    //start the asynctask for recording audio
    private void launchTask(String wavFile) {
        File f = new File(wavFile);
        recordTask = new RecordWaveTask(this);
        recordTask.execute(f);
    }


    //set the recorder source, output format etc so its in the correct state to record
    /*
    private void setupRecorder(){
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
            isRecorderReady = true;
    }
    */

    //show a persistent notification when recording is going on
    private void showNotification(String recordingName,String recordingText){
        // Create an empty intent so tapping the notification does nothing
        Intent emptyIntent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, emptyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        //create intent for the stop button
        Intent stopRecordingIntent = new Intent(String.valueOf(notificationId));
        stopRecordingIntent.putExtra("recording_stop",true);
        PendingIntent stopRecordingPendingIntent =
                PendingIntent.getBroadcast(this, new Random().nextInt(Integer.MAX_VALUE), stopRecordingIntent, PendingIntent.FLAG_CANCEL_CURRENT);


        NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(recordingName)
                .setContentText(recordingText)
                .setPriority(NotificationCompat.PRIORITY_LOW) //low priority so the duration updates don't annoy the user
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)                       //alert the user only when the notification first appears, not every time it updates
                .setOngoing(true)                            //so it can't be dismissed while recording
                .setAutoCancel(false)                       //so it can't be dismissed by clicking on it
                .addAction(R.drawable.ic_mic_off_white_40dp,"STOP",stopRecordingPendingIntent);  //add stop button and pending intent

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        notificationManager.notify(notificationId, notifBuilder.build());


    }

    //stop the audio recording
    private void stopRecording(){
        //interrupt the time recording thread
        recordingTimeUpdateThread.interrupt();

        //recorder.stop();
        //recorder.reset();
        
        //stop the audio recording AsyncTask
        if (!recordTask.isCancelled() && recordTask.getStatus() == AsyncTask.Status.RUNNING) {
            recordTask.cancel(false);
        } else {
            Log.d(TAG, "stopRecording: TASK NOT RUNNING!");
        }

        currentRecordingName = "";

        //make a toast, and remove the notification
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
                        //setupRecorder();
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
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
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


    //create notification channel for Android 8 and above
    //this method is called everytime the app starts
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Voice Notes", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Persistent notification shown when note is being recorded");
            // Register the channel with the system
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    //create and start a thread to record and display the time passed since recording began
    private void startRecordingTimeThread(){
        recordingTimeUpdateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                //isRecording has to be checked because the thread doesnt stop even if .interrupt() is called >.<
                while(!Thread.currentThread().isInterrupted() && isRecording){
                    Log.d(TAG, "run: thread running");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) { e.printStackTrace();}

                    if(isRecording) {
                        //get the milliseconds lapsed since recording began
                        Calendar now = Calendar.getInstance();
                        long time = now.getTimeInMillis() - recordingStartTime.getTimeInMillis();
                        //convert it into mins and secs
                        String duration = String.format(Locale.US,"%02d:%02d",
                                TimeUnit.MILLISECONDS.toMinutes(time),
                                TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time))
                                );
                        //use a handler to update the notification
                        recordingTimeHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                showNotification(currentRecordingName, duration);
                            }
                        });
                    }
                }
            }
        });
        //start the thread
        recordingTimeUpdateThread.start();


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(stopRecordingReciever);
    }

}
