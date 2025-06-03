package org.dreeam.leaf.config.modules.opt;

import net.minecraft.world.entity.MobCategory;
import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class ThrottleNaturalSpawnMob extends ConfigModules {
    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".throttled-mob-spawning";
    }

    public static boolean enabled = false;
    public static long[] failedAttempts;
    public static int[] spawnChance;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        MobCategory[] categories = MobCategory.values();
        failedAttempts = new long[categories.length];
        spawnChance = new int[categories.length];
        for (int i = 0; i < categories.length; i++) {
            String category = getBasePath() + "." + categories[i].getSerializedName();
            failedAttempts[i] = config.getLong(category + ".failed-attempts", -1);
            spawnChance[i] = (int) Math.round(config.getDouble(category + ".spawn-chance", 100.0) * 10.24);
        }
    }
}
