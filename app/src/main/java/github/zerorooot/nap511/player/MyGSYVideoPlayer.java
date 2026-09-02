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
import github.zerorooot.nap511.util.ConfigKeyUtil;
import github.zerorooot.nap511.util.DataStoreUtil;

public class MyGSYVideoPlayer extends StandardGSYVideoPlayer {
    private TextView mMoreScale;
    private TextView switchSpeed;
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

    private void initView() {
        batteryTextView = findViewById(R.id.batteryTextView);
        timeTextView = findViewById(R.id.timeTextView);

        if (DataStoreUtil.INSTANCE.getData(ConfigKeyUtil.HIDE_LOADING_VIEW, false)) {
            XLog.d("MyGSYVideoPlayer hide video start view");
            findViewById(R.id.startAndLoadLayout).setVisibility(GONE);
        }

        mMoreScale = findViewById(R.id.moreScale);
        switchSpeed = findViewById(R.id.switchSpeed);

        // 切换画面比例
        mMoreScale.setOnClickListener(v -> {
            if (!mHadPlay) return;
            mType = (mType + 1) % 5;
            resolveTypeUI();
        });

        // 切换倍速
        switchSpeed.setOnClickListener(v -> {
            v.setOnCreateContextMenuListener((menu, v1, menuInfo) -> {
                MenuItem speed5 = menu.add("× 0.5");
                speed5.setOnMenuItemClickListener(e -> {
                    getCurrentPlayer().setSpeed(0.5f, true);
                    switchSpeed.setText("0.5X");
                    return true;
                });
                MenuItem speed1 = menu.add("× 1.0");
                speed1.setOnMenuItemClickListener(e -> {
                    getCurrentPlayer().setSpeed(1f, true);
                    switchSpeed.setText("倍速");
                    return true;
                });
                MenuItem speed15 = menu.add("× 1.5");
                speed15.setOnMenuItemClickListener(e -> {
                    getCurrentPlayer().setSpeed(1.5f, true);
                    switchSpeed.setText("1.5X");
                    return true;
                });
                MenuItem speed2 = menu.add("× 2.0");
                speed2.setOnMenuItemClickListener(e -> {
                    getCurrentPlayer().setSpeed(2f, true);
                    switchSpeed.setText("2.0X");
                    return true;
                });
            });
            v.showContextMenu(v.getX(), v.getY());
        });
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
}