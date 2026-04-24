package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class OptimizeEntityActivation extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".optimize-entity-activation";
    }

    @Experimental
    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath(), enabled);
    }
}
