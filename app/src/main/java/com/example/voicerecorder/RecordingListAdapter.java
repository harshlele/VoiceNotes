package com.example.voicerecorder;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.ListIterator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class RecordingListAdapter extends RecyclerView.Adapter<RecordingListAdapter.ViewHolder> {
    //list of recordings to be shown
    private ArrayList<Recording> recordingArrayList;
    private Context context;
    private RecordingListListener recordingListListener;

    RecordingListAdapter(ArrayList<Recording> recordingArrayList, Context context, RecordingListListener listener) {
        this.recordingArrayList = recordingArrayList;
        this.context = context;
        this.recordingListListener = listener;
    }

    public ArrayList<Recording> getRecordingArrayList() {
        return recordingArrayList;
    }

    public void setRecordingArrayList(ArrayList<Recording> recordingArrayList) {
        this.recordingArrayList = recordingArrayList;
    }

    //remove a single recording
    public int removeRecording(long id){
        ListIterator<Recording> iterator = recordingArrayList.listIterator();
        int index = -1;
        while(iterator.hasNext()){
            Recording r = iterator.next();
            if (r.getId() == id){
                index = recordingArrayList.indexOf(r);
                iterator.remove();
            }
        }

        if(recordingArrayList.size() <= 0) recordingListListener.onListEmpty();
        //return the position of the removed item
        return index;
    }
    //edit recording list item
    public int editRecordingItem(long id, String name){
        int index = -1;
        for (Recording r:recordingArrayList){
            if(r.getId() == id){
                r.setName(name);
                index = recordingArrayList.indexOf(r);
            }
        }
        //return the index
        return index;
    }

    @NonNull
    @Override
    public RecordingListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout recordingItem = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.recording_item,parent,false);
        ViewHolder v = new ViewHolder(recordingItem);
        return v;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        Recording r = recordingArrayList.get(position);
        holder.recordingNameText.setText(r.getName());

        //format and set recording time and date
        Date d = new Date(r.getTimeMilis());
        DateFormat format = new SimpleDateFormat("EEE, MMM d, ''yy",Locale.getDefault());
        String dateText = format.format(d);
        holder.recordingDateText.setText(dateText);

        //format and set recording duration
        String duration = String.format(Locale.US,"%02d:%02d",
                TimeUnit.SECONDS.toMinutes(r.getDurationSec()),
                r.getDurationSec() - TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(r.getDurationSec()))
        );
        holder.recordingDurText.setText(duration);

        //format and set recording size
        String sizeText = "";
        if(r.getSizeMB() < 1){
            sizeText = (int)(r.getSizeMB() * 1000) + " KB";
        }
        else if(r.getSizeMB() > 1000){
            sizeText = (int)r.getSizeMB() + " GB";
        }
        else{
            sizeText = (int)r.getSizeMB() + " MB";
        }
        holder.recordingSizeText.setText(sizeText);

        //use the interface to send the clicked Recording object to RecordingActivity
        holder.rootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recordingListListener.onClicked(r);
            }
        });

        //use the interface to send clicked object to RecordingActivity
        holder.recordingDeleteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recordingListListener.onDeleteBtnClicked(r);
            }
        });
        //use the interface to send clicked object to RecordingActivity
        holder.recordingEditBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recordingListListener.onEditBtnClicked(r);
            }
        });

    }

    @Override
    public int getItemCount() {
        return recordingArrayList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder{

        private TextView recordingNameText, recordingDateText,recordingDurText,recordingSizeText;
        private ImageView recordingEditBtn,recordingDeleteBtn;
        private LinearLayout rootView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            recordingNameText = itemView.findViewById(R.id.recording_name_text);
            recordingDateText = itemView.findViewById(R.id.recording_date_text);
            recordingDurText = itemView.findViewById(R.id.recording_dur_text);
            recordingSizeText = itemView.findViewById(R.id.recording_size_text);
            recordingEditBtn = itemView.findViewById(R.id.edit_name_btn);
            recordingDeleteBtn = itemView.findViewById(R.id.delete_btn);
            rootView = (LinearLayout) itemView;

            //set custom font
            Typeface nunitoReg = Typeface.createFromAsset(context.getAssets(), "NunitoSans-Regular.ttf");
            Typeface nunitoBold = Typeface.createFromAsset(context.getAssets(), "NunitoSans-Bold.ttf");
            recordingNameText.setTypeface(nunitoBold);
            recordingDateText.setTypeface(nunitoReg);
            recordingDurText.setTypeface(nunitoReg);
            recordingSizeText.setTypeface(nunitoReg);

        }
    }
}
