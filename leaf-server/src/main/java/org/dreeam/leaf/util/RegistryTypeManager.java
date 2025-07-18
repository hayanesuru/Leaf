package org.dreeam.leaf.util;

import net.minecraft.core.registries.BuiltInRegistries;

public final class RegistryTypeManager {

    /**
     * The total number of attributes in the Built-in Registry.
     */
    public static final int ATTRIBUTE_ID_COUNTER;

    static {
        ATTRIBUTE_ID_COUNTER = BuiltInRegistries.ATTRIBUTE.size();
    }

    private RegistryTypeManager() {}
}
