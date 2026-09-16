package github.zerorooot.nap511.player;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.elvishew.xlog.XLog;
import com.shuyu.gsyvideoplayer.subtitle.GSYSubtitleStyle;
import com.shuyu.gsyvideoplayer.utils.CommonUtil;
import com.shuyu.gsyvideoplayer.utils.GSYVideoType;
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import github.zerorooot.nap511.R;
import github.zerorooot.nap511.bean.SubtitleStyleBean;

public class MyGSYVideoPlayer extends StandardGSYVideoPlayer {
    private TextView mMoreScale;
    private TextView switchSpeed;

    // 统一的合并抽屉面板组件
    private View layoutDrawer;
    private TextView tvDrawerTitle;
    private DrawerType mCurrentDrawerType = null;
    private OnDrawerOpenListener mOnDrawerOpenListener;

    /**
     * 抽屉类型枚举：选集、倍速、画面比例、字幕
     */
    public enum DrawerType {
        EPISODE, SPEED, SCALE, SUBTITLE
    }

    /**
     * 抽屉打开监听接口，用于 Activity/Fragment 动态切换 RecyclerView 适配器
     */
    public interface OnDrawerOpenListener {
        void onDrawerOpen(DrawerType type);
    }

    private int mType = 0;

    long forwardRewindIncrementMs = 15000;
    private TextView batteryTextView;
    private TextView timeTextView;
    private View layoutStatusInfo;
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

    /**
     * 设置抽屉打开监听器
     */
    public void setOnDrawerOpenListener(OnDrawerOpenListener listener) {
        this.mOnDrawerOpenListener = listener;
    }

    /**
     * 获取当前打开的抽屉类型
     */
    public DrawerType getCurrentDrawerType() {
        return mCurrentDrawerType;
    }

    private void initView() {
        batteryTextView = findViewById(R.id.batteryTextView);
        timeTextView = findViewById(R.id.timeTextView);
        layoutStatusInfo = findViewById(R.id.layout_status_info);

        mMoreScale = findViewById(R.id.moreScale);
        switchSpeed = findViewById(R.id.switchSpeed);
        TextView switchEpisode = findViewById(R.id.switchEpisode);
        TextView switchSubtitle = findViewById(R.id.switchSubtitle);

        // 绑定统一的右侧抽屉面板及其组件
        layoutDrawer = findViewById(R.id.layout_drawer);
        tvDrawerTitle = findViewById(R.id.tv_drawer_title);
        View closeDrawer = findViewById(R.id.close_drawer);

        // 底部各按钮绑定点击事件，打开对应类型的抽屉
        if (switchEpisode != null) {
            switchEpisode.setOnClickListener(v -> openDrawer(DrawerType.EPISODE, "选集"));
        }
        if (switchSpeed != null) {
            switchSpeed.setOnClickListener(v -> openDrawer(DrawerType.SPEED, "播放倍速"));
        }
        if (mMoreScale != null) {
            mMoreScale.setOnClickListener(v -> openDrawer(DrawerType.SCALE, "画面比例"));
        }
        if (switchSubtitle != null) {
            switchSubtitle.setOnClickListener(v -> openDrawer(DrawerType.SUBTITLE, mTitleTextView.getText() + " " + CommonUtil.stringForTime(getDuration())));
        }

        // 抽屉关闭按钮事件
        if (closeDrawer != null) {
            closeDrawer.setOnClickListener(v -> hideAllDrawers());
        }

        updateStatusInfoVisibility();
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateStatusInfoVisibility();
    }

    private void updateStatusInfoVisibility() {
        if (layoutStatusInfo != null) {
            boolean isPortrait = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
            layoutStatusInfo.setVisibility(isPortrait ? GONE : VISIBLE);
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

    /**
     * 打开指定类型的抽屉并更新标题
     *
     * @param type  抽屉类型 (选集/倍速/画面比例)
     * @param title 抽屉标题文本
     */
    public void openDrawer(DrawerType type, String title) {
        if (layoutDrawer != null) {
            // 如果当前点击的抽屉已经处于显示状态，再次点击时则进行关抽屉切换
            if (layoutDrawer.getVisibility() == VISIBLE && mCurrentDrawerType == type) {
                hideAllDrawers();
                return;
            }
            if (tvDrawerTitle != null) {
                tvDrawerTitle.setText(title);
            }
            mCurrentDrawerType = type;
            layoutDrawer.setVisibility(VISIBLE);
            if (mOnDrawerOpenListener != null) {
                mOnDrawerOpenListener.onDrawerOpen(type);
            }
        }
    }

    /**
     * 隐藏抽屉面板
     */
    public void hideAllDrawers() {
        if (layoutDrawer != null) {
            layoutDrawer.setVisibility(GONE);
        }
        mCurrentDrawerType = null;
    }

    /**
     * 判断当前抽屉面板是否正在显示
     */
    public boolean isAnyDrawerShowing() {
        return layoutDrawer != null && layoutDrawer.getVisibility() == VISIBLE;
    }

    @Override
    protected void onClickUiToggle(MotionEvent e) {
        if (isAnyDrawerShowing()) {
            hideAllDrawers();
            return;
        }
        super.onClickUiToggle(e);
    }

    public void setAspectScale(int type) {
        mType = type;
        resolveTypeUI();
    }

    public void setSpeedText(String text) {
        if (switchSpeed != null) {
            switchSpeed.setText(text);
        }
    }

    /**
     * 应用字幕样式设置 (字号、颜色、背景色、字体、加粗)
     */
    public void applySubtitleStyle(SubtitleStyleBean styleBean) {
        if (styleBean == null) return;
        GSYSubtitleStyle style = new GSYSubtitleStyle.Builder()
                .setTextSizeSp(styleBean.getTextSizeSp())
                .setTextColor(styleBean.getTextColor())
                .setBackgroundColor(styleBean.getBackgroundColor())
                .build();
        setSubtitleStyle(style);

        if (mSubtitleView != null) {
            int styleFlags = styleBean.isBold() ? Typeface.BOLD : Typeface.NORMAL;
            mSubtitleView.setTypeface(styleBean.getFontFamily().getTypeface(), styleFlags);
        }
    }

}