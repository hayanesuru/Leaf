package net.caffeinemc.mods.lithium.common.entity;

public interface EquipmentInfo {

    boolean shouldTickEnchantments();

    boolean hasUnsentEquipmentChanges();

    void onEquipmentChangesSent();
}
