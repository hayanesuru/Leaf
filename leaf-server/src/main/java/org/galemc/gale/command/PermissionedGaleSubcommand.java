// Gale - Gale commands

package org.galemc.gale.command;

import org.jetbrains.annotations.Nullable;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

public abstract class PermissionedGaleSubcommand implements GaleSubcommand {

    public final Permission permission;

    protected PermissionedGaleSubcommand(Permission permission) {
        this.permission = permission;
    }

    protected PermissionedGaleSubcommand(String permission, PermissionDefault permissionDefault) {
        this(new Permission(permission, permissionDefault));
    }

    @Override
    public boolean testPermission(CommandSender sender) {
        return sender.hasPermission(this.permission);
    }

    @Override
    public @Nullable Permission getPermission() {
        return this.permission;
    }

}
