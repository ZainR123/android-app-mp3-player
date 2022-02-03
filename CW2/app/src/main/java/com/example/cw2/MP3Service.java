package com.example.cw2;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteCallbackList;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MP3Service extends Service {

    //Declare objects and the notification ID
    public static MP3Service instance;
    protected ProgressThread thread;
    RemoteCallbackList<MyBinder> remoteCallbackList = new RemoteCallbackList<>();
    int notificationID = 1;
    private MP3Player music;
    private NotificationManager notifications;
    private NotificationCompat.Builder buildNotification;

    //Constructor
    public MP3Service() {
        instance = this;
    }

    //Instance getter used for inner static class to access binder functions
    public static MP3Service getInstance() {
        return instance;
    }

    //Getter for notification manager
    public NotificationManager getNotifications() {
        return notifications;
    }

    //Binder class allowing other applications to bind and create connections
    public class MyBinder extends Binder implements IInterface {

        ICallback callback;

        //Defines interface that specifies how a client can communicate with the service
        @Override
        public IBinder asBinder() {
            return this;
        }

        //Call load, stop, play and pause music functions from within binder
        void loadMusic(String uri, String trackName) {

            MP3Service.this.loadMusic(uri, trackName);
        }

        void stopMusic() {
            MP3Service.this.stopMusic();
        }

        void playMusic() {
            MP3Service.this.playMusic();
        }

        void pauseMusic() {
            MP3Service.this.pauseMusic();
        }

        NotificationManager getNotifications() {
            return MP3Service.this.getNotifications();
        }

        //Bind callback to application
        public void registerCallback(ICallback callback) {
            this.callback = callback;
            remoteCallbackList.register(MyBinder.this);
        }

        //Unbind callback to application
        public void unregisterCallback(ICallback callback) {
            remoteCallbackList.unregister(MyBinder.this);
        }
    }

    //Create thread for tracking track playing time
    protected class ProgressThread extends Thread implements Runnable {

        //Constructor to start thread
        public ProgressThread() {
            this.start();
        }

        //Thread running loop
        public void run() {
            //While song is playing and not complete
            while (music.getProgress() < music.getDuration()) {
                //Sleep to allow counts to update properly
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
                //Create SDF object which converts milliseconds into minutes and seconds
                @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("mm:ss");
                //Pass callbacks to main activity, updating song progress
                doCallbacks(music.getProgress(), music.getDuration(), false);
                //Update notification timer and progress bar with current stats
                buildNotification.setSubText(sdf.format(new Date(music.getProgress())) + "-" + sdf.format(new Date(music.getDuration())));
                buildNotification.setProgress(music.getDuration(), music.getProgress(), false);
                notifications.notify(notificationID, buildNotification.build());
            }
            //If the song is playing and is finished it's track length then create a final callback which resets the text views and variables
            //Call stop music to set the music state to STOPPED and clear any active threads
            if (music.getState() == MP3Player.MP3PlayerState.PLAYING) {
                doCallbacks(music.getProgress(), music.getDuration(), true);
                stopMusic();
            }
            //Clear any remaining notifications
            notifications.cancel(notificationID);
        }
    }

    //Send callbacks to main activity to all active callback requesters
    public void doCallbacks(int currentTime, int totalTime, boolean finished) {
        final int n = remoteCallbackList.beginBroadcast();
        for (int i = 0; i < n; i++) {
            remoteCallbackList.getBroadcastItem(i).callback.trackProgress(currentTime, totalTime, finished);
        }
        remoteCallbackList.finishBroadcast();
    }

    //Broadcast receiver to update notification button intent
    public static class NotificationReceiver extends BroadcastReceiver {

        //When a button is pressed get the intent and either: pause, play or stop music depending on intent
        @Override
        public void onReceive(Context context, Intent intent) {
            String selectedButton = intent.getAction();

            switch (selectedButton) {
                case "Pause":
                    MP3Service.getInstance().pauseMusic();
                    break;

                case "Start":
                    MP3Service.getInstance().playMusic();
                    break;

                case "Stop":
                    MP3Service.getInstance().stopMusic();
                    MP3Service.getInstance().doCallbacks(getInstance().music.getProgress(), getInstance().music.getDuration(), true);
                    break;
            }
        }
    }

    //Creates pending button intents for the notification bar
    public PendingIntent buttonIntents(String name) {
        Intent intent = new Intent(MP3Service.this, MP3Service.NotificationReceiver.class);
        intent.setAction(name);
        return PendingIntent.getBroadcast(MP3Service.this, 0, intent, 0);
    }

    //Initialise notifications
    private void createNotifications() {

        //Create new notification manager and cancel any notifications in the same ID slot
        notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notifications.cancel(notificationID);
        String CHANNEL_ID = "100";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "MP3Service";
            String description = "Music Player";
            //Create new notification channel with high importance so notification stays at the top of the list
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(description);
            //add channel to the notification manager
            notifications.createNotificationChannel(channel);
        }
        //Create new intent between main activity and this service
        Intent intent = new Intent(MP3Service.this, MainActivity.class);
        //Intent is to return to the main activity
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        //Create pending intent with selected intent
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        //Build notification, setting the notification to silent and making it top priority
        //Also adding so whenever the notification is pressed it will return to the main activity
        buildNotification = new NotificationCompat.Builder(this, CHANNEL_ID).setSilent(true).setWhen(0)
                .setPriority(NotificationCompat.PRIORITY_MAX).setContentIntent(pendingIntent);
    }

    //Loading music function which plays the songs and starts any processes which run alongside it
    public void loadMusic(String uri, String trackName) {

        //If the song is already playing then return
        if (music.getState() != MP3Player.MP3PlayerState.STOPPED && music.getFilePath().equals(uri)) {
            return;
        }

        //Stop any songs currently playing
        stopMusic();

        //Sleep to allow threads not to be overwhelmed by user calls
        try {
            Thread.sleep(150);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        //Call the music load function by passing the song path link
        music.load(uri);
        //Create new progress thread to start tracking music time
        thread = new ProgressThread();

        //Update notification with relevant song information and add buttons for pausing, playing and stopping
        //Add custom notification logo then notify the notification manager of the changes
        buildNotification.setContentTitle("Music Player")
                .setContentText(trackName)
                .setSmallIcon(R.drawable.outline_music_note_24)
                .addAction(R.drawable.outline_pause_circle_24, getString(R.string.pause_song), buttonIntents(getString(R.string.pause_song)))
                .addAction(R.drawable.outline_play_circle_24, getString(R.string.resume_song), buttonIntents(getString(R.string.resume_song)))
                .addAction(R.drawable.outline_stop_circle_24, getString(R.string.stop_song), buttonIntents(getString(R.string.stop_song)));
        notifications.notify(notificationID, buildNotification.build());
    }

    //Stop music function checks if music is already stopped or not
    //If not stopped then call stop music from the MP3Player class, set thread to null and cancel all notifications
    public void stopMusic() {
        if (music.getState() != MP3Player.MP3PlayerState.STOPPED) {
            music.stop();
            thread = null;
            notifications.cancel(notificationID);
        }
    }

    //If music is paused then call the play music function in MP3Player
    public void playMusic() {
        if (music.getState() == MP3Player.MP3PlayerState.PAUSED) {
            music.play();
        }
    }

    //If music is playing then call the pause music function in MP3Player
    public void pauseMusic() {
        if (music.getState() == MP3Player.MP3PlayerState.PLAYING) {
            music.pause();
        }
    }

    //On creation of the service instantiate the MP3Player class and create the notification manager and channel
    @Override
    public void onCreate() {
        // TODO Auto-generated method stub
        Log.d("g53mdp", "service onCreate");
        super.onCreate();
        music = new MP3Player();
        createNotifications();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // TODO Auto-generated method stub
        Log.d("g53mdp", "service onBind");
        return new MyBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // TODO Auto-generated method stub
        Log.d("g53mdp", "service onStartCommand");
        return Service.START_STICKY;
    }

    //When service ends then reset the threads and cancel all notifications open
    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        Log.d("g53mdp", "service onDestroy");
        thread = null;
        notifications.cancelAll();
        super.onDestroy();
    }

    @Override
    public void onRebind(Intent intent) {
        // TODO Auto-generated method stub
        Log.d("g53mdp", "service onRebind");
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // TODO Auto-generated method stub
        Log.d("g53mdp", "service onUnbind");
        return super.onUnbind(intent);
    }
}