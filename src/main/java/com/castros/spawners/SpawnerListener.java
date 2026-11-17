package com.castros.spawners;

import com.castros.spawners.SpawnerPlugin.Nivel;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import static com.castros.spawners.SpawnerPlugin.c;
import static com.castros.spawners.SpawnerPlugin.item;

public class SpawnerListener implements Listener {

    private final SpawnerPlugin plugin;
    private final Random rnd = new Random();

    public SpawnerListener(SpawnerPlugin plugin) {
        this.plugin = plugin;
    }

    static class Menu implements InventoryHolder {
        final Location loc;
        Inventory inv;

        Menu(Location loc) {
            this.loc = loc;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // ---------- Permisos ----------

    private boolean esDueno(Player p, PersistentDataContainer d) {
        UUID du = plugin.dueno(d);
        return p.hasPermission("spawner.admin") || du == null || du.equals(p.getUniqueId());
    }

    private boolean puede(Player p, PersistentDataContainer d) {
        return esDueno(p, d) || plugin.amigos(d).contains(p.getUniqueId().toString());
    }

    private String nombreDueno(UUID u) {
        if (u == null) return "Nadie";
        String n = Bukkit.getOfflinePlayer(u).getName();
        return n == null ? "?" : n;
    }

    // ---------- Colocar ----------

    @EventHandler(ignoreCancelled = true)
    public void colocar(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (b.getType() != Material.SPAWNER) return;
        Player p = e.getPlayer();
        ItemStack it = e.getItemInHand();

        int nivel = 1, gen = 0;
        EntityType tipo = null;
        if (it.getItemMeta() instanceof BlockStateMeta bm && bm.getBlockState() instanceof CreatureSpawner src) {
            PersistentDataContainer sd = src.getPersistentDataContainer();
            nivel = plugin.getNivel(sd);
            gen = sd.getOrDefault(plugin.kGenerados, PersistentDataType.INTEGER, 0);
            tipo = src.getSpawnedType();
        }
        final int nivelF = nivel, genF = gen;
        final EntityType tipoF = tipo;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (b.getType() != Material.SPAWNER) return;
            CreatureSpawner cs = (CreatureSpawner) b.getState();
            if (tipoF != null) cs.setSpawnedType(tipoF);
            PersistentDataContainer d = cs.getPersistentDataContainer();
            d.set(plugin.kNivel, PersistentDataType.INTEGER, nivelF);
            d.set(plugin.kActivo, PersistentDataType.BYTE, (byte) 1);
            d.set(plugin.kDueno, PersistentDataType.STRING, p.getUniqueId().toString());
            d.set(plugin.kGenerados, PersistentDataType.INTEGER, genF);
            plugin.aplicar(cs);
            cs.update(true, false);
        });
    }

    // ---------- Romper ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void romper(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.SPAWNER) return;
        Player p = e.getPlayer();
        CreatureSpawner cs = (CreatureSpawner) b.getState();
        PersistentDataContainer d = cs.getPersistentDataContainer();

        if (!esDueno(p, d)) {
            e.setCancelled(true);
            plugin.msg(p, "&cEste spawner es de &f" + nombreDueno(plugin.dueno(d)) + "&c.");
            return;
        }

        int gen = plugin.generadosTotal(b, d);
        plugin.pendientes.remove(plugin.key(b.getLocation()));

        if (p.getGameMode() == GameMode.CREATIVE) {
            e.setDropItems(false);
            e.setExpToDrop(0);
            return;
        }

        boolean seda = p.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH);
        if (plugin.getConfig().getBoolean("romper.requiere-toque-de-seda", true) && !seda) {
            e.setCancelled(true);
            plugin.msg(p, "&cNecesitas un pico con &fToque de Seda&c (o usa el menú para quitarlo).");
            return;
        }

        e.setDropItems(false);
        e.setExpToDrop(0);
        if (seda) {
            EntityType tipo = cs.getSpawnedType();
            if (tipo == null) tipo = EntityType.PIG;
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5),
                    plugin.crearSpawner(tipo, plugin.getNivel(d), gen));
        }
    }

    // ---------- Generar mobs ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void generar(SpawnerSpawnEvent e) {
        CreatureSpawner cs = e.getSpawner();
        if (cs == null) return;
        PersistentDataContainer d = cs.getPersistentDataContainer();
        if (!d.has(plugin.kNivel, PersistentDataType.INTEGER)) return; // spawner vanilla, no lo tocamos

        if (!plugin.activo(d)) {
            e.setCancelled(true);
            return;
        }
        e.getEntity().getPersistentDataContainer()
                .set(plugin.kMobNivel, PersistentDataType.INTEGER, plugin.getNivel(d));
        plugin.pendientes.merge(plugin.key(cs.getLocation()), 1, Integer::sum);
    }

    // ---------- Drops: estrellas y loot extra ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void muerte(EntityDeathEvent e) {
        LivingEntity m = e.getEntity();
        Integer nv = m.getPersistentDataContainer().get(plugin.kMobNivel, PersistentDataType.INTEGER);
        if (nv == null) return;

        Player killer = m.getKiller();
        Nivel n = plugin.nivel(nv);
        boolean soloJugador = plugin.getConfig().getBoolean("estrella.solo-si-mata-jugador", true);

        // Loot extra
        if (n.lootMult() > 1 && (killer != null || !soloJugador) && rnd.nextDouble() < n.lootProb()) {
            List<ItemStack> extra = new ArrayList<>();
            for (ItemStack it : e.getDrops()) {
                ItemStack copia = it.clone();
                copia.setAmount(Math.min(it.getAmount() * (n.lootMult() - 1), it.getMaxStackSize()));
                extra.add(copia);
            }
            e.getDrops().addAll(extra);
        }

        // Estrellas digitales: van directo al saldo del jugador que mata el mob
        if (killer != null && rnd.nextDouble() < n.estrellaProb()) {
            int q = 1 + rnd.nextInt(n.estrellaMax());
            plugin.sumar(killer, q);
            killer.sendActionBar(c("&e+" + q + " ✦ &7| Total: &e" + plugin.saldo(killer)));
        }
    }

    @EventHandler
    public void entrar(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.barras.remove(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            plugin.convertirItems(p);
            plugin.refrescar(p);
        }, 20L);
    }

    // ---------- Menu ----------

    @EventHandler(ignoreCancelled = false)
    public void interactuar(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.SPAWNER) return;
        Player p = e.getPlayer();

        ItemStack mano = p.getInventory().getItemInMainHand();
        // Los huevos de spawn ya no cambian el mob del spawner
        if (mano.getType().name().endsWith("_SPAWN_EGG")) e.setCancelled(true);
        // Agachado con un bloque en la mano = colocar bloques normalmente
        if (p.isSneaking() && !mano.getType().isAir()) return;

        e.setCancelled(true);
        if (!p.hasPermission("spawner.usar")) return;
        abrir(p, b);
    }

    private void abrir(Player p, Block b) {
        CreatureSpawner cs = (CreatureSpawner) b.getState();
        PersistentDataContainer d = cs.getPersistentDataContainer();
        if (!d.has(plugin.kNivel, PersistentDataType.INTEGER)) {
            plugin.msg(p, "&cEste spawner no está gestionado por el plugin.");
            return;
        }
        if (!puede(p, d)) {
            plugin.msg(p, "&cEste spawner es de &f" + nombreDueno(plugin.dueno(d)) + "&c.");
            return;
        }
        Menu m = new Menu(b.getLocation());
        m.inv = Bukkit.createInventory(m, 27, c("&8Spawner de " + SpawnerPlugin.nombreMob(cs.getSpawnedType())));
        llenar(m.inv, cs, p, b);
        p.openInventory(m.inv);
    }

    private static String seg(int ticks) {
        return String.format(Locale.ROOT, "%.0f", ticks / 20.0);
    }

    private static int pct(double v) {
        return (int) Math.round(v * 100);
    }

    private void llenar(Inventory inv, CreatureSpawner cs, Player p, Block b) {
        PersistentDataContainer d = cs.getPersistentDataContainer();
        int nv = plugin.getNivel(d);
        int max = plugin.maxNivel();
        boolean on = plugin.activo(d);
        Nivel n = plugin.nivel(nv);
        String mob = SpawnerPlugin.nombreMob(cs.getSpawnedType());

        ItemStack fondo = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, fondo);

        // Info + estadisticas
        List<String> info = new ArrayList<>(List.of(
                "&7Mob: &f" + mob,
                "&7Nivel: &e" + nv + "&7/&e" + max,
                "&7Estado: " + (on ? "&aEncendido" : "&cApagado"),
                "&7Dueño: &f" + nombreDueno(plugin.dueno(d)),
                "&7Mobs generados: &f" + plugin.generadosTotal(b, d),
                "",
                "&7Mobs por spawn: &f" + n.cantidad(),
                "&7Cada: &f" + seg(n.delayMin()) + "-" + seg(n.delayMax()) + " s",
                "&7Prob. de estrellas: &e" + pct(n.estrellaProb()) + "% &7(hasta " + n.estrellaMax() + ")"));
        if (n.lootMult() > 1) {
            info.add("&7Botín x" + n.lootMult() + ": &e" + pct(n.lootProb()) + "%");
        }
        inv.setItem(10, item(Material.SPAWNER, "&6Spawner de " + mob, info.toArray(new String[0])));

        // Mejorar
        if (nv < max) {
            Nivel sig = plugin.nivel(nv + 1);
            int tiene = plugin.saldo(p);
            List<String> l = new ArrayList<>(List.of(
                    "&7Costo: &e" + sig.costo() + " estrellas",
                    "&7Tienes: " + (tiene >= sig.costo() ? "&a" : "&c") + tiene,
                    "",
                    "&7Mobs por spawn: &f" + n.cantidad() + " &8→ &a" + sig.cantidad(),
                    "&7Tiempo: &f" + seg(n.delayMin()) + "-" + seg(n.delayMax()) + "s &8→ &a"
                            + seg(sig.delayMin()) + "-" + seg(sig.delayMax()) + "s",
                    "&7Estrellas: &f" + pct(n.estrellaProb()) + "% &8→ &a" + pct(sig.estrellaProb()) + "%",
                    "&7Botín: &fx" + n.lootMult() + " &8→ &ax" + sig.lootMult() + " &7(" + pct(sig.lootProb()) + "%)",
                    "",
                    "&eClic para mejorar"));
            inv.setItem(12, item(Material.EXPERIENCE_BOTTLE, "&aMejorar a nivel " + (nv + 1), l.toArray(new String[0])));
        } else {
            inv.setItem(12, item(Material.NETHER_STAR, "&6¡Nivel máximo!", "&7Este spawner ya no se puede mejorar."));
        }

        // Encender / apagar
        inv.setItem(13, on
                ? item(Material.LIME_DYE, "&aEncendido", "&eClic para apagar")
                : item(Material.RED_DYE, "&cApagado", "&eClic para encender"));

        // Amigos
        List<String> lineas = new ArrayList<>();
        List<String> amigos = plugin.amigos(d);
        if (amigos.isEmpty()) lineas.add("&7Nadie todavía");
        for (String u : amigos) lineas.add("&f- " + nombreDueno(UUID.fromString(u)));
        lineas.add("");
        lineas.add("&8/spawner amigo agregar <jugador>");
        lineas.add("&8/spawner amigo quitar <jugador>");
        lineas.add("&8(mirando al spawner)");
        inv.setItem(14, item(Material.PLAYER_HEAD, "&bAmigos de confianza", lineas.toArray(new String[0])));

        // Quitar
        inv.setItem(16, item(Material.BARRIER, "&cQuitar spawner",
                "&7Te lo devuelve como ítem", "&7con su nivel guardado.", "", "&eClic para quitarlo"));
    }

    @EventHandler
    public void clic(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != top) return;

        Block b = m.loc.getBlock();
        if (b.getType() != Material.SPAWNER) {
            p.closeInventory();
            return;
        }
        CreatureSpawner cs = (CreatureSpawner) b.getState();
        PersistentDataContainer d = cs.getPersistentDataContainer();
        if (!puede(p, d)) {
            p.closeInventory();
            return;
        }

        switch (e.getRawSlot()) {
            case 12 -> mejorar(p, cs, d);
            case 13 -> alternar(p, cs, d);
            case 16 -> {
                quitar(p, cs, d, b);
                return;
            }
            default -> {
                return;
            }
        }
        llenar(top, cs, p, b);
    }

    @EventHandler
    public void arrastrar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    private void mejorar(Player p, CreatureSpawner cs, PersistentDataContainer d) {
        int nv = plugin.getNivel(d);
        if (nv >= plugin.maxNivel()) {
            plugin.msg(p, "&cYa está en el nivel máximo.");
            return;
        }
        Nivel sig = plugin.nivel(nv + 1);
        int tiene = plugin.saldo(p);
        if (tiene < sig.costo()) {
            plugin.msg(p, "&cTe faltan &e" + (sig.costo() - tiene) + " estrellas&c.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        plugin.restar(p, sig.costo());
        d.set(plugin.kNivel, PersistentDataType.INTEGER, nv + 1);
        plugin.aplicar(cs);
        cs.update(true, false);
        plugin.msg(p, "&aSpawner mejorado al nivel &e" + (nv + 1) + "&a.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    private void alternar(Player p, CreatureSpawner cs, PersistentDataContainer d) {
        boolean nuevo = !plugin.activo(d);
        d.set(plugin.kActivo, PersistentDataType.BYTE, (byte) (nuevo ? 1 : 0));
        plugin.aplicar(cs);
        cs.update(true, false);
        plugin.msg(p, nuevo ? "&aSpawner encendido." : "&cSpawner apagado.");
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    private void quitar(Player p, CreatureSpawner cs, PersistentDataContainer d, Block b) {
        if (!esDueno(p, d)) {
            plugin.msg(p, "&cSolo el dueño puede quitar el spawner.");
            return;
        }
        int gen = plugin.generadosTotal(b, d);
        plugin.pendientes.remove(plugin.key(b.getLocation()));
        EntityType tipo = cs.getSpawnedType() == null ? EntityType.PIG : cs.getSpawnedType();
        ItemStack it = plugin.crearSpawner(tipo, plugin.getNivel(d), gen);
        b.setType(Material.AIR);
        plugin.entregar(p, it);
        p.closeInventory();
        plugin.msg(p, "&aSpawner quitado. Conserva su nivel.");
    }
}
