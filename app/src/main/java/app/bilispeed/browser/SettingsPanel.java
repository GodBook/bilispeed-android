package app.bilispeed.browser;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;

/** A native settings surface; opening it never replaces or reloads the current web page. */
@android.annotation.SuppressLint("ViewConstructor") // Built with a required listener, never inflated from XML.
final class SettingsPanel extends ScrollView {
    interface Listener {
        void onRate(float rate);
        void onRemember(boolean enabled);
        void onTouchLayout(boolean enabled);
        void onAutomatic(boolean enabled);
        void onAction(String action);
    }

    private static final int PINK = Color.rgb(215, 68, 111);
    private static final int INK = Color.rgb(36, 38, 46);
    private static final int MUTED = Color.rgb(120, 122, 132);
    private static final int SOFT = Color.rgb(245, 246, 248);
    private static final int BLUSH = Color.rgb(253, 237, 242);
    private static final float[] PRESETS = {1, 1.25f, 1.5f, 2, 2.5f, 3, 3.5f, 4, 5};
    private final Listener listener;
    private final LinearLayout content;
    private final ArrayList<Button> presets = new ArrayList<>();
    private final TextView chosen;
    private final TextView status;
    private final EditText custom;
    private final SeekBar slider;
    private final Switch remember;
    private final Switch touch;
    private final Switch automatic;
    private final TextView update;
    private boolean syncing;

    SettingsPanel(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setBackgroundColor(SOFT);
        setFillViewport(true);
        setClipToPadding(false);
        setVerticalScrollBarEnabled(false);
        setContentDescription("设置页面");
        content = column();
        content.setPadding(dp(20), dp(18), dp(20), dp(28));
        content.setFocusableInTouchMode(true);
        addView(content, new ScrollView.LayoutParams(-1, -2));

        LinearLayout heading = row();
        LinearLayout titles = column();
        TextView title = label("设置", 30, INK);
        title.setTypeface(null, Typeface.BOLD);
        if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        titles.addView(title);
        TextView subtitle = label("按你的习惯，舒服地看", 13, MUTED);
        subtitle.setPadding(0, dp(5), 0, 0);
        titles.addView(subtitle);
        heading.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        Button back = actionButton("返回浏览", false);
        back.setOnClickListener(view -> listener.onAction("back"));
        heading.addView(back, new LinearLayout.LayoutParams(dp(88), dp(44)));
        content.addView(heading);

        LinearLayout playback = section("播放", "倍速即时生效");
        LinearLayout rateHeading = row();
        TextView rateTitle = label("播放倍速", 18, INK);
        rateTitle.setTypeface(null, Typeface.BOLD);
        rateHeading.addView(rateTitle, new LinearLayout.LayoutParams(0, -2, 1));
        chosen = label("1x", 34, PINK);
        chosen.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chosen.setFontFeatureSettings("tnum");
        chosen.setContentDescription("当前倍速");
        rateHeading.addView(chosen);
        playback.addView(rateHeading);
        status = label("打开视频后自动应用", 12, MUTED);
        status.setPadding(0, dp(3), 0, dp(16));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        playback.addView(status);
        for (int line = 0; line < 3; line++) {
            LinearLayout rates = row();
            for (int column = 0; column < 3; column++) {
                float rate = PRESETS[line * 3 + column];
                Button item = actionButton(MainActivity.formatRate(rate), false);
                item.setTag(rate);
                item.setContentDescription("选择 " + MainActivity.formatRate(rate) + " 倍速");
                item.setOnClickListener(view -> listener.onRate(rate));
                LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, dp(44), 1);
                cell.rightMargin = column == 2 ? 0 : dp(8);
                rates.addView(item, cell);
                presets.add(item);
            }
            LinearLayout.LayoutParams lineLayout = new LinearLayout.LayoutParams(-1, -2);
            lineLayout.bottomMargin = dp(8);
            playback.addView(rates, lineLayout);
        }
        LinearLayout rangeHeading = row();
        rangeHeading.setPadding(0, dp(8), 0, 0);
        rangeHeading.addView(label("自由调节", 13, MUTED), new LinearLayout.LayoutParams(0, -2, 1));
        rangeHeading.addView(label("0.25–5x", 12, MUTED));
        playback.addView(rangeHeading);
        slider = new SeekBar(context);
        slider.setMax(95);
        slider.setContentDescription("自由调节倍速，0.25 到 5 倍");
        slider.setProgressTintList(ColorStateList.valueOf(PINK));
        slider.setThumbTintList(ColorStateList.valueOf(PINK));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    float rate = .25f + progress * .05f;
                    chosen.setText(MainActivity.formatRate(rate));
                    custom.setText(MainActivity.number(rate));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { listener.onRate(.25f + bar.getProgress() * .05f); }
        });
        playback.addView(slider, new LinearLayout.LayoutParams(-1, dp(44)));
        LinearLayout customRow = row();
        custom = new EditText(context);
        custom.setSingleLine(true);
        custom.setTextSize(16);
        custom.setTextColor(INK);
        custom.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        custom.setImeOptions(EditorInfo.IME_ACTION_DONE);
        custom.setContentDescription("自定义倍速，输入 0.25 到 5");
        custom.setHint("例如 2.75");
        custom.setPadding(dp(14), 0, dp(14), 0);
        custom.setBackground(shape(SOFT, 12));
        customRow.addView(custom, new LinearLayout.LayoutParams(0, dp(46), 1));
        customRow.addView(label("  倍  ", 14, MUTED));
        Button apply = actionButton("应用", true);
        apply.setOnClickListener(view -> applyCustomRate());
        custom.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            applyCustomRate();
            return true;
        });
        customRow.addView(apply, new LinearLayout.LayoutParams(dp(72), dp(46)));
        playback.addView(customRow);
        remember = toggle(playback, "记住上次倍速", "下次打开时沿用当前选择", enabled -> listener.onRemember(enabled));

        LinearLayout browsing = section("浏览与外观", "适合手机，也保留原版选择");
        touch = toggle(browsing, "手机触屏布局", "关闭后使用电脑原版，可双指缩放", enabled -> listener.onTouchLayout(enabled));
        divider(browsing);
        settingAction(browsing, "字幕按钮外观", "调整大小与不透明度", "appearance");

        LinearLayout tools = section("常用工具", "当前页面的快捷操作");
        LinearLayout toolRow = row();
        String[] actions = {"open", "copy", "reload"};
        String[] labels = {"打开链接", "复制链接", "刷新页面"};
        for (int index = 0; index < actions.length; index++) {
            String action = actions[index];
            Button item = actionButton(labels[index], false);
            item.setOnClickListener(view -> listener.onAction(action));
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, dp(48), 1);
            cell.rightMargin = index == 2 ? 0 : dp(8);
            toolRow.addView(item, cell);
        }
        tools.addView(toolRow);
        settingAction(tools, "在系统浏览器打开", "使用当前页面链接", "external");

        LinearLayout app = section("关于与更新", "B站倍速浏览器 · " + BuildConfig.VERSION_NAME);
        update = settingAction(app, "检查更新", "通过 GitHub 获取正式版本", "update");
        divider(app);
        automatic = toggle(app, "启动时检查更新", "发现新版本时提醒，不自动下载", enabled -> listener.onAutomatic(enabled));
        divider(app);
        settingAction(app, "项目源码", BuildConfig.UPDATE_REPOSITORY, "source");
        settingAction(app, "关于应用", "使用说明与版本信息", "about");
        TextView footnote = label("设置自动保存\n内容与账号服务由哔哩哔哩官方网页提供", 12, MUTED);
        footnote.setGravity(Gravity.CENTER);
        footnote.setLineSpacing(dp(5), 1);
        footnote.setPadding(0, dp(22), 0, 0);
        content.addView(footnote);
    }

    void sync(float rate, boolean rememberRate, boolean touchLayout, boolean automaticUpdates, String updateLabel) {
        syncing = true;
        setRate(rate);
        remember.setChecked(rememberRate);
        touch.setChecked(touchLayout);
        automatic.setChecked(automaticUpdates);
        setUpdateLabel(updateLabel);
        syncing = false;
    }

    void setRate(float rate) {
        chosen.setText(MainActivity.formatRate(rate));
        if (!custom.hasFocus()) custom.setText(MainActivity.number(rate));
        slider.setProgress(Math.round((rate - .25f) / .05f));
        for (Button button : presets) {
            boolean selected = Math.abs((Float) button.getTag() - rate) < .001;
            if (button.isSelected() != selected) {
                button.setSelected(selected);
                button.setTextColor(selected ? PINK : INK);
                button.setBackground(ripple(selected ? BLUSH : SOFT, 11));
                button.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            }
        }
    }

    void setPlaybackStatus(String value) { if (!value.contentEquals(status.getText())) status.setText(value); }
    void setUpdateLabel(String value) { if (!value.contentEquals(update.getText())) update.setText(value); }

    private void applyCustomRate() {
        try {
            float rate = Float.parseFloat(custom.getText().toString().trim().replace(',', '.'));
            if (!MainActivity.validRate(rate)) throw new IllegalArgumentException();
            custom.setError(null);
            custom.clearFocus();
            listener.onRate(rate);
            ((InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(custom.getWindowToken(), 0);
            content.requestFocus();
        } catch (IllegalArgumentException error) { custom.setError("请输入 0.25 到 5 之间的数值"); }
    }

    private LinearLayout section(String title, String hint) {
        LinearLayout heading = row();
        heading.setPadding(dp(3), dp(24), dp(3), dp(10));
        TextView name = label(title, 13, MUTED);
        name.setTypeface(null, Typeface.BOLD);
        heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(label(hint, 11, MUTED));
        content.addView(heading);
        LinearLayout group = column();
        group.setPadding(dp(16), dp(12), dp(16), dp(12));
        group.setBackground(shape(Color.WHITE, 18));
        content.addView(group, new LinearLayout.LayoutParams(-1, -2));
        return group;
    }

    private interface ToggleAction { void changed(boolean enabled); }
    private Switch toggle(LinearLayout group, String title, String hint, ToggleAction action) {
        LinearLayout line = row();
        line.setMinimumHeight(dp(68));
        LinearLayout labels = column();
        labels.addView(label(title, 15, INK));
        TextView description = label(hint, 12, MUTED);
        description.setPadding(0, dp(5), dp(8), 0);
        labels.addView(description);
        line.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch control = new Switch(getContext());
        control.setContentDescription(title);
        control.setMinimumWidth(dp(48));
        control.setMinimumHeight(dp(48));
        ColorStateList tint = new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{PINK, Color.rgb(166, 169, 179)});
        control.setThumbTintList(tint);
        control.setTrackTintList(tint);
        control.setOnCheckedChangeListener((button, enabled) -> { if (!syncing) action.changed(enabled); });
        line.addView(control);
        group.addView(line, new LinearLayout.LayoutParams(-1, -2));
        line.setBackground(ripple(Color.TRANSPARENT, 8));
        line.setOnClickListener(view -> control.setChecked(!control.isChecked()));
        return control;
    }

    private TextView settingAction(LinearLayout group, String title, String hint, String action) {
        LinearLayout line = row();
        line.setMinimumHeight(dp(68));
        line.setPadding(0, dp(10), 0, dp(10));
        LinearLayout labels = column();
        TextView name = label(title, 15, INK);
        labels.addView(name);
        TextView subtitle = label(hint, 12, MUTED);
        subtitle.setPadding(0, dp(5), dp(8), 0);
        labels.addView(subtitle);
        line.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(label("›", 25, Color.rgb(171, 174, 183)));
        line.setBackground(ripple(Color.TRANSPARENT, 8));
        line.setFocusable(true);
        line.setContentDescription(title + "，" + hint);
        line.setOnClickListener(view -> listener.onAction(action));
        group.addView(line, new LinearLayout.LayoutParams(-1, -2));
        return name;
    }

    private void divider(LinearLayout group) {
        View line = new View(getContext());
        line.setBackgroundColor(SOFT);
        group.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private LinearLayout column() { LinearLayout view = new LinearLayout(getContext()); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(getContext()); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setFontFeatureSettings("tnum");
        return view;
    }
    private Button actionButton(String text, boolean primary) {
        Button view = new Button(getContext());
        view.setText(text); view.setTextSize(14); view.setAllCaps(false);
        view.setTextColor(primary ? Color.WHITE : INK);
        view.setMinWidth(0); view.setMinimumWidth(0); view.setMinHeight(0); view.setMinimumHeight(0);
        view.setPadding(dp(4), 0, dp(4), 0);
        view.setBackground(ripple(primary ? PINK : SOFT, 11));
        return view;
    }
    private GradientDrawable shape(int color, int radius) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape; }
    private RippleDrawable ripple(int color, int radius) { return new RippleDrawable(ColorStateList.valueOf(Color.argb(28, 215, 68, 111)), shape(color, radius), shape(Color.WHITE, radius)); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int side = Math.max(dp(20), (w - dp(560)) / 2);
        content.setPadding(side, dp(18), side, dp(28));
    }
}
