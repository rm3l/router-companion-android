package org.rm3l.ddwrt.utils;

import android.content.Context;
import android.content.pm.ShortcutManager;
import android.os.Build;
import android.support.annotation.Nullable;

import com.crashlytics.android.Crashlytics;

/**
 * Created by rm3l on 02/12/2016.
 */

public final class AppShortcutUtils {

    private AppShortcutUtils() {}

    public static void reportShortcutUsed(@Nullable final Context ctx, final String shortcutId) {
        if (ctx == null) {
            return;
        }
        try {
            //#199: report shortcut
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                final ShortcutManager shortcutManager = ctx.getSystemService(ShortcutManager.class);
                /*
                Applications that publish shortcuts should call this method
                whenever the user selects the shortcut containing the given ID or
                when the user completes an action in the application that is
                equivalent to selecting the shortcut
                 */
                shortcutManager.reportShortcutUsed(shortcutId);
            }
        } catch (final Exception e) {
            //No worries
            Crashlytics.logException(e);
        }
    }
}
