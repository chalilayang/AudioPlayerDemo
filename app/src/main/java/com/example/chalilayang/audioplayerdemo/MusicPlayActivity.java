package com.example.chalilayang.audioplayerdemo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.example.chalilayang.audioplayerdemo.utils.ChooseMediaUtil;
import com.example.chalilayang.audioplayerdemo.utils.FileUtils;

public class MusicPlayActivity extends Activity {

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final String TAG = MusicPlayActivity.class.getSimpleName();
    private static final int REQUEST_CODE = 17;
    public static final String KEY = "music_path";

    private String musicPath = null;
    private AudioPlayer audioPlayer;
    private boolean isPlaying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_play);
        isPlaying = false;
        musicPath = getIntent().getStringExtra(KEY);
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(0.33f);
            }
        });
        findViewById(R.id.button2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(0.5f);
            }
        });
        findViewById(R.id.button3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(1f);
            }
        });
        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(2f);
            }
        });
        findViewById(R.id.button5).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(3f);
            }
        });
        findViewById(R.id.button6).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPlaying) {
                    pause();
                    ((TextView)findViewById(R.id.button6)).setText("resume");
                    isPlaying = false;
                } else {
                    resume();
                    ((TextView)findViewById(R.id.button6)).setText("pause");
                    isPlaying = true;
                }
            }
        });
        findViewById(R.id.button7).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startMusicSelectActivity();
            }
        });
    }

    public void play(float speed) {
        if (FileUtils.isValid(musicPath)) {
            if (audioPlayer != null) {
                audioPlayer.releaseResource();
                audioPlayer = null;
            }
            audioPlayer = new AudioPlayer();
            if (audioPlayer.openAudio(musicPath, 0, speed)) {
                audioPlayer.prepare();
                audioPlayer.play(speed);
                isPlaying = true;
            }
        }
    }

    public void pause() {
        if (audioPlayer != null) {
            audioPlayer.pause();
        }
    }

    public void resume() {
        if (audioPlayer != null) {
            audioPlayer.resume(1f);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioPlayer != null) {
            audioPlayer.releaseResource();
            audioPlayer = null;
        }
    }

    private void startMusicSelectActivity() {
        Intent intent = new Intent();
        intent.setType("audio/mpeg");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    Log.i(this.TAG, "---uri===" + uri);
                    Log.i(this.TAG, "---uri.getPath===" + uri.getPath());
                    musicPath = ChooseMediaUtil.getPath(this, uri);
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            verifyStoragePermissions(this);
        }
    }
    /**
     * 检查应用程序是否允许写入存储设备
     * <p>
     * <p>
     * <p>
     * 如果应用程序不允许那么会提示用户授予权限
     *
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission
                .WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }
}
