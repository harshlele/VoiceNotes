package com.example.voicerecorder;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class RecordingListAdapter extends RecyclerView.Adapter<RecordingListAdapter.ViewHolder> {
    //list of recordings to be shown
    private ArrayList<Recording> recordingArrayList;
    private Context context;

    public RecordingListAdapter(ArrayList<Recording> recordingArrayList, Context context) {
        this.recordingArrayList = recordingArrayList;
        this.context = context;
    }

    public ArrayList<Recording> getRecordingArrayList() {
        return recordingArrayList;
    }

    public void setRecordingArrayList(ArrayList<Recording> recordingArrayList) {
        this.recordingArrayList = recordingArrayList;
    }

    @NonNull
    @Override
    public RecordingListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RelativeLayout recordingItem = (RelativeLayout) LayoutInflater.from(context).inflate(R.layout.recording_item,parent,false);
        ViewHolder v = new ViewHolder(recordingItem);
        return v;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        Recording r = recordingArrayList.get(position);
        //TODO: format date and size
        holder.recordingNameText.setText(r.getName());
        holder.recordingDateText.setText(String.valueOf(r.getTimeMilis()));
        holder.recordingDurText.setText(r.getDurationSec() + " sec");
        holder.recordingSizeText.setText(r.getSizeMB() + " MB");

    }


    @Override
    public int getItemCount() {
        return recordingArrayList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder{

        private TextView recordingNameText, recordingDateText,recordingDurText,recordingSizeText;
        private ImageView recordingEditBtn,recordingDeleteBtn;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            recordingNameText = itemView.findViewById(R.id.recording_name_text);
            recordingDateText = itemView.findViewById(R.id.recording_date_text);
            recordingDurText = itemView.findViewById(R.id.recording_dur_text);
            recordingSizeText = itemView.findViewById(R.id.recording_size_text);
            recordingEditBtn = itemView.findViewById(R.id.edit_name_btn);
            recordingDeleteBtn = itemView.findViewById(R.id.delete_btn);
        }
    }
}
