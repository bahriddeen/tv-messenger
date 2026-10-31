package com.tvmessenger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The text box in the middle of the screen. It never takes focus or touches, so the remote keeps driving
 * whatever is on screen. Main thread only.
 */
final class Overlay {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static View view;
    private static Runnable pendingHidden;
    private static final Runnable HIDE = Overlay::hide;

    private Overlay() {}

    static void show(Context c, String text, long millis, Runnable onHidden) {
        Context app = c.getApplicationContext();
        TextView t = textView(app, text, textSizeSp(text));
        t.setBackground(box(app));
        int pad = dp(app, 24);
        t.setPadding(pad, pad, pad, pad);
        add(app, t, millis, onHidden);
    }

    /** The photo fitted into the box, the caption (if any) under it as typed. */
    static void showPhoto(Context c, Bitmap photo, String caption, long millis, Runnable onHidden) {
        Context app = c.getApplicationContext();
        LinearLayout column = new LinearLayout(app);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackground(box(app));
        int pad = dp(app, 20);
        column.setPadding(pad, pad, pad, pad);

        ImageView image = new ImageView(app);
        image.setImageBitmap(photo);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setMaxHeight((int) (app.getResources().getDisplayMetrics().heightPixels * 0.62f));
        column.addView(image, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        if (caption != null && !caption.isEmpty()) {
            TextView t = textView(app, caption, Math.min(28, textSizeSp(caption)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(app, 16);
            column.addView(t, lp);
        }
        add(app, column, millis, onHidden);
    }

    private static TextView textView(Context app, String text, float sizeSp) {
        TextView t = new TextView(app);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.START); // the text keeps the sender's own line breaks and spacing
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        return t;
    }

    private static GradientDrawable box(Context app) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE0101418);
        bg.setCornerRadius(dp(app, 20));
        bg.setStroke(dp(app, 2), 0x55FFFFFF);
        return bg;
    }

    private static void add(Context app, View v, long millis, Runnable onHidden) {
        hide();
        int screenWidth = app.getResources().getDisplayMetrics().widthPixels;
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
            app.getSystemService(WindowManager.class).addView(v, lp);
        } catch (RuntimeException e) {
            // No overlay permission (granted with appops at install), or the window was refused: nothing to show.
            if (onHidden != null) onHidden.run();
            return;
        }
        view = v;
        pendingHidden = onHidden;
        MAIN.postDelayed(HIDE, millis);
    }

    private static int dp(Context app, int value) {
        return (int) (value * app.getResources().getDisplayMetrics().density);
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
