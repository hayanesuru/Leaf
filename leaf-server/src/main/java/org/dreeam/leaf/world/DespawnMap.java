package org.dreeam.leaf.world;

import io.papermc.paper.configuration.WorldConfiguration;
import io.papermc.paper.configuration.type.DespawnRange;
import it.unimi.dsi.fastutil.objects.ObjectArrays;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.bukkit.event.entity.EntityRemoveEvent;

import java.util.List;

public final class DespawnMap {
    private static final ServerPlayer[] EMPTY_PLAYER_ARRAY = {};

    private ServerPlayer[] players = EMPTY_PLAYER_ARRAY;
    private double[] pos = {};

    public void prepare(ServerLevel world) {
        this.players = world.players().toArray(EMPTY_PLAYER_ARRAY);

        List<ServerPlayer> playerList = world.players();
        ServerPlayer[] list = new ServerPlayer[playerList.size()];
        int newSize = 0;
        for (ServerPlayer player1 : playerList) {
            if (EntitySelector.PLAYER_AFFECTS_SPAWNING.test(player1)) {
                list[newSize++] = player1;
            }
        }
        list = ObjectArrays.trim(list, newSize);
        this.players = list;
        this.pos = new double[this.players.length * 3];
        for (int i = 0; i < players.length; i++) {
            this.pos[i * 3] = this.players[i].getX();
            this.pos[i * 3 + 1] = this.players[i].getY();
            this.pos[i * 3 + 2] = this.players[i].getZ();
        }
    }

    public void reset() {
        this.players = EMPTY_PLAYER_ARRAY;
    }

    public void checkDespawn(Mob mob) {
        double x = mob.getX();
        double y = mob.getY();
        double z = mob.getZ();
        double distance = Double.MAX_VALUE;
        Player nearestPlayer = null;
        for (int i = 0, playersSize = players.length; i < playersSize; i++) {
            final double dx = this.pos[i * 3] - x;
            final double dy = this.pos[i * 3 + 1] - y;
            final double dz = this.pos[i * 3 + 2] - z;
            double d1 = dx * dx + dy * dy + dz * dz;
            if (d1 < distance) {
                distance = d1;
                nearestPlayer = players[i];
            }
        }
        if (nearestPlayer == null) {
            return;
        }
        Level world = mob.level();
        final WorldConfiguration.Entities.Spawning.DespawnRangePair despawnRangePair = world.paperConfig().entities.spawning.despawnRanges.get(mob.getType().getCategory());
        final DespawnRange.Shape shape = world.paperConfig().entities.spawning.despawnRangeShape;
        final double dy = Math.abs(nearestPlayer.getY() - y);
        final double dySqr = Mth.square(dy);
        final double dxSqr = Mth.square(nearestPlayer.getX() - x);
        final double dzSqr = Mth.square(nearestPlayer.getZ() - z);
        final double distanceSquared = dxSqr + dzSqr + dySqr;
        if (despawnRangePair.hard().shouldDespawn(shape, dxSqr, dySqr, dzSqr, dy) && mob.removeWhenFarAway(distanceSquared)) {
            mob.discard(EntityRemoveEvent.Cause.DESPAWN);
        } else if (despawnRangePair.soft().shouldDespawn(shape, dxSqr, dySqr, dzSqr, dy)) {
            if (mob.getNoActionTime() > 600 && mob.random.nextInt(800) == 0 && mob.removeWhenFarAway(distanceSquared)) {
                mob.discard(EntityRemoveEvent.Cause.DESPAWN);
            }
        } else {
            mob.setNoActionTime(0);
        }
    }
}
