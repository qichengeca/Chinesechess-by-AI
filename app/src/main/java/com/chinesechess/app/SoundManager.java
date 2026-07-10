package com.chinesechess.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * 音效管理器 — 播放棋子点击/移动音效
 */
public class SoundManager {
    private static SoundManager instance;
    private SoundPool soundPool;
    private int clickSoundId;
    private boolean loaded = false;

    private SoundManager(Context context) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(attrs)
                .build();
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool pool, int sampleId, int status) {
                loaded = (status == 0);
            }
        });
        clickSoundId = soundPool.load(context, R.raw.click, 1);
    }

    public static void init(Context context) {
        if (instance == null) instance = new SoundManager(context.getApplicationContext());
    }

    public static SoundManager get() { return instance; }

    public void playClick() {
        if (loaded && soundPool != null) {
            soundPool.play(clickSoundId, 0.6f, 0.6f, 1, 0, 1.0f);
        }
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}