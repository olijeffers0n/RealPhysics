package com.jeff.realphysics.utils;

import com.jeff.realphysics.RealPhysics;
import org.bukkit.scheduler.BukkitRunnable;

public class GarbageCollector {

    private final RealPhysics plugin;
    private final int delay;

    public GarbageCollector(RealPhysics passedPlugin, int delay){
        this.plugin = passedPlugin;
        this.delay = delay;

        main();
    }

    private void main(){
        new BukkitRunnable() {
            @Override
            public void run() {
                int time = plugin.timeUntilGarbageCollector;
                if (time == 0){
                    plugin.timeUntilGarbageCollector = delay;
                    plugin.blockIDs.clear();
                    plugin.blockIDsInAir.clear();
                    plugin.MidAirInterceptor.clearBlocker();
                    return;
                }
                plugin.timeUntilGarbageCollector = time - 1;
            }
        }.runTaskTimer(this.plugin,0L,20L);
    }
}
