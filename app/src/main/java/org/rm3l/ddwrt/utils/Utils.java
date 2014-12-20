/*
 * DD-WRT Companion is a mobile app that lets you connect to,
 * monitor and manage your DD-WRT routers on the go.
 *
 * Copyright (C) 2014  Armel Soro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact Info: Armel Soro <apps+ddwrt@rm3l.org>
 */

package org.rm3l.ddwrt.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rm3l.ddwrt.donate.DonateActivity;
import org.rm3l.ddwrt.resources.conn.Router;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

/**
 * General utilities
 */
public final class Utils {

    public static final String TAG = Utils.class.getSimpleName();

    private static int nextLoaderId = 1;

    private Utils() {
    }

    public static int getNextLoaderId() {
        return nextLoaderId++;
    }

    public static void readAll(@NotNull BufferedReader bufferedReader, @NotNull StringBuffer result) throws IOException {
        for (String line; (line = bufferedReader.readLine()) != null; ) {
            result.append(line);
        }
    }

    @Nullable
    public static String[] getLines(@NotNull BufferedReader bufferedReader) throws IOException {
        final List<String> lines = Lists.newArrayList();
        for (String line; (line = bufferedReader.readLine()) != null; ) {
            lines.add(line);
        }
        return lines.toArray(new String[lines.size()]);
    }

    @NotNull
    public static AlertDialog buildAlertDialog(@NotNull final Context context, @Nullable final String title, @NotNull final String msg,
                                               final boolean cancelable, final boolean cancelableOnTouchOutside) {
        @NotNull final AlertDialog alertDialog = new AlertDialog.Builder(context).create();
        if (!Strings.isNullOrEmpty(title)) {
            alertDialog.setTitle(title);
        }
        alertDialog.setMessage(msg);
        alertDialog.setCancelable(cancelable);
        alertDialog.setCanceledOnTouchOutside(cancelableOnTouchOutside);

        return alertDialog;
    }

    public static void openDonateActivity(@NotNull final Context context) {
        final Intent donateIntent = new Intent(context, DonateActivity.class);
        context.startActivity(donateIntent);
    }

    @NotNull
    public static String intToIp(final int i) {

        return (i & 0xFF) + "." +
                ((i >> 8) & 0xFF) + "." +
                ((i >> 16) & 0xFF) + "." +
                ((i >> 24) & 0xFF);
    }

    @NotNull
    public static List<Router> dbIdsToPosition(@NotNull final List<Router> routersList) {
        final List<Router> routers = Lists.newArrayListWithCapacity(routersList.size());

        int i = 0;
        for (final Router router : routersList) {
            @NotNull final Router r = new Router(router);
            r.setId(i++);
            routers.add(r);
        }
        return routers;
    }

    public static String toHumanReadableByteCount(final long sizeInBytes) {
        return FileUtils.byteCountToDisplaySize(sizeInBytes);
    }

}
