// SPDX-License-Identifier: GPL-3.0-only
package org.budikos.control;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: on = performance, off = battery or balanced; subtitle = current profile. */
public class PerfTileService extends TileService {

    @Override
    public void onStartListening() {
        update(Perf.profile());
    }

    @Override
    public void onClick() {
        String next = Perf.next(Perf.profile());
        Perf.setProfile(next);
        update(next);
    }

    private void update(String p) {
        Tile t = getQsTile();
        if (t == null) return;
        t.setLabel(getString(R.string.tile_label));
        t.setSubtitle(getString(Perf.profileLabel(p)));
        t.setState(Perf.PERFORMANCE.equals(p) ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
