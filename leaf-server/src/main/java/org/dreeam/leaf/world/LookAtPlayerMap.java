package org.dreeam.leaf.world;

import it.unimi.dsi.fastutil.objects.ObjectArrays;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.dreeam.leaf.util.KDTree3D;
import org.jspecify.annotations.Nullable;

public class LookAtPlayerMap {

    private static final ServerPlayer[] EMPTY_PLAYERS = {};
    private final KDTree3D tree = new KDTree3D();
    private ServerPlayer[] players = EMPTY_PLAYERS;

    public void tick(ServerLevel world) {
        ServerPlayer[] players = world.players().toArray(EMPTY_PLAYERS);
        final double[] pxl = new double[players.length];
        final double[] pyl = new double[players.length];
        final double[] pzl = new double[players.length];
        int i = 0;
        for (int j = 0; j < players.length; j++) {
            ServerPlayer p = players[j];
            if (p.canBeSeenByAnyone()) {
                pxl[i] = p.getX();
                pyl[i] = p.getY();
                pzl[i] = p.getZ();
                players[i] = p;
                i++;
            }
        }
        final int[] indices = new int[i];
        for (int j = 0; j < i; j++) {
            indices[j] = j;
        }
        tree.build(new double[][]{pxl, pyl, pzl}, indices);
        this.players = ObjectArrays.setLength(players, i);
    }

    public @Nullable ServerPlayer get(double x, double y, double z, double dist) {
        int i = tree.nearestIdx(x, y, z, dist < 0.0 ? 16384.0 : dist * dist);
        return i != -1 ? players[i] : null;
    }
}
