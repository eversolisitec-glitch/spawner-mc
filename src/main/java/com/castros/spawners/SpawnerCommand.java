package com.castros.spawners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class SpawnerCommand implements CommandExecutor, TabCompleter {

    private final SpawnerPlugin plugin;

    public SpawnerCommand(SpawnerPlugin plugin) {
        this.plugin = plugin;
    }

    private void say(CommandSender s, String t) {
        s.sendMessage(SpawnerPlugin.c("&8[&6Spawner&8] &7" + t));
    }

    private void ayuda(CommandSender s) {
        say(s, "Comandos:");
        s.sendMessage(SpawnerPlugin.c("&e/spawner saldo &7- cuántas estrellas tienes"));
        s.sendMessage(SpawnerPlugin.c("&e/spawner pantalla &7- mostrar/ocultar el contador en pantalla"));
        s.sendMessage(SpawnerPlugin.c("&e/spawner amigo agregar|quitar <jugador> &7(mirando al spawner)"));
        if (s.hasPermission("spawner.admin")) {
            s.sendMessage(SpawnerPlugin.c("&e/spawner dar <jugador> <mob> [nivel]"));
            s.sendMessage(SpawnerPlugin.c("&e/spawner estrellas <jugador> <cantidad> &7(negativo = quitar)"));
            s.sendMessage(SpawnerPlugin.c("&e/spawner recargar"));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            ayuda(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "dar" -> dar(sender, args);
            case "estrellas" -> estrellas(sender, args);
            case "amigo" -> amigo(sender, args);
            case "saldo" -> saldo(sender);
            case "pantalla" -> pantalla(sender);
            case "recargar" -> {
                if (!sender.hasPermission("spawner.admin")) {
                    say(sender, "&cNo tienes permiso.");
                    return true;
                }
                plugin.reloadConfig();
                say(sender, "&aConfiguración recargada.");
            }
            default -> ayuda(sender);
        }
        return true;
    }

    private void dar(CommandSender s, String[] a) {
        if (!s.hasPermission("spawner.admin")) {
            say(s, "&cNo tienes permiso.");
            return;
        }
        if (a.length < 3) {
            say(s, "&cUso: /spawner dar <jugador> <mob> [nivel]");
            return;
        }
        Player objetivo = Bukkit.getPlayerExact(a[1]);
        if (objetivo == null) {
            say(s, "&cEse jugador no está conectado.");
            return;
        }
        EntityType tipo;
        try {
            tipo = EntityType.valueOf(a[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            say(s, "&cMob no válido.");
            return;
        }
        if (!tipo.isAlive() || !tipo.isSpawnable()) {
            say(s, "&cEse mob no se puede usar en un spawner.");
            return;
        }
        int nivel = 1;
        if (a.length > 3) {
            try {
                nivel = Integer.parseInt(a[3]);
            } catch (NumberFormatException ex) {
                say(s, "&cEl nivel debe ser un número.");
                return;
            }
        }
        nivel = Math.max(1, Math.min(nivel, plugin.maxNivel()));
        plugin.entregar(objetivo, plugin.crearSpawner(tipo, nivel, 0));
        say(s, "&aLe diste un spawner de &f" + SpawnerPlugin.nombreMob(tipo) + " &anivel " + nivel + " a " + objetivo.getName() + ".");
    }

    private void estrellas(CommandSender s, String[] a) {
        if (!s.hasPermission("spawner.admin")) {
            say(s, "&cNo tienes permiso.");
            return;
        }
        if (a.length < 3) {
            say(s, "&cUso: /spawner estrellas <jugador> <cantidad>");
            return;
        }
        Player objetivo = Bukkit.getPlayerExact(a[1]);
        if (objetivo == null) {
            say(s, "&cEse jugador no está conectado.");
            return;
        }
        int cant;
        try {
            cant = Integer.parseInt(a[2]);
        } catch (NumberFormatException ex) {
            say(s, "&cLa cantidad debe ser un número.");
            return;
        }
        if (cant == 0) {
            say(s, "&cLa cantidad no puede ser 0.");
            return;
        }
        plugin.sumar(objetivo, cant);
        say(s, "&aSaldo de " + objetivo.getName() + ": &e" + plugin.saldo(objetivo) + " estrellas&a.");
    }

    private void saldo(CommandSender s) {
        if (!(s instanceof Player p)) {
            say(s, "&cSolo los jugadores pueden usar esto.");
            return;
        }
        say(s, "Tienes &e" + plugin.saldo(p) + " ✦ &7estrellas.");
    }

    private void pantalla(CommandSender s) {
        if (!(s instanceof Player p)) {
            say(s, "&cSolo los jugadores pueden usar esto.");
            return;
        }
        PersistentDataContainer d = p.getPersistentDataContainer();
        boolean oculto = d.getOrDefault(plugin.kOculto, PersistentDataType.BYTE, (byte) 0) == 1;
        d.set(plugin.kOculto, PersistentDataType.BYTE, (byte) (oculto ? 0 : 1));
        plugin.refrescar(p);
        say(s, oculto ? "&aContador de estrellas mostrado." : "&cContador de estrellas oculto.");
    }

    private void amigo(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            say(s, "&cSolo los jugadores pueden usar esto.");
            return;
        }
        if (a.length < 3) {
            say(s, "&cUso: /spawner amigo agregar|quitar <jugador>");
            return;
        }
        Block b = p.getTargetBlockExact(6);
        if (b == null || b.getType() != Material.SPAWNER) {
            say(s, "&cMira directamente a tu spawner.");
            return;
        }
        CreatureSpawner cs = (CreatureSpawner) b.getState();
        PersistentDataContainer d = cs.getPersistentDataContainer();
        UUID du = plugin.dueno(d);
        if (!p.hasPermission("spawner.admin") && du != null && !du.equals(p.getUniqueId())) {
            say(s, "&cSolo el dueño puede cambiar los amigos.");
            return;
        }
        OfflinePlayer t = Bukkit.getPlayerExact(a[2]);
        if (t == null) t = Bukkit.getOfflinePlayerIfCached(a[2]);
        if (t == null) {
            say(s, "&cNo conozco a ese jugador.");
            return;
        }
        List<String> amigos = plugin.amigos(d);
        String id = t.getUniqueId().toString();
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "agregar" -> {
                if (!amigos.contains(id)) amigos.add(id);
                say(s, "&a" + a[2] + " ahora puede usar este spawner.");
            }
            case "quitar" -> {
                amigos.remove(id);
                say(s, "&a" + a[2] + " ya no puede usar este spawner.");
            }
            default -> {
                say(s, "&cUso: /spawner amigo agregar|quitar <jugador>");
                return;
            }
        }
        plugin.setAmigos(d, amigos);
        cs.update(true, false);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("amigo", "saldo", "pantalla"));
            if (s.hasPermission("spawner.admin")) out.addAll(List.of("dar", "estrellas", "recargar"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("amigo")) {
            out.addAll(List.of("agregar", "quitar"));
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("dar") || a[0].equalsIgnoreCase("estrellas"))) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("amigo")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("dar")) {
            for (EntityType t : EntityType.values()) {
                if (t.isAlive() && t.isSpawnable()) out.add(t.getKey().getKey());
            }
        }
        String ult = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(ult));
        return out;
    }
}
