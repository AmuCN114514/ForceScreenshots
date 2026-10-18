package com.forceshot.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String DEFAULT_DIR = "Pictures/Screenshots";

    private EditText dirInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("强制截图");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);

        TextView body = new TextView(this);
        body.setTextSize(16);
        body.setPadding(0, (int) (16 * d), 0, (int) (16 * d));
        body.setText("使用步骤：\n\n"
                + "1. 点击下方按钮进入「无障碍 / 辅助功能」设置\n"
                + "2. 找到「强制截图」并开启\n"
                + "3. 开启后屏幕右侧会出现一个红色「截」悬浮球\n"
                + "4. 在任意界面点击该按钮即可截图；按住可拖动到任意位置\n\n"
                + "因 Android 安全机制，银行、支付等界面使用了 FLAG_SECURE 保护，是否可截取决于机型，部分设备可能失败。");

        TextView dirLabel = new TextView(this);
        dirLabel.setText("保存目录（相册内相对路径）");
        dirLabel.setTextSize(16);

        dirInput = new EditText(this);
        dirInput.setInputType(InputType.TYPE_CLASS_TEXT);
        dirInput.setHint(DEFAULT_DIR);

        Button saveDir = new Button(this);
        saveDir.setText("保存目录设置");
        saveDir.setAllCaps(false);
        saveDir.setOnClickListener(v -> saveDir());

        Button open = new Button(this);
        open.setText("打开无障碍设置");
        open.setAllCaps(false);
        open.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });

        LinearLayout.LayoutParams wrap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        root.addView(title);
        root.addView(body);
        root.addView(dirLabel);
        root.addView(dirInput, wrap);
        root.addView(saveDir, wrap);
        root.addView(open, wrap);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        dirInput.setText(getPrefs().getString("save_dir", DEFAULT_DIR));
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences("forceshot", MODE_PRIVATE);
    }

    private void saveDir() {
        String dir = dirInput.getText().toString().trim();
        while (dir.startsWith("/")) {
            dir = dir.substring(1);
        }
        if (dir.isEmpty()) {
            dir = DEFAULT_DIR;
        }
        getPrefs().edit().putString("save_dir", dir).apply();
        dirInput.setText(dir);
        Toast.makeText(this, "已保存目录：" + dir, Toast.LENGTH_SHORT).show();
    }
}
