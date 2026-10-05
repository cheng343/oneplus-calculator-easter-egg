package io.github.cheng343.calculator.easteregg;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private CheckBox hideIcon;
    private boolean updatingIconState;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars());
            view.setPadding(padding + bars.left, padding + bars.top,
                    padding + bars.right, padding + bars.bottom);
            return insets;
        });
        TextView title = new TextView(this);
        title.setText(R.string.module_name);
        title.setTextSize(26);
        layout.addView(title);
        TextView instructions = new TextView(this);
        instructions.setText(R.string.instructions);
        instructions.setTextSize(16);
        instructions.setPadding(0, padding, 0, padding);
        layout.addView(instructions);
        Button open = new Button(this);
        open.setText(R.string.open_calculator);
        open.setOnClickListener(view -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.coloros.calculator");
            if (launch == null) Toast.makeText(this, R.string.calculator_missing, Toast.LENGTH_SHORT).show();
            else startActivity(launch);
        });
        layout.addView(open);
        hideIcon = new CheckBox(this);
        hideIcon.setText(R.string.hide_launcher_icon);
        hideIcon.setChecked(LauncherIcon.isHidden(this));
        hideIcon.setOnCheckedChangeListener((button, hidden) -> {
            if (updatingIconState) return;
            try {
                LauncherIcon.setHidden(this, hidden);
            } catch (RuntimeException error) {
                updatingIconState = true;
                button.setChecked(!hidden);
                updatingIconState = false;
                Toast.makeText(this, R.string.icon_change_failed, Toast.LENGTH_LONG).show();
            }
        });
        layout.addView(hideIcon);
        TextView iconHint = new TextView(this);
        iconHint.setText(R.string.hide_launcher_icon_help);
        iconHint.setPadding(0, 0, 0, padding);
        layout.addView(iconHint);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(layout, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        layout.requestApplyInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hideIcon == null) return;
        updatingIconState = true;
        try {
            hideIcon.setChecked(LauncherIcon.isHidden(this));
        } finally {
            updatingIconState = false;
        }
    }
}
