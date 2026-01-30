package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class IceAndSnowChance extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName() + ".ice-and-snow-chance";
    }

    public static int iceAndSnowChance = 48 * 8;

    @Override
    public void onLoaded() {
        iceAndSnowChance = config.getInt(getBasePath(), 48 * 8);
        if (iceAndSnowChance < 0) {
            iceAndSnowChance = 48;
        }
    }
}
