package com.example.marketplace.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Spawns a non-networked Paper {@link Player} for automated self-tests.
 * The entity is not fully joined (no connection), but inventory + Vault UUID work.
 */
public final class FakePlayerFactory {
    private FakePlayerFactory() {
    }

    public static Player spawn(String name, Logger logger) {
        try {
            World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (world == null) {
                return null;
            }
            Location loc = world.getSpawnLocation();
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));

            Object craftServer = Bukkit.getServer();
            Object minecraftServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);
            Object serverLevel = world.getClass().getMethod("getHandle").invoke(world);

            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Object profile = gameProfileClass
                .getConstructor(UUID.class, String.class)
                .newInstance(uuid, name);

            Class<?> clientInfoClass = Class.forName("net.minecraft.server.level.ClientInformation");
            Object clientInfo = clientInfoClass.getMethod("createDefault").invoke(null);

            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
            Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");

            Constructor<?> ctor = serverPlayerClass.getConstructor(
                minecraftServerClass,
                serverLevelClass,
                gameProfileClass,
                clientInfoClass
            );
            Object serverPlayer = ctor.newInstance(minecraftServer, serverLevel, profile, clientInfo);

            Method getBukkitEntity = serverPlayerClass.getMethod("getBukkitEntity");
            Player player = (Player) getBukkitEntity.invoke(serverPlayer);
            try {
                player.teleport(loc);
            } catch (Throwable ignored) {
                // teleport may require a connection on some builds
            }

            if (logger != null) {
                logger.info("FakePlayer spawned: " + name + " / " + uuid);
            }
            return player;
        } catch (Throwable t) {
            if (logger != null) {
                logger.warning("FakePlayer spawn failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                t.printStackTrace();
            }
            return null;
        }
    }

    public static void despawn(Player player, Logger logger) {
        if (player == null) {
            return;
        }
        try {
            player.kick(net.kyori.adventure.text.Component.text("selftest-end"));
        } catch (Throwable t) {
            try {
                Method remove = player.getClass().getMethod("remove");
                remove.invoke(player);
            } catch (Throwable ignored) {
            }
        }
        if (logger != null) {
            logger.info("FakePlayer despawned: " + player.getName());
        }
    }
}
