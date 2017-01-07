package org.rm3l.router_companion.help;

import android.support.annotation.NonNull;

import org.rm3l.router_companion.R;
import org.rm3l.router_companion.RouterCompanionAppConstants;
import org.rm3l.router_companion.web.WebActivity;

/**
 * Created by rm3l on 30/05/15.
 */
public class HelpActivity extends WebActivity {

    @Override
    protected CharSequence getTitleStr() {
        return null;
    }

    @Override
    protected int getTitleResId() {
        return R.string.help;
    }

    @NonNull
    @Override
    public String getUrl() {
        return RouterCompanionAppConstants.REMOTE_HELP_WEBSITE;
    }


}