package com.example.voicerecorder;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.ohoussein.playpause.PlayPauseView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
    //to check whether a recording is being played
    private boolean isPlayingRecording = false;
    //to check if the player has been started with a new file, or if its being resumed from a pause
    private boolean isPlayerResuming = false;


    //calendar to hold the start time
    private Calendar recordingStartTime;
    //thread that will record the time passed since recording was started, and a handler to update the notification from the thread
    private Thread recordingTimeUpdateThread;
    private Handler recordingTimeHandler;
    //filename of the current ongoing recording
    private String currentRecordingName = "";
    //full path of current recording
    private String recordingPath;


    //AsyncTask to record audio
    private RecordWaveTask recordTask = null;
    //to play recordings
    private MediaPlayer mediaPlayer;

    //recording button
    private ImageView newRecordingBtn;
    //text views for name and duration
    private TextView recordingNameText, recordingTimeText;
    //root layout
    private RelativeLayout rootLayout;
    //play/pause button
    private PlayPauseView playPauseBtn;

    //Broadcast reciever to recieve messages from the notification
    private BroadcastReceiver stopRecordingReciever;

    enum ANIMATION_ORIGIN{ANIMATION_ORIGIN_RECORD_BTN, ANIMATION_ORIGIN_PLAY_BTN};

    //global listener because they are defined in onCreate, but set in onResume
    private MediaPlayer.OnPreparedListener onPreparedListener;
    private MediaPlayer.OnErrorListener onErrorListener;
    private MediaPlayer.OnCompletionListener onCompletionListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //initialise handler
        recordingTimeHandler = new Handler();


        //initialise views
        newRecordingBtn = findViewById(R.id.new_recording_btn);
        enableNewRecordingBtn(false);                                // initialise record button and disable it until the app is ready to record
        recordingNameText = findViewById(R.id.recording_name);
        recordingTimeText = findViewById(R.id.recording_time);
        playPauseBtn = findViewById(R.id.play_pause_btn);
        playPauseBtn.setVisibility(View.GONE);

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
                //change UI of recording button
                isRecording = !isRecording;
                if (isRecording) newRecordingBtn.setImageDrawable(getDrawable(R.drawable.ic_mic_off_white_128dp));
                else newRecordingBtn.setImageDrawable(getDrawable(R.drawable.ic_mic_128dp));

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
                            //launchTask starts AsyncTask which starts recording
                            launchTask(recordingPath);

                            //record current time to calculate time passed since recording began
                            recordingStartTime = Calendar.getInstance();

                            //show a notification for the recording
                            showNotification(filename,"Recording Note");
                            currentRecordingName = filename;

                            //start a thread to record the time passed since recording began
                            startRecordingTimeThread();

                            //set the recording name
                            recordingNameText.setText(filename);
                            //set the recording name and text colors
                            recordingNameText.setTextColor(getResources().getColor(R.color.colorOff));
                            recordingTimeText.setTextColor(getResources().getColor(R.color.colorOff));
                            //hide the play/pause button
                            playPauseBtn.setVisibility(View.GONE);

                            //animate the activity background to a new color
                            animateActivityBackground(R.color.colorOff,R.color.colorOn,ANIMATION_ORIGIN.ANIMATION_ORIGIN_RECORD_BTN);
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
                    //this method stops the AsyncTask that's recording audio
                    stopRecording();

                    //animate the activity background to a new color
                    animateActivityBackground(R.color.colorOn,R.color.colorOff,ANIMATION_ORIGIN.ANIMATION_ORIGIN_RECORD_BTN);
                    //set the recording name and text colors
                    recordingNameText.setTextColor(getResources().getColor(R.color.colorOn));
                    recordingTimeText.setTextColor(getResources().getColor(R.color.colorOn));
                    //show the play/pause button
                    if(!currentRecordingName.equals(null) && !currentRecordingName.equals("")){
                        playPauseBtn.setVisibility(View.VISIBLE);
                    }

                }
            }
        });

        //click listener for play/pause button
        playPauseBtn.setOnClickListener(view -> {
            //toggle button
            playPauseBtn.toggle();
            isPlayingRecording = !isPlayingRecording;
            //animate background and change text view colours depending on whether recording is being played.
            if(isPlayingRecording){
                //hide the recording button
                newRecordingBtn.setVisibility(View.GONE);
                enableNewRecordingBtn(false);
                recordingNameText.setTextColor(ContextCompat.getColor(MainActivity.this,R.color.colorOff));
                recordingTimeText.setTextColor(ContextCompat.getColor(MainActivity.this,R.color.colorOff));
                animateActivityBackground(R.color.colorOff,R.color.colorOn,ANIMATION_ORIGIN.ANIMATION_ORIGIN_PLAY_BTN);
                //if the player has started new recording, set the source and prepare it
                if(!isPlayerResuming) {
                    try {

                        FileInputStream inputStream = new FileInputStream(recordingPath);
                        mediaPlayer.setDataSource(inputStream.getFD());
                        mediaPlayer.prepareAsync();
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(getApplicationContext(),"Error on playback",Toast.LENGTH_LONG).show();
                    }
                    isPlayerResuming = false;

                }
                //if its resuming from paused playback, just start it
                else {
                    mediaPlayer.start();
                }
            }
            else{
                //make the recording button visible again
                newRecordingBtn.setVisibility(View.VISIBLE);
                enableNewRecordingBtn(true);
                recordingNameText.setTextColor(ContextCompat.getColor(MainActivity.this,R.color.colorOn));
                recordingTimeText.setTextColor(ContextCompat.getColor(MainActivity.this,R.color.colorOn));
                animateActivityBackground(R.color.colorOn,R.color.colorOff,ANIMATION_ORIGIN.ANIMATION_ORIGIN_PLAY_BTN);
                //pause playback
                if(mediaPlayer.isPlaying()){
                    mediaPlayer.pause();
                    isPlayerResuming = true;
                }

            }

        });

        //setup and register the broadcast reciever to get messages from the notification
        stopRecordingReciever = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getBooleanExtra("recording_stop",false) == true) {
                    isRecording = false;
                    stopRecording();
                    //animate the activity background to a new color
                    animateActivityBackground(R.color.colorOn,R.color.colorOff,ANIMATION_ORIGIN.ANIMATION_ORIGIN_RECORD_BTN);
                    //set the recording name and text colors
                    recordingNameText.setTextColor(getResources().getColor(R.color.colorOn));
                    recordingTimeText.setTextColor(getResources().getColor(R.color.colorOn));
                    //set the new recording button back to on
                    enableNewRecordingBtn(true);
                    
                    //show the play/pause button
                    if(!currentRecordingName.equals(null) && !currentRecordingName.equals("")){
                        playPauseBtn.setVisibility(View.VISIBLE);
                    }


                }
            }
        };


        IntentFilter filter = new IntentFilter();
        filter.addAction(String.valueOf(notificationId));

        registerReceiver(stopRecordingReciever,filter);

        //define listeners
        onPreparedListener = new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mediaPlayer.start();
            }
        };

        onErrorListener = new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                Toast.makeText(getApplicationContext(),"MediaPlayer Error",Toast.LENGTH_LONG).show();
                return true;
            }
        };

        onCompletionListener = new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                //set the UI back to non-playing state
                newRecordingBtn.setVisibility(View.VISIBLE);
                enableNewRecordingBtn(true);
                playPauseBtn.change(true);
                isPlayingRecording = false;
                isPlayerResuming = false;
                //reset the recorder
                mediaPlayer.reset();
                recordingNameText.setTextColor(ContextCompat.getColor(MainActivity.this,R.color.colorOn));
                recordingTimeText.setTextColor(ContextCompat.getColor(MainActivity.this,R.color.colorOn));
                animateActivityBackground(R.color.colorOn,R.color.colorOff,ANIMATION_ORIGIN.ANIMATION_ORIGIN_PLAY_BTN);
            }
        };

    }


    //start the asynctask for recording audio
    private void launchTask(String wavFile) {
        File f = new File(wavFile);
        recordTask = new RecordWaveTask(this);
        recordTask.execute(f);
    }


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
                .setPriority(NotificationCompat.PRIORITY_MIN) //minimum priority so the duration updates don't annoy the user
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)                       //alert the user only when the notification first appears, not every time it updates
                .setOngoing(true)                            //so it can't be dismissed while recording
                .setAutoCancel(false)                       //so it can't be dismissed by clicking on it
                .addAction(R.drawable.ic_mic_off_white_40dp,"STOP",stopRecordingPendingIntent);  //add stop button and pending intent

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        notificationManager.notify(notificationId, notifBuilder.build());


    }

    //stop the audio recording
    private void stopRecording() {


        //interrupt the time recording thread
        recordingTimeUpdateThread.interrupt();

        //stop the audio recording AsyncTask
        if (!recordTask.isCancelled() && recordTask.getStatus() == AsyncTask.Status.RUNNING) {
            recordTask.cancel(false);
        } else {
            Log.d(TAG, "stopRecording: TASK NOT RUNNING!");
        }

        //currentRecordingName = "";

        //make a toast, and remove the notification
        Toast.makeText(getApplicationContext(),"Recording Stopped",Toast.LENGTH_SHORT).show();
        NotificationManagerCompat.from(this).cancel(notificationId);


    }

    //enable/disable recording button and change its color and icon
    private void enableNewRecordingBtn(boolean enable){
        newRecordingBtn.setEnabled(enable);
        if(!enable) {
            newRecordingBtn.setImageDrawable(getDrawable(R.drawable.ic_mic_off_white_128dp));
        }
        else{
            newRecordingBtn.setImageDrawable(getDrawable(R.drawable.ic_mic_128dp));
        }
    }

    //run an animation where the activity background changes colour in a circle
    private void animateActivityBackground(int startColor, int endColor,ANIMATION_ORIGIN origin){


        if(rootLayout == null) rootLayout = findViewById(R.id.root_layout);
        //calculate the final radius of the circle
        int finalRadius = (int)Math.hypot(rootLayout.getWidth(),rootLayout.getHeight());

        int centerX,centerY;
        //calculate center co-ordinates (from where the circle will begin drawing) based on ANIMATION_ORIGIN
        if(origin == ANIMATION_ORIGIN.ANIMATION_ORIGIN_PLAY_BTN){
            int[] coords = new int[2];
            playPauseBtn.getLocationOnScreen(coords);
            centerX = coords[0] + playPauseBtn.getWidth()/2;
            centerY = coords[1] + playPauseBtn.getHeight()/2;
        }
        else if(origin == ANIMATION_ORIGIN.ANIMATION_ORIGIN_RECORD_BTN){
            int[] coords = new int[2];
            newRecordingBtn.getLocationOnScreen(coords);
            centerX = coords[0] + newRecordingBtn.getWidth()/2;
            centerY = coords[1] + newRecordingBtn.getHeight()/2;
        }
        else{
            centerX = rootLayout.getWidth()/2;
            centerY = rootLayout.getHeight()/2;
        }

        //create bitmap outside the ValueAnimator so we can use the same Bitmap every time
        Bitmap b = Bitmap.createBitmap(rootLayout.getWidth(),rootLayout.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        //initially, draw the start colour over the whole canvas
        c.drawColor(ContextCompat.getColor(MainActivity.this,startColor));
        p.setColor(ContextCompat.getColor(MainActivity.this,endColor));

        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        valueAnimator.setDuration(1000);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                //calculate current radius, then draw a circle at the center that's of the current radius
                //over time, the cirle becomes bigger and bigger until it occupies the whole screen,
                // and the whole background colour has been changed
                float currentRadius = ((float) (valueAnimator.getAnimatedValue())) * finalRadius;
                c.drawCircle(centerX,centerY,currentRadius,p);
                rootLayout.setBackground(new BitmapDrawable(getResources(),b));
            }
        });
        valueAnimator.start();
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
                    Snackbar.make(findViewById(R.id.root_layout), "App needs Storage and Audio permissions to record audio", Snackbar.LENGTH_INDEFINITE).setAction("GRANT", new View.OnClickListener() {
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
                                recordingTimeText.setText(duration);
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
    protected void onResume() {
        super.onResume();
        //initialise mediaplayer in onResume because it has to be re-initialised every time the user comes back to the activity
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());

        mediaPlayer.setOnPreparedListener(onPreparedListener);
        mediaPlayer.setOnCompletionListener(onCompletionListener);
        mediaPlayer.setOnErrorListener(onErrorListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        //release and nullify the mediaplayer so other apps can use it
        mediaPlayer.release();
        mediaPlayer = null;
    }

    //unregister the receiver in onDestroy instead of onStop so the notification works even when app is in the background
    @Override
    protected void onDestroy() {
        super.onDestroy();
        NotificationManagerCompat.from(this).cancel(notificationId);
        unregisterReceiver(stopRecordingReciever);
    }

}
