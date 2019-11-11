package com.example.voicerecorder;

//interface used when recording is clicked in RecordingsActivity
public interface RecordingListListener {
    void onClicked(Recording recording);
    void onDeleteBtnClicked(Recording recording);
    void onEditBtnClicked(Recording recording);
    void onListEmpty();

}
