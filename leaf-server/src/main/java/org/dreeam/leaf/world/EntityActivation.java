package org.dreeam.leaf.world;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup;
import io.papermc.paper.entity.activation.ActivationType;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.dreeam.leaf.config.modules.opt.DynamicActivationofBrain;
import org.dreeam.leaf.util.KDTree2D;
import org.dreeam.leaf.util.KDTree3D;

public final class EntityActivation {
    private static final ActivationType[] ACTIVATION_TYPES = ActivationType.values();
    private static final ServerPlayer[] EMPTY_PLAYERS = {};

    private final ObjectArrayList<Entity> entityListCache = new ObjectArrayList<>();
    private final LongOpenHashSet chunks = new LongOpenHashSet();
    private final KDTree2D kdTree2 = new KDTree2D();
    private final KDTree3D kdTree3 = new KDTree3D();

    public void activateEntities(ServerLevel world) {
        final int miscActivationRange = world.spigotConfig.miscActivationRange;
        final int raiderActivationRange = world.spigotConfig.raiderActivationRange;
        final int animalActivationRange = world.spigotConfig.animalActivationRange;
        final int monsterActivationRange = world.spigotConfig.monsterActivationRange;
        final int waterActivationRange = world.spigotConfig.waterActivationRange;
        final int flyingActivationRange = world.spigotConfig.flyingMonsterActivationRange;
        final int villagerActivationRange = world.spigotConfig.villagerActivationRange;
        world.wakeupInactiveRemainingAnimals = Math.min(world.wakeupInactiveRemainingAnimals + 1, world.spigotConfig.wakeUpInactiveAnimals);
        world.wakeupInactiveRemainingVillagers = Math.min(world.wakeupInactiveRemainingVillagers + 1, world.spigotConfig.wakeUpInactiveVillagers);
        world.wakeupInactiveRemainingMonsters = Math.min(world.wakeupInactiveRemainingMonsters + 1, world.spigotConfig.wakeUpInactiveMonsters);
        world.wakeupInactiveRemainingFlying = Math.min(world.wakeupInactiveRemainingFlying + 1, world.spigotConfig.wakeUpInactiveFlying);

        int maxRange = Math.max(monsterActivationRange, animalActivationRange);
        maxRange = Math.max(maxRange, raiderActivationRange);
        maxRange = Math.max(maxRange, miscActivationRange);
        maxRange = Math.max(maxRange, flyingActivationRange);
        maxRange = Math.max(maxRange, waterActivationRange);
        maxRange = Math.max(maxRange, villagerActivationRange);
        maxRange = Math.min((world.spigotConfig.simulationDistance << 4) - 8, maxRange);

        final double[] ranges = new double[ACTIVATION_TYPES.length];
        ranges[ActivationType.WATER.ordinal()] = waterActivationRange;
        ranges[ActivationType.FLYING_MONSTER.ordinal()] = flyingActivationRange;
        ranges[ActivationType.VILLAGER.ordinal()] = villagerActivationRange;
        ranges[ActivationType.MONSTER.ordinal()] = monsterActivationRange;
        ranges[ActivationType.ANIMAL.ordinal()] = animalActivationRange;
        ranges[ActivationType.RAIDER.ordinal()] = raiderActivationRange;
        ranges[ActivationType.MISC.ordinal()] = miscActivationRange;
        for (int i = 0; i < ranges.length; i++) {
            if (ranges[i] > 0.0) {
                ranges[i] = ranges[i] * ranges[i];
            }
        }

        final long currentTick = MinecraftServer.currentTick; // cast to long
        final ObjectArrayList<Entity> entities = this.entityListCache;
        final LongOpenHashSet chunks = this.chunks;
        final ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup lookup = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel) world).moonrise$getEntityLookup();
        final KDTree2D kdTree2 = this.kdTree2;
        final KDTree3D kdTree3 = this.kdTree3;
        final boolean tickMarkers = world.paperConfig().entities.markers.tick;
        int playerSize = 0;
        final ServerPlayer[] players = world.players().toArray(EMPTY_PLAYERS);
        final double[] pxl = new double[players.length];
        final double[] pyl = new double[players.length];
        final double[] pzl = new double[players.length];
        for (int i = 0; i < players.length; i++) {
            final ServerPlayer p = players[i];
            p.activatedTick = currentTick;
            if (world.spigotConfig.ignoreSpectatorActivation && p.isSpectator()) {
                continue;
            }
            if (!world.purpurConfig.idleTimeoutTickNearbyEntities && p.isAfk()) {
                continue; // Purpur - AFK API
            }
            players[playerSize] = p;
            pxl[playerSize] = p.getX();
            pyl[playerSize] = p.getY();
            pzl[playerSize] = p.getZ();
            playerSize++;
        }

        final int[] indices = new int[playerSize];
        for (int k = 0; k < playerSize; k++) {
            indices[k] = k;
        }
        kdTree2.build(new double[][]{pxl, pzl}, indices);
        for (int k = 0; k < playerSize; k++) {
            indices[k] = k;
        }
        kdTree3.build(new double[][]{pxl, pyl, pzl}, indices);

        final double worldHeight = world.getHeight();
        getEntities(world, players, playerSize, maxRange, worldHeight, lookup, chunks, entities);
        final Object[] raw = entities.elements();
        final int size = entities.size();

        if (size != 0 && playerSize != 0) {
            final boolean dab = DynamicActivationofBrain.enabled;
            final boolean dontEnableIfInWater = DynamicActivationofBrain.dontEnableIfInWater;
            final int startSq = DynamicActivationofBrain.startDistanceSquared;
            final int distMod = DynamicActivationofBrain.activationDistanceMod;
            final int maxPrio = DynamicActivationofBrain.maximumActivationPrio;
            activateEntities(size, raw, tickMarkers, currentTick, ranges, kdTree2, dab, dontEnableIfInWater, kdTree3, startSq, distMod, maxPrio);
        }
        entities.clear();
        chunks.clear();
    }

    private static void activateEntities(int size, Object[] entities, boolean tickMarkers, long currentTick, double[] ranges, KDTree2D kdTree2, boolean dab, boolean dontEnableIfInWater, KDTree3D kdTree3, int startSq, int distMod, int maxPrio) {
        for (int i = 0; i < size; i++) {
            final Entity entity = (Entity) entities[i];
            if (!tickMarkers && entity instanceof Marker) {
                continue;
            }
            if (currentTick <= entity.activatedTick) {
                continue;
            }
            final Vec3 p = entity.position;
            if (entity.defaultActivationState) {
                entity.activatedTick = currentTick;
            } else {
                final double max = ranges[entity.activationType.ordinal()];
                final double near = kdTree2.nearestSqr(p.x, p.z, max);
                if (near != max) {
                    entity.activatedTick = currentTick;
                }
            }
            final int a;
            if (dab
                && entity.getType().dabEnabled
                && (!dontEnableIfInWater || !entity.isInWater() || (entity instanceof WaterAnimal || (entity instanceof final LivingEntity livingEntity && livingEntity.canBreatheUnderwater())))) {
                final int distSq = (int) kdTree3.nearestSqr(p.x, p.y, p.z, 16384.0);
                a = distSq > startSq ?
                    Math.max(1, Math.min(distSq >> distMod, maxPrio)) :
                    1;
            } else {
                a = 1;
            }
            entity.activatedPriority = a;
        }
    }

    private static void getEntities(ServerLevel world, Player[] players, int playerSize, int maxRange, double worldHeight, EntityLookup lookup, LongOpenHashSet chunks, ObjectArrayList<Entity> entities) {
        for (int k = 0; k < playerSize; k++) {
            final Player player = players[k];
            final AABB box = player.getBoundingBox().inflate(maxRange, worldHeight, maxRange);
            lookup.leaf$getEntities(box, chunks, entities);
            ca.spottedleaf.moonrise.common.PlatformHooks.get().addToGetEntities(world, null, box, null, entities);
        }
    }
}
