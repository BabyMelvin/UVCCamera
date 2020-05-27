package com.silead.tof.util;

import android.util.Log;

public class SDLog {
    private static final String TAG = "SDLog";
    private static boolean mDebug = true;

    private SDLog() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    public static void v(String tag, String info) {
        if (mDebug)
            Log.v(tag, "silead: " + info);
    }

    public static void d(String tag, String info) {
        if (mDebug)
            Log.d(tag, "silead: " + info);
    }
    public static void i(String tag, String info) {
        if (mDebug)
            Log.i(tag, "silead: " + info);
    }
    public static void e(String tag, String info) {
            Log.e(tag, "silead: " + info);
    }
}
