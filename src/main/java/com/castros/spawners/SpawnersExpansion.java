package com.castros.spawners;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Registra %spawners_estrellas% en PlaceholderAPI. */
public final class SpawnersExpansion extends PlaceholderExpansion {

    private final SpawnerPlugin plugin;

    public SpawnersExpansion(SpawnerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "spawners";
    }

    @Override
    public @NotNull String getAuthor() {
        return "castros";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /** Que PlaceholderAPI no la quite al hacer /papi reload. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, @NotNull String params) {
        if (offline == null) return "";
        if (params.equalsIgnoreCase("estrellas")) {
            Player p = offline.getPlayer();
            return p == null ? "0" : String.valueOf(plugin.saldo(p));
        }
        return null;
    }
}
