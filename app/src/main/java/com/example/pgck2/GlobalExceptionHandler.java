package com.example.pgck2;

import android.content.Context;
import android.util.Log;
import java.io.PrintWriter;
import java.io.StringWriter;

public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {
    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final Context context;

    public GlobalExceptionHandler(Context context) {
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        this.context = context;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        Log.e("FATAL_APP_CRASH", "Uncaught Exception in thread " + thread.getName(), throwable);
        Log.e("FATAL_APP_CRASH", sw.toString());

        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        } else {
            System.exit(2);
        }
    }
}
