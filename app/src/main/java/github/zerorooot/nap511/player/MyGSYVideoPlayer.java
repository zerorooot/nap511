package github.zerorooot.nap511.player;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.elvishew.xlog.XLog;
import com.shuyu.gsyvideoplayer.utils.CommonUtil;
import com.shuyu.gsyvideoplayer.utils.GSYVideoType;
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import github.zerorooot.nap511.R;

public class MyGSYVideoPlayer extends StandardGSYVideoPlayer {
    private TextView mMoreScale;
    private TextView switchSpeed;
    private TextView switchEpisode;
    private View layoutEpisodeDrawer;
    private View closeEpisodeDrawer;
    private View layoutSpeedDrawer;
    private View closeSpeedDrawer;
    private View layoutScaleDrawer;
    private View closeScaleDrawer;
    private int mType = 0;

    long forwardRewindIncrementMs = 15000;
    private TextView batteryTextView;
    private TextView timeTextView;
    public static String TAG = "MyGSYVideoPlayer";

    // 独立的 UI 主线程定时器，独立于 GSY 播放状态
    private final Handler mClockHandler = new Handler(Looper.getMainLooper());
    private final Runnable mClockRunnable = new Runnable() {
        @Override
        public void run() {
            setBatteryAndTime();
            // 每 1000ms 刷新一次，确保即使暂停也能每秒更新
            mClockHandler.postDelayed(this, 1000);
        }
    };

    public MyGSYVideoPlayer(Context context) {
        super(context);
    }

    public MyGSYVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public String getPlayTag() {
        return TAG;
    }

    @Override
    protected void init(Context context) {
        super.init(context);
        GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_DEFAULT);
        initView();
    }

    public void setHideLoadingView(boolean hide) {
        if (hide && findViewById(R.id.startAndLoadLayout) != null) {
            XLog.d("MyGSYVideoPlayer hide video start view");
            findViewById(R.id.startAndLoadLayout).setVisibility(GONE);
        }
    }

    private void initView() {
        batteryTextView = findViewById(R.id.batteryTextView);
        timeTextView = findViewById(R.id.timeTextView);

        mMoreScale = findViewById(R.id.moreScale);
        switchSpeed = findViewById(R.id.switchSpeed);
        switchEpisode = findViewById(R.id.switchEpisode);

        layoutEpisodeDrawer = findViewById(R.id.layout_episode_drawer);
        closeEpisodeDrawer = findViewById(R.id.close_episode_drawer);
        layoutSpeedDrawer = findViewById(R.id.layout_speed_drawer);
        closeSpeedDrawer = findViewById(R.id.close_speed_drawer);
        layoutScaleDrawer = findViewById(R.id.layout_scale_drawer);
        closeScaleDrawer = findViewById(R.id.close_scale_drawer);

        if (switchEpisode != null) {
            switchEpisode.setOnClickListener(v -> toggleDrawer(layoutEpisodeDrawer));
        }
        if (switchSpeed != null) {
            switchSpeed.setOnClickListener(v -> toggleDrawer(layoutSpeedDrawer));
        }
        if (mMoreScale != null) {
            mMoreScale.setOnClickListener(v -> toggleDrawer(layoutScaleDrawer));
        }

        if (closeEpisodeDrawer != null) {
            closeEpisodeDrawer.setOnClickListener(v -> hideAllDrawers());
        }
        if (closeSpeedDrawer != null) {
            closeSpeedDrawer.setOnClickListener(v -> hideAllDrawers());
        }
        if (closeScaleDrawer != null) {
            closeScaleDrawer.setOnClickListener(v -> hideAllDrawers());
        }
    }

    /**
     * View 挂载到窗口时启动定时器
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startClockTimer();
    }

    /**
     * View 销毁离开窗口时停止定时器，防止内存泄漏
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopClockTimer();
    }

    private void startClockTimer() {
        stopClockTimer();
        mClockHandler.post(mClockRunnable);
    }

    private void stopClockTimer() {
        mClockHandler.removeCallbacks(mClockRunnable);
    }


    @Override
    public int getLayoutId() {
        return R.layout.video_layout_preview;
    }

    private void setBatteryAndTime() {
        if (batteryTextView == null || timeTextView == null || getContext() == null) {
            return;
        }

        // 获取电量与充电状态
        Intent batteryStatus = getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;

            int batteryPct = (scale > 0) ? (level * 100 / scale) : 100;
            batteryTextView.setText(batteryPct + "%" + (isCharging ? " ⚡" : ""));
        }

        // 规范为 HH:mm:ss
        timeTextView.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
    }

    private void resolveTypeUI() {
        if (!mHadPlay) return;
        if (mType == 1) {
            mMoreScale.setText("16:9");
            GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_16_9);
        } else if (mType == 2) {
            mMoreScale.setText("4:3");
            GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_4_3);
        } else if (mType == 3) {
            mMoreScale.setText("全屏");
            GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_FULL);
        } else if (mType == 4) {
            mMoreScale.setText("拉伸");
            GSYVideoType.setShowType(GSYVideoType.SCREEN_MATCH_FULL);
        } else if (mType == 0) {
            mMoreScale.setText("默认");
            GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_DEFAULT);
        }
        changeTextureViewShowType();
        if (mTextureView != null) mTextureView.requestLayout();
    }

    public void forwardOrRewind(long time) {
        long totalTimeDuration = getDuration();
        mSeekTimePosition = (int) (getGSYVideoManager().getCurrentPosition() + time);
        if (mSeekTimePosition > totalTimeDuration) {
            mSeekTimePosition = totalTimeDuration;
        }
        String seekTime = CommonUtil.stringForTime(mSeekTimePosition);
        String totalTime = CommonUtil.stringForTime(totalTimeDuration);
        getGSYVideoManager().seekTo(mSeekTimePosition);

        new Handler(Objects.requireNonNull(Looper.myLooper())).postDelayed(() -> {
            showProgressDialog(time, seekTime, mSeekTimePosition, totalTime, totalTimeDuration);
        }, 100);
        new Handler(Objects.requireNonNull(Looper.myLooper())).postDelayed(this::dismissProgressDialog, 600);
    }

    public void playNext(String url, String title) {
        setUp(url, mCache, null, title, true);
        mTitleTextView.setText(title);
        startPlayLogic();
    }

    @Override
    protected void changeUiToPreparingShow() {
        super.changeUiToPreparingShow();
        setViewShowState(mStartButton, VISIBLE);
    }

    @Override
    protected void changeUiToPlayingBufferingShow() {
        super.changeUiToPlayingBufferingShow();
        setViewShowState(mStartButton, VISIBLE);
    }

    @Override
    protected void changeUiToPlayingBufferingClear() {
        super.changeUiToPlayingBufferingClear();
        setViewShowState(mStartButton, VISIBLE);
    }

    @Override
    public void touchDoubleUp(MotionEvent event) {
        float x = event.getX();
        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;

        if (x <= screenWidth * 0.3) {
            forwardOrRewind(forwardRewindIncrementMs * (-1));
        } else if (x > screenWidth * 0.3 && x < screenWidth * 0.6) {
            if (!mHadPlay) return;
            clickStartIcon();
        } else if (x >= screenWidth * 0.6) {
            forwardOrRewind(forwardRewindIncrementMs);
        }
    }

    public void showDrawer(View drawer) {
        hideAllDrawers();
        if (drawer != null) {
            drawer.setVisibility(VISIBLE);
        }
    }

    public void toggleDrawer(View drawer) {
        if (drawer != null) {
            if (drawer.getVisibility() == VISIBLE) {
                drawer.setVisibility(GONE);
            } else {
                showDrawer(drawer);
            }
        }
    }

    public void hideAllDrawers() {
        if (layoutEpisodeDrawer != null) layoutEpisodeDrawer.setVisibility(GONE);
        if (layoutSpeedDrawer != null) layoutSpeedDrawer.setVisibility(GONE);
        if (layoutScaleDrawer != null) layoutScaleDrawer.setVisibility(GONE);
    }

    public boolean isAnyDrawerShowing() {
        return (layoutEpisodeDrawer != null && layoutEpisodeDrawer.getVisibility() == VISIBLE)
                || (layoutSpeedDrawer != null && layoutSpeedDrawer.getVisibility() == VISIBLE)
                || (layoutScaleDrawer != null && layoutScaleDrawer.getVisibility() == VISIBLE);
    }

    public void showEpisodeDrawer() {
        showDrawer(layoutEpisodeDrawer);
    }

    public void hideEpisodeDrawer() {
        hideAllDrawers();
    }

    public boolean isEpisodeDrawerShowing() {
        return isAnyDrawerShowing();
    }

    public void setAspectScale(int type) {
        mType = type;
        resolveTypeUI();
    }

    public int getAspectScaleType() {
        return mType;
    }

    public void setSpeedText(String text) {
        if (switchSpeed != null) {
            switchSpeed.setText(text);
        }
    }

    public void setMoreScaleText(String text) {
        if (mMoreScale != null) {
            mMoreScale.setText(text);
        }
    }

    @Override
    protected void hideAllWidget() {
        super.hideAllWidget();
        hideAllDrawers();
    }
}