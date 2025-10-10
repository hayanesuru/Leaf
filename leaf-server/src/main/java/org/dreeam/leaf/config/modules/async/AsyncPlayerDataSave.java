package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class AsyncPlayerDataSave extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-playerdata-save";
    }

    public static boolean enabled = false;
    public static boolean playerdata = false;
    public static boolean advancements = false;
    public static boolean stats = false;
    public static boolean levelData = false;
    public static boolean userList = false;
    private static boolean asyncPlayerDataSavingInitialized;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Asynchronously save player data.""",
            """
                异步保存玩家数据.""");

        if (asyncPlayerDataSavingInitialized) {
            config.getConfigSection(getBasePath());
            return;
        }
        asyncPlayerDataSavingInitialized = true;

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        boolean advancements = config.getBoolean(getBasePath() + ".advancements", false);
        boolean playerdata = config.getBoolean(getBasePath() + ".playerdata", false);
        boolean stats = config.getBoolean(getBasePath() + ".stats", false);
        boolean levelData = config.getBoolean(getBasePath() + ".level-data", false);
        boolean userList = config.getBoolean(getBasePath() + ".user-list", false);
        AsyncPlayerDataSave.advancements = enabled && advancements;
        AsyncPlayerDataSave.playerdata = enabled && playerdata;
        AsyncPlayerDataSave.stats = enabled && stats;
        AsyncPlayerDataSave.levelData = enabled && levelData;
        AsyncPlayerDataSave.userList = enabled && userList;

        org.dreeam.leaf.async.AsyncPlayerDataSaving.init();
    }
}
