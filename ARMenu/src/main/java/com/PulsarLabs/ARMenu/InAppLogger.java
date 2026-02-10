package com.PulsarLabs.ARMenu;

import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

/** Lightweight in-app logger to surface messages on screen when adb logcat is unavailable. */
public class InAppLogger {
    private static final int MAX_LINES = 50;
    private static final List<String> lines = new ArrayList<String>();
    private static TextView sink;
    private static Handler mainHandler;

    public static synchronized void attach(TextView view) {
        sink = view;
        mainHandler = new Handler(view.getContext().getMainLooper());
        flush();
    }

    public static synchronized void log(String msg) {
        if (msg == null) return;
        lines.add(msg);
        if (lines.size() > MAX_LINES) {
            lines.remove(0);
        }
        flush();
    }

    private static void flush() {
        if (sink == null || mainHandler == null) return;
        final StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, lines.size() - MAX_LINES); i < lines.size(); i++) {
            sb.append(lines.get(i)).append("\n");
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (sink != null) {
                    sink.setText(sb.toString());
                }
            }
        });
    }
}
