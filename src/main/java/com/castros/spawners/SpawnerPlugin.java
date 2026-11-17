package com.castros.spawners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SpawnerPlugin extends JavaPlugin {

    public record Nivel(int cantidad, int delayMin, int delayMax, int maxCercanos, int costo,
                        double estrellaProb, int estrellaMax, double lootProb, int lootMult) {}

    public NamespacedKey kNivel, kActivo, kDueno, kAmigos, kGenerados, kMobNivel, kEstrella;

    /** Mobs generados que aun no se han guardado en el spawner (se guardan cada 5 min, al abrir el menu y al apagar). */
    public final Map<String, Integer> pendientes = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        kNivel = new NamespacedKey(this, "nivel");
        kActivo = new NamespacedKey(this, "activo");
        kDueno = new NamespacedKey(this, "dueno");
        kAmigos = new NamespacedKey(this, "amigos");
        kGenerados = new NamespacedKey(this, "generados");
        kMobNivel = new NamespacedKey(this, "mob_nivel");
        kEstrella = new NamespacedKey(this, "estrella");

        getServer().getPluginManager().registerEvents(new SpawnerListener(this), this);
        SpawnerCommand cmd = new SpawnerCommand(this);
        getCommand("spawner").setExecutor(cmd);
        getCommand("spawner").setTabCompleter(cmd);
        getServer().getScheduler().runTaskTimer(this, this::guardarTodo, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        guardarTodo();
    }

    // ---------- Texto e items ----------

    public static Component c(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static ItemStack item(Material m, String nombre, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(c(nombre));
        if (lore.length > 0) {
            im.lore(Arrays.stream(lore).map(SpawnerPlugin::c).toList());
        }
        it.setItemMeta(im);
        return it;
    }

    public void msg(Player p, String texto) {
        p.sendMessage(c("&8[&6Spawner&8] &7" + texto));
    }

    public static String nombreMob(EntityType t) {
        String n = t.getKey().getKey().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    // ---------- Niveles ----------

    public int maxNivel() {
        ConfigurationSection s = getConfig().getConfigurationSection("niveles");
        return s == null ? 1 : s.getKeys(false).size();
    }

    public Nivel nivel(int n) {
        n = Math.max(1, Math.min(n, maxNivel()));
        ConfigurationSection s = getConfig().getConfigurationSection("niveles." + n);
        if (s == null) return new Nivel(2, 400, 800, 8, 0, 0.2, 1, 0, 1);
        return new Nivel(
                s.getInt("cantidad", 1),
                s.getInt("delay-min", 200),
                s.getInt("delay-max", 800),
                s.getInt("max-cercanos", 6),
                s.getInt("costo", 0),
                s.getDouble("estrella-prob", 0.1),
                Math.max(1, s.getInt("estrella-max", 1)),
                s.getDouble("loot-prob", 0),
                Math.max(1, s.getInt("loot-mult", 1)));
    }

    // ---------- Datos guardados en el spawner ----------

    public int getNivel(PersistentDataContainer d) {
        return d.getOrDefault(kNivel, PersistentDataType.INTEGER, 1);
    }

    public boolean activo(PersistentDataContainer d) {
        return d.getOrDefault(kActivo, PersistentDataType.BYTE, (byte) 1) == 1;
    }

    public UUID dueno(PersistentDataContainer d) {
        String s = d.get(kDueno, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public List<String> amigos(PersistentDataContainer d) {
        String s = d.getOrDefault(kAmigos, PersistentDataType.STRING, "");
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }

    public void setAmigos(PersistentDataContainer d, List<String> lista) {
        d.set(kAmigos, PersistentDataType.STRING, String.join(",", lista));
    }

    /** Aplica los valores del nivel (y el estado encendido/apagado) al spawner. Hay que llamar a cs.update() despues. */
    public void aplicar(CreatureSpawner cs) {
        PersistentDataContainer d = cs.getPersistentDataContainer();
        Nivel n = nivel(getNivel(d));
        boolean on = activo(d);
        cs.setSpawnCount(n.cantidad());
        cs.setMaxSpawnDelay(Math.max(n.delayMax(), cs.getMinSpawnDelay()));
        cs.setMinSpawnDelay(n.delayMin());
        cs.setMaxSpawnDelay(n.delayMax());
        cs.setMaxNearbyEntities(n.maxCercanos());
        cs.setRequiredPlayerRange(on ? getConfig().getInt("rango-activacion", 16) : 0);
    }

    // ---------- Items: spawner y estrella ----------

    public ItemStack crearSpawner(EntityType tipo, int nivel, int generados) {
        ItemStack it = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) it.getItemMeta();
        CreatureSpawner cs = (CreatureSpawner) meta.getBlockState();
        cs.setSpawnedType(tipo);
        PersistentDataContainer d = cs.getPersistentDataContainer();
        d.set(kNivel, PersistentDataType.INTEGER, nivel);
        d.set(kActivo, PersistentDataType.BYTE, (byte) 1);
        d.set(kGenerados, PersistentDataType.INTEGER, generados);
        meta.setBlockState(cs);
        String nombre = nombreMob(tipo);
        meta.displayName(c("&6Spawner de " + nombre + " &7[Nivel " + nivel + "]"));
        meta.lore(List.of(
                c("&7Mob: &f" + nombre),
                c("&7Nivel: &e" + nivel + "&7/&e" + maxNivel()),
                c("&8Colócalo y haz clic derecho para abrir el menú")));
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack crearEstrella(int cantidad) {
        ItemStack it = new ItemStack(Material.NETHER_STAR, cantidad);
        ItemMeta im = it.getItemMeta();
        im.displayName(c(getConfig().getString("estrella.nombre", "&e✦ Estrella de Spawner")));
        im.lore(List.of(c("&7Moneda para mejorar spawners")));
        im.getPersistentDataContainer().set(kEstrella, PersistentDataType.BYTE, (byte) 1);
        im.setEnchantmentGlintOverride(true);
        it.setItemMeta(im);
        return it;
    }

    public boolean esEstrella(ItemStack it) {
        return it != null && it.getType() == Material.NETHER_STAR && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(kEstrella, PersistentDataType.BYTE);
    }

    public int contarEstrellas(Player p) {
        int total = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (esEstrella(s)) total += s.getAmount();
        }
        return total;
    }

    public void quitarEstrellas(Player p, int cantidad) {
        int falta = cantidad;
        ItemStack[] cont = p.getInventory().getStorageContents();
        for (int i = 0; i < cont.length && falta > 0; i++) {
            if (esEstrella(cont[i])) {
                int q = Math.min(falta, cont[i].getAmount());
                falta -= q;
                if (q == cont[i].getAmount()) cont[i] = null;
                else cont[i].setAmount(cont[i].getAmount() - q);
            }
        }
        p.getInventory().setStorageContents(cont);
    }

    public void entregar(Player p, ItemStack it) {
        p.getInventory().addItem(it).values()
                .forEach(resto -> p.getWorld().dropItemNaturally(p.getLocation(), resto));
    }

    // ---------- Estadisticas (mobs generados) ----------

    public String key(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    public int generadosTotal(Block b, PersistentDataContainer d) {
        return d.getOrDefault(kGenerados, PersistentDataType.INTEGER, 0)
                + pendientes.getOrDefault(key(b.getLocation()), 0);
    }

    public void guardar(Block b) {
        Integer extra = pendientes.remove(key(b.getLocation()));
        if (extra == null || b.getType() != Material.SPAWNER) return;
        CreatureSpawner cs = (CreatureSpawner) b.getState();
        PersistentDataContainer d = cs.getPersistentDataContainer();
        d.set(kGenerados, PersistentDataType.INTEGER,
                d.getOrDefault(kGenerados, PersistentDataType.INTEGER, 0) + extra);
        cs.update(true, false);
    }

    public void guardarTodo() {
        for (String k : new ArrayList<>(pendientes.keySet())) {
            String[] a = k.split(";");
            World w = Bukkit.getWorld(a[0]);
            if (w == null) {
                pendientes.remove(k);
                continue;
            }
            int x = Integer.parseInt(a[1]), y = Integer.parseInt(a[2]), z = Integer.parseInt(a[3]);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            guardar(w.getBlockAt(x, y, z));
        }
    }
}
