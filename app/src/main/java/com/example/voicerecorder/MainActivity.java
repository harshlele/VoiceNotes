package com.example.voicerecorder;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private FloatingActionButton newRecordingBtn;

    private static final String TAG = "recorder";

    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        newRecordingBtn = findViewById(R.id.new_recording_fab);
        newRecordingBtn.setOnClickListener(view -> {
            isRecording = !isRecording;
            if(isRecording) newRecordingBtn.setImageDrawable(getDrawable(R.drawable.ic_mic_off_white_40dp));
            else newRecordingBtn.setImageDrawable(getDrawable(R.drawable.ic_mic_white_40dp));
        });

    }
}
