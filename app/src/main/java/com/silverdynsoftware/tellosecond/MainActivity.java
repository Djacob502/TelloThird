package com.silverdynsoftware.tellosecond;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.silverdynsoftware.tellosecond.greenRobot.eventsGreenRobot;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import nl.bravobit.ffmpeg.ExecuteBinaryResponseHandler;
import nl.bravobit.ffmpeg.FFmpeg;
import nl.bravobit.ffmpeg.FFtask;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;


public class MainActivity extends AppCompatActivity implements IVLCVout.Callback   {

    TextView tvMessage;
    TextView tvStatus;
    TextView tvVideo;

    private DatagramSocket socketMainSending;
    private InetAddress inetAddressMainSending;
    public static final int portMainSending = 8889;
    public static final String addressMainSending = "192.168.10.1";

    DatagramSocket socketStatusServer;
    DatagramSocket socketStreamOnServer;

    // Status Receiver
    StatusDatagramReceiver statusDatagramReceiver;
    StreamOnDatagramReceiver streamOnDatagramReceiver;

    FFtask fftask;

    // VLC Variables
    //private static final String SAMPLE_URL = "https://archive.org/download/Popeye_forPresident/Popeye_forPresident_512kb.mp4";
    private static final String SAMPLE_URL = "udp://@0.0.0.0:11111";


    private static final int SURFACE_BEST_FIT = 0;
    private static final int SURFACE_FIT_HORIZONTAL = 1;
    private static final int SURFACE_FIT_VERTICAL = 2;
    private static final int SURFACE_FILL = 3;
    private static final int SURFACE_16_9 = 4;
    private static final int SURFACE_4_3 = 5;
    private static final int SURFACE_ORIGINAL = 6;
    private static int CURRENT_SIZE = SURFACE_BEST_FIT;

    private FrameLayout mVideoSurfaceFrame = null;
    //    private FrameLayout mVideoSurfaceFrame1 = null;
    private SurfaceView mVideoSurface = null;
//    private SurfaceView mVideoSurface1 = null;

    private final Handler mHandler = new Handler();

    private LibVLC mLibVLC = null;
    private MediaPlayer mMediaPlayer = null;
    //    private MediaPlayer mMediaPlayer2 = null;
    private int mVideoHeight = 0;
    private int mVideoWidth = 0;
    private int mVideoVisibleHeight = 0;
    private int mVideoVisibleWidth = 0;
    private int mVideoSarNum = 0;
    private int mVideoSarDen = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvMessage = findViewById(R.id.tvMessage);
        tvStatus = findViewById(R.id.tvStatus);
        tvVideo = findViewById(R.id.tvVideo);

        // So we can get the
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // VLC
        final ArrayList<String> args = new ArrayList<>();
        //args.add("-vvv");
        //args.add("--network");
        //args.add("-caching 0");
        //args.add("--demux");
        mLibVLC = new LibVLC(this, args);
        mMediaPlayer = new MediaPlayer(mLibVLC);
//        mMediaPlayer2 = new MediaPlayer(mLibVLC);

        mVideoSurfaceFrame = (FrameLayout) findViewById(R.id.video_surface_frame);
//        mVideoSurfaceFrame1 = (FrameLayout) findViewById(R.id.video_surface_frame1);
        mVideoSurface = (SurfaceView) findViewById(R.id.video_surface);
//        mVideoSurface1 = (SurfaceView) findViewById(R.id.video_surface1);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMediaPlayer.release();
        mLibVLC.release();
    }




    public void on_click_btnInitialize(View v) {
        try {
            socketMainSending = new DatagramSocket();
            inetAddressMainSending = getInetAddressByName(addressMainSending); // InetAddress.getByName(addressMainSending);
            if (inetAddressMainSending == null) {
                tvMessage.setText("inet Address Main Sending is null");
            } else {
                tvMessage.setText("Initialize without error");
            }
            socketStatusServer = new DatagramSocket(null);
            InetSocketAddress addressStatus = new InetSocketAddress("0.0.0.0", 8890);
            socketStatusServer.bind(addressStatus);
            socketStreamOnServer = new DatagramSocket(null);
            //InetSocketAddress addressStreamOn = new InetSocketAddress("0.0.0.0", 11111);
            //socketStreamOnServer.bind(addressStreamOn);
        } catch (IOException e) {
            tvMessage.setText("Error on initialize: " + e.getMessage());
        }
    }

    public void on_click_btnSendCommand(View v) {
        SendOneCommand sendOneCommand = new SendOneCommand();
        sendOneCommand.doInBackground("command");
    }

    public void on_click_btnGetBattery(View v) {
        SendOneCommand sendOneCommand = new SendOneCommand();
        sendOneCommand.doInBackground("battery?");
    }

    public void on_click_btnGetTemp(View v) {
        SendOneCommand sendOneCommand = new SendOneCommand();
        sendOneCommand.doInBackground("temp?");
    }

    public void on_click_btnStartStatus(View v) {
        SendOneCommand sendOneCommand = new SendOneCommand();
        sendOneCommand.doInBackground("command");
        statusDatagramReceiver = new StatusDatagramReceiver();
        statusDatagramReceiver.start();
    }

    public void on_click_btnEndStatus(View v) {
        statusDatagramReceiver.kill();
    }

    public void on_click_btnStartVideo(View v) {
        SendOneCommand sendOneCommand = new SendOneCommand();
        sendOneCommand.doInBackground("streamon");
        //streamOnDatagramReceiver = new StreamOnDatagramReceiver();
        //streamOnDatagramReceiver.start();
    }

    public void on_click_btnStopVideo(View v) {
        SendOneCommand sendOneCommand = new SendOneCommand();
        sendOneCommand.doInBackground("streamoff");
        try {
            streamOnDatagramReceiver.kill();
        } catch (Exception e) {

        }
    }

    public class SendOneCommand extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... strings) {
            String command = strings[0];
            byte[] buf = strings[0].getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, inetAddressMainSending, portMainSending);
            try {
                socketMainSending.send(packet);
                buf =new byte[500];
                packet = new DatagramPacket(buf, buf.length);
                socketMainSending.receive(packet);
                String doneText = new String(packet.getData(), 0,packet.getLength(), StandardCharsets.UTF_8);
                EventBus.getDefault().post(new eventsGreenRobot.lastReceivedMessage(command + ": " + doneText));
            } catch (IOException e) {
                EventBus.getDefault().post(new eventsGreenRobot.lastReceivedMessage(command + ": IOException - " + e.getMessage()));
            } catch (Exception e) {
                EventBus.getDefault().post(new eventsGreenRobot.lastReceivedMessage(command + ": Exception - " + e.getMessage()));
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);

        // VLC clean up code
        mMediaPlayer.stop();
        mMediaPlayer.getVLCVout().detachViews();
        mMediaPlayer.getVLCVout().removeCallback(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(eventsGreenRobot.lastReceivedMessage event) {
        String lastMessage = (String) event.lastMessage;
        tvMessage.setText(lastMessage);
    };

    public static InetAddress getInetAddressByName(String name)
    {
        AsyncTask<String, Void, InetAddress> task = new AsyncTask<String, Void, InetAddress>()
        {

            @Override
            protected InetAddress doInBackground(String... params)
            {
                try
                {
                    return InetAddress.getByName(params[0]);
                }
                catch (UnknownHostException e)
                {
                    return null;
                }
            }
        };
        try
        {
            return task.execute(name).get();
        }
        catch (InterruptedException e)
        {
            return null;
        }
        catch (ExecutionException e)
        {
            return null;
        }

    }

    private class StatusDatagramReceiver extends Thread {
        private boolean bKeepRunning = true;
        private String lastMessage = "";

        @Override
        public void run() {
            String message;
            byte[] lmessage = new byte[500];
            DatagramPacket packet = new DatagramPacket(lmessage, lmessage.length);

            try {

                while(bKeepRunning) {
                    socketStatusServer.receive(packet);
                    message = new String(lmessage, 0, packet.getLength());
                    lastMessage = message;
                    EventBus.getDefault().post(new eventsGreenRobot.lastReceivedStatus("Status: " + lastMessage));
                }

                if (socketStatusServer == null) {
                    socketStatusServer.close();
                }

            } catch (IOException ioe){
                EventBus.getDefault().post(new eventsGreenRobot.lastReceivedStatus("Status: IOException - " + ioe.getMessage()));
            }

        }

        public void kill() {
            bKeepRunning = false;
        }
    }

    private class StreamOnDatagramReceiver extends Thread {
        private boolean bKeepRunning = true;
        private String lastMessage = "";

        @Override
        public void run() {
            String message;
            byte[] lmessage = new byte[50000];
            DatagramPacket packet = new DatagramPacket(lmessage, lmessage.length);

            try {

                while(bKeepRunning) {
                    socketStreamOnServer.receive(packet);
                    message = new String(lmessage, 0, packet.getLength());
                    lastMessage = message;
                    EventBus.getDefault().post(new eventsGreenRobot.StreamOnReceivedMessage("Video: " + lastMessage));
                }

                if (socketStreamOnServer == null) {
                    socketStreamOnServer.close();
                }

            } catch (IOException ioe){
                EventBus.getDefault().post(new eventsGreenRobot.StreamOnReceivedMessage("Video: IOException - " + ioe.getMessage()));
            }

        }

        public void kill() {
            bKeepRunning = false;
        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(eventsGreenRobot.StreamOnReceivedMessage event) {
        String streamMessage = (String) event.streamMessage;
        tvVideo.setText(streamMessage);
    };

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(eventsGreenRobot.lastReceivedStatus event) {
        String lastStatus = (String) event.lastStatus;
        tvStatus.setText(lastStatus);
    };

    public void on_click_btnTestFfmpeg(View v) {
        if (FFmpeg.getInstance(this).isSupported()) {
            tvMessage.setText("FFmpeg is supported!!!");
        } else {
            tvMessage.setText("FFmpeg is not supported.");
        }
    }

    public void on_click_btnTestVersion(View v) {
        String[] cmd = {"-version"};
        FFmpeg ffmpeg = FFmpeg.getInstance(this);
        // to execute "ffmpeg -version" command you just need to pass "-version"
        ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {

            @Override
            public void onStart() {}

            @Override
            public void onProgress(String message) {}

            @Override
            public void onFailure(String message) {

            }

            @Override
            public void onSuccess(String message) {
                tvMessage.setText("Version: " + message);
            }

            @Override
            public void onFinish() {}

        });
    }

    public void on_click_btnCaptureVideoStream(View v) {
        // Original java line for FFMpeg
        // Runtime.getRuntime().exec("ffmpeg -i udp://0.0.0.0:11111 -f sdl Tello");

        File directory = getFilesDir();
        String output = directory + "/tello.mp4";


        Log.v("MainActivity", "The storage path is: " + output);
        String[] cmd = {"-i", "udp://127.0.0.1:11111", "-vcodec", "copy", output};
        FFmpeg ffmpeg = FFmpeg.getInstance(this);
        // to execute "ffmpeg -version" command you just need to pass "-version"
        fftask = ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {

            @Override
            public void onStart() {
                tvMessage.setText("OnStart called");
            }

            @Override
            public void onProgress(String message) {
                tvMessage.setText("Progress");
            }

            @Override
            public void onFailure(String message) {
                Log.v("TEST", "FFMPEG streaming command failure: " + message);
                tvMessage.setText("FFMPEG streaming command failure: " + message);
            }

            @Override
            public void onSuccess(String message) {
                tvMessage.setText("FFMPEG streaming command success: " + message);
            }

            @Override
            public void onFinish() {}

        });


    }

    public void on_click_btnStopVideoStream(View v) {
        Log.v("MainActivity", "Stop FFMPEG Recording");
        fftask.sendQuitSignal();
    }

    public void on_clik_btnVLCVideoPlayer(View v) {
        final IVLCVout vlcVout = mMediaPlayer.getVLCVout();
        try {
//        final IVLCVout vlcVout2 = mMediaPlayer2.getVLCVout();
            vlcVout.setVideoView(mVideoSurface);
//        vlcVout2.setVideoView(mVideoSurface1);
            vlcVout.attachViews();
//        vlcVout2.attachViews();
            mMediaPlayer.getVLCVout().addCallback(this);
//        mMediaPlayer2.getVLCVout().addCallback(this);

            Media media = new Media(mLibVLC, Uri.parse(SAMPLE_URL));
//        Media media1 = new Media(mLibVLC, Uri.parse(SAMPLE_URL1));
            mMediaPlayer.setMedia(media);
//        mMediaPlayer2.setMedia(media1);
            media.release();
            mMediaPlayer.play();
//        mMediaPlayer2.play();
//        mMediaPlayer.setRate(.5f);
        } catch (Exception e) {
            Toast.makeText(this, "Error typically caused by pressing the VLC player twice", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSurfacesCreated(IVLCVout vlcVout) {
    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vlcVout) {
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void onNewLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
        mVideoWidth = width;
        mVideoHeight = height;
        mVideoVisibleWidth = visibleWidth;
        mVideoVisibleHeight = visibleHeight;
        mVideoSarNum = sarNum;
        mVideoSarDen = sarDen;
    }


}
