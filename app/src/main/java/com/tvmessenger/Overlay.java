package com.tvmessenger;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * The text box in the middle of the screen. It never takes focus or touches, so the remote keeps driving
 * whatever is on screen. Main thread only.
 */
final class Overlay {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static TextView view;
    private static Runnable pendingHidden;
    private static final Runnable HIDE = Overlay::hide;

    private Overlay() {}

    static void show(Context c, String text, long millis, Runnable onHidden) {
        hide();
        Context app = c.getApplicationContext();
        WindowManager wm = app.getSystemService(WindowManager.class);
        float density = app.getResources().getDisplayMetrics().density;
        int screenWidth = app.getResources().getDisplayMetrics().widthPixels;

        TextView t = new TextView(app);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp(text));
        int pad = (int) (24 * density);
        t.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE0101418);
        bg.setCornerRadius(20 * density);
        bg.setStroke((int) (2 * density), 0x55FFFFFF);
        t.setBackground(bg);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                (int) (screenWidth * 0.60f),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;

        try {
            wm.addView(t, lp);
        } catch (RuntimeException e) {
            // No overlay permission (granted with appops at install), or the window was refused: nothing to show.
            if (onHidden != null) onHidden.run();
            return;
        }
        view = t;
        pendingHidden = onHidden;
        MAIN.postDelayed(HIDE, millis);
    }

    static void hide() {
        MAIN.removeCallbacks(HIDE);
        if (view != null) {
            try {
                view.getContext().getSystemService(WindowManager.class).removeView(view);
            } catch (RuntimeException ignored) {
            }
            view = null;
        }
        Runnable done = pendingHidden;
        pendingHidden = null;
        if (done != null) done.run();
    }

    /** Large for a few words, smaller as the message grows, so long texts still fit the box. */
    static float textSizeSp(String text) {
        int chars = text.codePointCount(0, text.length());
        if (chars <= 40) return 40;
        if (chars <= 120) return 32;
        if (chars <= 300) return 26;
        return 22;
    }
}
