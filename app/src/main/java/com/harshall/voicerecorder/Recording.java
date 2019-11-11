package com.harshall.voicerecorder;

//Java object to represent Recordings
public class Recording {

    private String name;
    private int durationSec;
    long id,timeMilis;
    private float sizeMB;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDurationSec() {
        return durationSec;
    }

    public void setDurationSec(int durationSec) {
        this.durationSec = durationSec;
    }

    public long getTimeMilis() {
        return timeMilis;
    }

    public void setTimeMilis(long timeMilis) {
        this.timeMilis = timeMilis;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public float getSizeMB() {
        return sizeMB;
    }

    public void setSizeMB(float sizeMB) {
        this.sizeMB = sizeMB;
    }
}
