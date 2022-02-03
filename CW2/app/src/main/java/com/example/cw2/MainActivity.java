package com.example.cw2;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.SimpleCursorAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cw2.databinding.ActivityMainBinding;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    //Declare data binding object, current track name and service binder used to access mp3 player methods
    //View binding to be used to access xml elements
    ActivityMainBinding activityMainBinding;
    String trackName = "None";
    private MP3Service.MyBinder mp3Service = null;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Creates binding object and sets view
        activityMainBinding = ActivityMainBinding.inflate(LayoutInflater.from(this));
        setContentView(activityMainBinding.getRoot());
        //Set song playing to none
        activityMainBinding.songPlaying.setText(getString(R.string.song_playing) + trackName);

        //Import list of mp3 files from sdcard/music directory
        Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                null,
                MediaStore.Audio.Media.IS_MUSIC + "!= 0",
                null,
                null);
        activityMainBinding.musicList.setAdapter(new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_1,
                cursor,
                new String[]{MediaStore.Audio.Media.DATA},
                new int[]{android.R.id.text1}));
        activityMainBinding.musicList.setOnItemClickListener((myAdapter, myView, myItemInt, mylng) -> {
            Cursor c = (Cursor) activityMainBinding.musicList.getItemAtPosition(myItemInt);
            //Store directory of selected mp3 file as a string
            String uri = c.getString(c.getColumnIndex(MediaStore.Audio.Media.DATA));
            File file = new File(uri);
            //Extract the file name from the directory string
            trackName = file.getName();
            //Set the current song playing text to the new song selected
            activityMainBinding.songPlaying.setText(getString(R.string.song_playing) + trackName);
            //Use service binder object to run the load music function and pass in the directory string and the track name
            mp3Service.loadMusic(uri, trackName);
        });

        //Add button listener for when the pause button is pressed
        //If pressed use service binder object to run the pause music function
        activityMainBinding.pauseButton.setOnClickListener(v -> mp3Service.pauseMusic());
        //Add button listener for when the stop button is pressed
        //If pressed use service binder object to run the stop music function and reset all values back to default
        activityMainBinding.stopButton.setOnClickListener(v -> {
            mp3Service.stopMusic();
            trackName = "None";
            activityMainBinding.songPlaying.setText(getString(R.string.song_playing) + trackName);
            activityMainBinding.timer.setText(getString(R.string.song_timer));
            activityMainBinding.songProgress.setProgress(0);
            activityMainBinding.songProgress.setMax(0);
        });
        //Add button listener for when the play button is pressed
        //If pressed use service binder object to run the play music function
        activityMainBinding.resumeButton.setOnClickListener(v -> mp3Service.playMusic());
        //Link the main activity to the MP3 Service
        this.bindService(new Intent(this, MP3Service.class), serviceConnection,
                Context.BIND_AUTO_CREATE);
    }
    //Used to monitor the connection between a service and an application
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        //Once connected then create a new service binder object and link callback thread in service
        @Override
        public void onServiceConnected(ComponentName trackName, IBinder service) {
            mp3Service = (MP3Service.MyBinder) service;
            mp3Service.registerCallback(callback);

        }
        //If service broken unexpectedly close the service and set the object to null and unlink callback
        @Override
        public void onServiceDisconnected(ComponentName trackName) {
            mp3Service = null;
            assert false;
            mp3Service.unregisterCallback(callback);
        }
    };

    //Creates callback, forming thread in MP3Service, this thread will return the current track time and duration as long as a song is playing
    ICallback callback = new ICallback() {
        @SuppressLint("SetTextI18n")
        @Override
        public void trackProgress(int currentTime, int totalTime, boolean finished) {
            runOnUiThread(() -> {
                //If the song is finished reset text displayed on mp3 player
                if (finished) {
                    trackName = "None";
                    activityMainBinding.timer.setText(getString(R.string.song_timer));
                    activityMainBinding.songProgress.setProgress(0);
                    activityMainBinding.songProgress.setMax(0);
                } else {
                    //Else print current song name as well as the current song time with progress bar
                    @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("mm:ss");
                    activityMainBinding.timer.setText(sdf.format(new Date(currentTime)) + "-" + sdf.format(new Date(totalTime)));
                    activityMainBinding.songProgress.setMax(totalTime);
                    activityMainBinding.songProgress.setProgress(currentTime);
                }
                activityMainBinding.songPlaying.setText(getString(R.string.song_playing) + trackName);
            });
        }
    };

    //When phone exited then remove all notifications
    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        Log.d("g53mdp", "MainActivity onDestroy");
        mp3Service.getNotifications().cancelAll();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        Log.d("g53mdp", "MainActivity onPause");
        super.onPause();
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        Log.d("g53mdp", "MainActivity onResume");
        super.onResume();
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        Log.d("g53mdp", "MainActivity onStart");
        super.onStart();
    }

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        Log.d("g53mdp", "MainActivity onStop");
        mp3Service.getNotifications().cancelAll();
        super.onStop();
    }

    //Save the instance state in case an instance is destroyed when rotating the app etc
    @Override
    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        //Store all variables necessary
        savedInstanceState.putString("trackName", trackName);
    }

    //On restore of an instance retrieve all data stored and set all options back
    @SuppressLint("SetTextI18n")
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        //Retrieve data
        trackName = savedInstanceState.getString("trackName");
        //Set text again for visual elements
        activityMainBinding.songPlaying.setText(getString(R.string.song_playing) + trackName);
    }
}