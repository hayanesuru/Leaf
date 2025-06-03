package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class OptimizeBiome extends ConfigModules {
    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".cache-mob-spawning-biome";
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config().getBoolean(getBasePath(), enabled);
    }
}
