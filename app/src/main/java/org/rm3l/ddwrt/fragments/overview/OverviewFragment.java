package org.rm3l.ddwrt.fragments.overview;

import android.os.Bundle;
import android.support.annotation.Nullable;

import org.rm3l.ddwrt.fragments.AbstractBaseFragment;
import org.rm3l.ddwrt.tiles.DDWRTTile;
import org.rm3l.ddwrt.tiles.dashboard.bandwidth.WANTotalTrafficOverviewTile;
import org.rm3l.ddwrt.tiles.dashboard.network.NetworkTopologyMapTile;
import org.rm3l.ddwrt.tiles.dashboard.system.UptimeTile;

import java.util.Arrays;
import java.util.List;

/**
 * Created by rm3l on 29/08/15.
 */
public class OverviewFragment extends AbstractBaseFragment {

    @Nullable
    @Override
    protected List<DDWRTTile> getTiles(@Nullable Bundle savedInstanceState) {
        return Arrays.<DDWRTTile>asList(
                new UptimeTile(this, savedInstanceState, this.router),
                new WANTotalTrafficOverviewTile(this, savedInstanceState, this.router),
                new NetworkTopologyMapTile(this, savedInstanceState, this.router));
    }
}
