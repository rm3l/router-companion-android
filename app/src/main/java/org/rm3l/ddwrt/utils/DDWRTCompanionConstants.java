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

import org.rm3l.ddwrt.BuildConfig;

/**
 * App Constants
 */
public final class DDWRTCompanionConstants {

    //FIXME Consider increasing this value prior to release
    public static final long TILE_REFRESH_MILLIS = 30 * 1000l;

    //This is only used to check feedback submitted by end-users
    public static final String PUBKEY = \"fake-key\";
            "AY5ab5Nbu" +
            "6fMj7xRnc" +
            "dGgoNSvYM" +
            "BT6B42r2p" +
            "bp/mABgAz" +
            "8" +
            "I";

    //FIXME Update prior to release
    public static final boolean TEST_MODE = false;
    public static final long MAX_PRIVKEY_SIZE_BYTES = 300 * 1024l;
    public static final String SYNC_INTERVAL_MILLIS_PREF = "syncIntervalMillis";
    public static final String SORTING_STRATEGY_PREF = "sortingStrategy";
    public static final String EMPTY_STRING = "";
    public static final String ALWAYS_CHECK_CONNECTION_PREF_KEY = \"fake-key\";

    public String[] mMonth = new String[] {
            "Jan", "Feb" , "Mar", "Apr", "May", "Jun",
            "Jul", "Aug" , "Sep", "Oct", "Nov", "Dec"
    };

    public static final String DEFAULT_SHARED_PREFERENCES_KEY = \"fake-key\";

    private DDWRTCompanionConstants() {
    }
}
