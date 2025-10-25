package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class AsyncPlayerDataSave extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-playerdata-save";
    }

    public static boolean enabled = true;
    public static boolean playerdata = false;
    public static boolean advancements = false;
    public static boolean stats = false;
    public static boolean levelData = false;
    public static boolean userList = false;
    public static boolean profileCache = true;
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
        advancements = get(getBasePath() + "advancements", advancements);
        playerdata = get(getBasePath() + "playerdata", playerdata);
        stats = get(getBasePath() + "stats", stats);
        levelData = get(getBasePath() + "level-data", levelData);
        userList = get(getBasePath() + "user-list", userList);
        profileCache = get(getBasePath() + "profile-cache", profileCache);
    }

    private boolean get(String s, boolean def) {
        return config.getBoolean(getBasePath() + '.' + s, def) && enabled;
    }
}
