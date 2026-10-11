package app.bilispeed.browser;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Button;
import android.widget.TextView;

/** Shared native palette. Text/background pairs meet WCAG AA for normal text. */
final class ThemeColors {
    final boolean dark;
    final int surface, ink, muted, soft, blush, accent, border, primaryText;
    ThemeColors(Context context) {
        String mode = context.getSharedPreferences("playback", 0).getString("theme", "system");
        dark = "dark".equals(mode) || "system".equals(mode)
                && (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        surface = Color.parseColor(dark ? "#191a1f" : "#ffffff");
        ink = Color.parseColor(dark ? "#eeeeF2" : "#24262e");
        muted = Color.parseColor(dark ? "#b2b4bf" : "#656775");
        soft = Color.parseColor(dark ? "#24262e" : "#f5f6f8");
        blush = Color.parseColor(dark ? "#49303c" : "#fdedf2");
        accent = Color.parseColor(dark ? "#ff91b2" : "#b52d5b");
        border = Color.parseColor(dark ? "#50525f" : "#e1e2e8");
        primaryText = dark ? Color.rgb(25, 26, 31) : Color.WHITE;
    }
    private int map(int color, ThemeColors previous, boolean background) {
        if (color == previous.accent) return accent;
        if (color == previous.ink && !background) return ink;
        if (color == previous.muted && !background) return muted;
        if (color == previous.soft) return soft;
        if (color == previous.blush) return blush;
        if (color == previous.border) return border;
        if (background && color == previous.surface) return surface;
        return color;
    }
    private void drawable(Drawable value, ThemeColors previous) {
        if (value instanceof ColorDrawable) {
            ColorDrawable color = (ColorDrawable) value; color.setColor(map(color.getColor(), previous, true));
        } else if (value instanceof GradientDrawable) {
            GradientDrawable shape = (GradientDrawable) value;
            if (shape.getColor() != null) shape.setColor(map(shape.getColor().getDefaultColor(), previous, true));
            shape.setStroke(1, border);
        } else if (value instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) value;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) drawable(layers.getDrawable(i), previous);
        }
    }
    void apply(View view, ThemeColors previous) {
        drawable(view.getBackground(), previous);
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            int color = text.getCurrentTextColor();
            text.setTextColor(map(color, previous, false));
            text.setHintTextColor(muted);
            if (view instanceof Button && color == previous.primaryText) text.setTextColor(primaryText);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) apply(group.getChildAt(i), previous);
        }
    }
    ColorStateList accentList() { return ColorStateList.valueOf(accent); }

    void dialog(Window window) {
        if (window == null) return;
        GradientDrawable background=new GradientDrawable();
        background.setColor(surface);
        background.setCornerRadius(16*window.getContext().getResources().getDisplayMetrics().density);
        window.setBackgroundDrawable(background);
        window.setStatusBarColor(surface);window.setNavigationBarColor(surface);
        if(android.os.Build.VERSION.SDK_INT>=29)window.setNavigationBarContrastEnforced(false);
        if(android.os.Build.VERSION.SDK_INT>=30 && window.getInsetsController()!=null) {
            int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            window.getInsetsController().setSystemBarsAppearance(dark?0:mask,mask);
        }
        dialogViews(window.getDecorView());
    }

    private void dialogViews(View view) {
        if("bilispeed-media-preview".equals(view.getTag()))return;
        if(view instanceof TextView) {
            TextView text=(TextView)view;
            text.setTextColor(view instanceof Button?accent:ink);
            text.setHintTextColor(muted);
            if(view instanceof EditText && !(view.getBackground() instanceof GradientDrawable))view.setBackgroundTintList(accentList());
            if(view instanceof CheckedTextView)((CheckedTextView)view).setCheckMarkTintList(accentList());
        }
        if(view instanceof ProgressBar) {
            ((ProgressBar)view).setProgressTintList(accentList());
            ((ProgressBar)view).setIndeterminateTintList(accentList());
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)dialogViews(((ViewGroup)view).getChildAt(i));
    }
}
