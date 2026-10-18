package com.forceshot.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.TextView;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ForceShotAccessibilityService extends AccessibilityService {

    private static final String DEFAULT_DIR = "Pictures/Screenshots";

    private WindowManager windowManager;
    private View floatingButton;
    private View tipView;
    private volatile boolean capturing = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Runnable removeTip = () -> {
        if (tipView != null && windowManager != null) {
            try {
                windowManager.removeView(tipView);
            } catch (Exception ignored) {
            }
            tipView = null;
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            setServiceInfo(info);
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showFloatingButton();
        showTip("强制截图已开启，点击屏幕上的「截」悬浮球截图");
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences("forceshot", MODE_PRIVATE);
    }

    private void showFloatingButton() {
        if (floatingButton != null) {
            return;
        }
        float d = getResources().getDisplayMetrics().density;
        int size = (int) (56 * d);

        Button b = new Button(this);
        b.setText("截");
        b.setTextSize(20);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#E53935"));
        bg.setStroke((int) (2 * d), Color.WHITE);
        b.setBackground(bg);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        lp.x = (int) (12 * d);
        lp.y = 0;

        b.setOnTouchListener(new FloatingTouchListener(b, lp, (int) (8 * d)));

        try {
            windowManager.addView(b, lp);
            floatingButton = b;
        } catch (Exception e) {
            showTip("悬浮球创建失败: " + e.getMessage());
        }
    }

    /**
     * 处理悬浮球的拖动与点击：移动超过阈值判定为拖动，否则判定为点击截图。
     */
    private class FloatingTouchListener implements View.OnTouchListener {
        private final View view;
        private final WindowManager.LayoutParams lp;
        private final int touchSlop;

        private float downRawX, downRawY;
        private int startX, startY;
        private boolean dragging;

        FloatingTouchListener(View view, WindowManager.LayoutParams lp, int touchSlop) {
            this.view = view;
            this.lp = lp;
            this.touchSlop = touchSlop;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startX = lp.x;
                    startY = lp.y;
                    dragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (!dragging && Math.hypot(dx, dy) > touchSlop) {
                        dragging = true;
                    }
                    if (dragging) {
                        // gravity=END 时，lp.x 越大越靠左；gravity=CENTER_VERTICAL 时，lp.y 越大越靠下
                        lp.x = startX - (int) dx;
                        lp.y = startY + (int) dy;
                        clampToScreen();
                        try {
                            windowManager.updateViewLayout(view, lp);
                        } catch (Exception ignored) {
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!dragging) {
                        captureScreen();
                    }
                    return true;
            }
            return false;
        }

        private void clampToScreen() {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int screenW = dm.widthPixels;
            int screenH = dm.heightPixels;
            int size = lp.width;

            int minX = 0;
            int maxX = Math.max(minX, screenW - size);
            if (lp.x < minX) {
                lp.x = minX;
            } else if (lp.x > maxX) {
                lp.x = maxX;
            }

            int halfLimit = Math.max(0, screenH / 2 - size / 2);
            if (lp.y < -halfLimit) {
                lp.y = -halfLimit;
            } else if (lp.y > halfLimit) {
                lp.y = halfLimit;
            }
        }
    }

    private void captureScreen() {
        if (capturing) {
            showTip("正在截图，请稍候…");
            return;
        }
        capturing = true;

        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(),
                    new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(ScreenshotResult screenshotResult) {
                            try {
                                HardwareBuffer buffer = screenshotResult.getHardwareBuffer();
                                Bitmap hw = Bitmap.wrapHardwareBuffer(
                                        buffer, screenshotResult.getColorSpace());
                                if (hw == null) {
                                    showTip("截图失败：无法解析图像数据");
                                    return;
                                }
                                Bitmap sw = hw.copy(Bitmap.Config.ARGB_8888, false);
                                hw.recycle();
                                if (sw != null) {
                                    saveToGallery(sw);
                                    sw.recycle();
                                }
                            } catch (Exception e) {
                                showTip("截图失败: " + e.getMessage());
                            } finally {
                                capturing = false;
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            capturing = false;
                            showTip("截图失败，错误码: " + errorCode);
                        }
                    });
        } catch (Exception e) {
            capturing = false;
            showTip("截图调用失败: " + e.getMessage());
        }
    }

    private void saveToGallery(Bitmap bitmap) {
        String name = "ForceShot_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                + ".png";

        String dir = getPrefs().getString("save_dir", DEFAULT_DIR);
        if (dir == null || dir.trim().isEmpty()) {
            dir = DEFAULT_DIR;
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, dir);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri uri = null;
        try {
            uri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                showTip("保存失败：无法创建相册条目");
                return;
            }
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) {
                    showTip("保存失败：无法写入文件");
                    return;
                }
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                os.flush();
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
            showTip("已截图");
        } catch (Exception e) {
            showTip("保存失败: " + e.getMessage());
        }
    }

    /**
     * 用无障碍 overlay 显示一条会自动消失的短提示，避免部分 ROM 拦截后台 Toast 导致看不到。
     */
    private void showTip(String msg) {
        mainHandler.post(() -> {
            if (windowManager == null) {
                return;
            }
            removeTip.run();

            float d = getResources().getDisplayMetrics().density;

            TextView tv = new TextView(this);
            tv.setText(msg);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(15);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#CC000000"));
            bg.setCornerRadius(24 * d);
            tv.setBackground(bg);

            int ph = (int) (14 * d);
            int pv = (int) (10 * d);
            tv.setPadding(ph, pv, ph, pv);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = (int) (80 * d);

            try {
                windowManager.addView(tv, lp);
                tipView = tv;
                mainHandler.removeCallbacks(removeTip);
                mainHandler.postDelayed(removeTip, 1400);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 无需处理事件，仅使用 service 的截图能力
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        removeFloatingButton();
        removeTip.run();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        removeFloatingButton();
        removeTip.run();
        super.onDestroy();
    }

    private void removeFloatingButton() {
        if (floatingButton != null && windowManager != null) {
            try {
                windowManager.removeView(floatingButton);
            } catch (Exception ignored) {
            }
            floatingButton = null;
        }
    }
}
