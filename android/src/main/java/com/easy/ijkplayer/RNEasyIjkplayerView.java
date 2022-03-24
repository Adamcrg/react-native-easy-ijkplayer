package com.easy.ijkplayer;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import java.io.IOException;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.ISurfaceTextureHolder;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;




public class RNEasyIjkplayerView extends TextureView implements LifecycleEventListener {

    private static final String TAG = "IJKPlayer";
    private static final String NAME_ERROR_EVENT = "onError";
    private static final String NAME_INFO_EVENT = "onInfo";
    private static final String NAME_COMPLETE_EVENT = "onComplete";
    private static final String NAME_PROGRESS_UPDATE_EVENT = "onProgressUpdate";
    private static final String NAME_PREPARE_EVENT = "onPrepared";
    public static final int PROGRESS_UPDATE_INTERVAL_MILLS = 500;
    private IjkMediaPlayer mIjkPlayer;
    public static int mDuration;
    public static int mAutoPlay = 0;
    public static WritableMap size = Arguments.createMap();
    private String mCurrUrl;
    private boolean mManualPause;
    private boolean mManualStop;
    private SurfaceTexture mSurfaceTexture;
    private  Surface mSurface;
    private Handler mHandler = new Handler();
    private Runnable progressUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIjkPlayer == null || mDuration == 0) {
                return;
            }
            long currProgress = mIjkPlayer.getCurrentPosition();
            int mCurrProgress = (int) Math.ceil((currProgress * 1.0f)/1000);
            sendEvent(NAME_PROGRESS_UPDATE_EVENT, "progress", "" + mCurrProgress);
            mHandler.postDelayed(progressUpdateRunnable, PROGRESS_UPDATE_INTERVAL_MILLS);
        }
    };

    public RNEasyIjkplayerView(ReactContext reactContext) {
        super(reactContext);
        reactContext.addLifecycleEventListener(this);
        initIjkMediaPlayer();
        initSurfaceView();
        initIjkMediaPlayerListener();
    }


    private void initSurfaceView() {
        this.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int i, int i1) {
                Log.d(TAG, "onSurfaceTextureAvailable: ");
                if (mSurfaceTexture != null){
                    setSurfaceTexture(mSurfaceTexture);
                    if (mManualPause){
                        mManualPause = false;
                        mIjkPlayer.start();
                    }

                }else {
                    mSurfaceTexture = surface;
                    mSurface = new Surface(surface);
                    mIjkPlayer.setSurface(mSurface);
                }

            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

            }


        });
    }

    private void initIjkMediaPlayer() {
        mIjkPlayer = new IjkMediaPlayer();
    }


    private void initIjkMediaPlayerListener() {

        mIjkPlayer.setLogEnabled(true);

        mIjkPlayer.native_setLogLevel(2);
        mIjkPlayer.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(IMediaPlayer iMediaPlayer) {
                mDuration = (int)Math.ceil(mIjkPlayer.getDuration()/1000);
                mHandler.post(progressUpdateRunnable);
                sendEvent(NAME_PREPARE_EVENT, "isPrepare", "1");
            }
        });

        mIjkPlayer.setOnVideoSizeChangedListener(new IMediaPlayer.OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(IMediaPlayer iMediaPlayer, int width, int height, int i2, int i3) {
                Log.i(TAG, "width:" + width + " height:" + height);
                size.putInt("width", width);
                size.putInt("height", height);
                float ratioHW = height * 1.0f / width;
            }
        });

        mIjkPlayer.setOnInfoListener(new IMediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(IMediaPlayer iMediaPlayer, int infoCode, int i1) {
                sendEvent(NAME_INFO_EVENT, "code", "" + infoCode);
                return false;
            }
        });

        mIjkPlayer.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(IMediaPlayer iMediaPlayer, int errorCode, int i1) {
                sendEvent(NAME_ERROR_EVENT, "code", "" + errorCode);
                return false;
            }
        });

        mIjkPlayer.setOnBufferingUpdateListener(new IMediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(IMediaPlayer iMediaPlayer, int i) {

            }
        });

        mIjkPlayer.setOnCompletionListener(new IMediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(IMediaPlayer iMediaPlayer) {
//                mHandler.removeCallbacks(progressUpdateRunnable);
                sendEvent(NAME_COMPLETE_EVENT, "complete", "1");
                stop();
            }
        });
        //增加视频秒开功能
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1);
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fast", 1);//不额外优化
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 200);//10240
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1);
        //pause output until enough packets have been read after stalling
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0);//是否开启缓冲
        //drop frames when cpu is too slow：0-120
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 120);//丢帧,默认是1
        //automatically start playing on prepared
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);//默认值48
        //0：代表关闭；1：代表开启
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);//开启硬解
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 0);//自动旋屏
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 0);//处理分辨率变化
        //max buffer size should be pre-read：默认为15*1024*1024
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 0);//最大缓存数
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 2);//默认最小帧数2

        //input buffer:don't limit the input buffer size (useful with realtime streams)
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1);//是否限制输入缓存数
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "nobuffer");

    }

    private void sendEvent(String eventName, String paramName, String paramValue) {
        WritableMap event = Arguments.createMap();
        event.putString(paramName, "" + paramValue);
        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                getId(),
                eventName,
                event);
    }


    public void setMAutoPlay(int autoPlay) {
        mAutoPlay = autoPlay;
    }

    public int getMAutoPlay() {
        return mAutoPlay;
    }

    public void seekTo(long progress) {
        if (mIjkPlayer != null) {
            mIjkPlayer.seekTo(progress);
        }
    }

    public void restart(String url) {
        stop();
        setDataSource(url);
        resetSurfaceView();
    }

    public void resetSurfaceView() {
        this.setVisibility(SurfaceView.GONE);
        this.setVisibility(SurfaceView.VISIBLE);
    }

    public void start() {
        if (mIjkPlayer != null) { //已经初始化
            if (mIjkPlayer.isPlaying()) return;
            if (mManualPause) { //手动点击暂停
                mIjkPlayer.start();
            } else { //第一次播放
                mIjkPlayer.prepareAsync();
            }
            resetSurfaceView();
            mManualPause = false;
            sendEvent(NAME_INFO_EVENT, "info", "playing");
        } else {
            setDataSource(mCurrUrl);
            initIjkMediaPlayerListener();
            initSurfaceView();
            resetSurfaceView();
            mIjkPlayer.prepareAsync();
            mManualStop = false;
            sendEvent(NAME_INFO_EVENT, "info", "playing");
        }
    }

    public void pause() {
        if (mIjkPlayer != null) {
            mIjkPlayer.pause();
            mManualPause = true;
            mHandler.removeCallbacks(progressUpdateRunnable);
        }
        sendEvent(NAME_INFO_EVENT, "info", "paused");
    }

    public void stop() {
        if (mIjkPlayer != null) {
            mIjkPlayer.stop();
            mIjkPlayer.reset();
            mIjkPlayer = null;
            mManualStop = true;
            sendEvent(NAME_INFO_EVENT, "info", "stop");
            mHandler.removeCallbacks(progressUpdateRunnable);
        }
    }

    public boolean isPlaying() {
        if (mIjkPlayer != null) {
            return mIjkPlayer.isPlaying();
        }
        return false;
    }

    public void setDataSource(String url) {
        try {
            if (mIjkPlayer == null) initIjkMediaPlayer();
            mIjkPlayer.setDataSource(url);
            mCurrUrl = url;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onHostResume() {
        Log.i(TAG, "onHostResume");
        if (!mManualPause) {
            Log.i(TAG, "exec start");
//            mIjkPlayer.start();
            mHandler.post(progressUpdateRunnable);
        }
    }

    @Override
    public void onHostPause() {
        Log.i(TAG, "onHostPause");
        try {
            if(mIjkPlayer==null) return;
//            mIjkPlayer.pause();
            mHandler.removeCallbacks(progressUpdateRunnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onHostDestroy() {
        Log.i(TAG, "onHostDestroy");
            try {
            if(mIjkPlayer==null) return;

              mIjkPlayer.stop();
        mIjkPlayer.release();
        mHandler.removeCallbacks(progressUpdateRunnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
     
    }
}
