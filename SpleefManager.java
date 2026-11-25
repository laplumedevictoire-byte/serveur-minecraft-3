package fr.minijeux;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spleef : le sol (zone pos1 -> pos2) est cassé par les joueurs ;
 * celui qui tombe sous la zone est éliminé. Le sol est restauré en fin de partie.
 */
public class SpleefManager implements Listener {
    enum State { WAITING, COUNTDOWN, RUNNING }

    private final MiniJeux plugin;
    private State state = State.WAITING;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Map<Block, BlockData> snapshot = new HashMap<>();
    private Location pos1, pos2;
    private BukkitTask countdownTask;

    public SpleefManager(MiniJeux plugin) {
        this.plugin = plugin;
        this.pos1 = plugin.getConfig().getLocation("spleef.pos1");
        this.pos2 = plugin.getConfig().getLocation("spleef.pos2");

        // Vérifie toutes les 5 ticks si un joueur est tombé
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 5L);
    }

    // ------------------------------------------------------------------ config

    public void setPos(int which, Location loc) {
        Location block = loc.getBlock().getLocation();
        if (which == 1) pos1 = block; else pos2 = block;
        plugin.getConfig().set("spleef.pos" + which, block);
        plugin.saveConfig();
    }

    private boolean ready() {
        return pos1 != null && pos2 != null && pos1.getWorld() != null
                && pos1.getWorld().equals(pos2.getWorld());
    }

    private boolean inFloor(Location l) {
        if (!ready() || !l.getWorld().equals(pos1.getWorld())) return false;
        return l.getBlockX() >= Math.min(pos1.getBlockX(), pos2.getBlockX())
                && l.getBlockX() <= Math.max(pos1.getBlockX(), pos2.getBlockX())
                && l.getBlockY() >= Math.min(pos1.getBlockY(), pos2.getBlockY())
                && l.getBlockY() <= Math.max(pos1.getBlockY(), pos2.getBlockY())
                && l.getBlockZ() >= Math.min(pos1.getBlockZ(), pos2.getBlockZ())
                && l.getBlockZ() <= Math.max(pos1.getBlockZ(), pos2.getBlockZ());
    }

    private Location randomSpawn() {
        World w = pos1.getWorld();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        double y = Math.max(pos1.getBlockY(), pos2.getBlockY()) + 1;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new Location(w, r.nextInt(minX, maxX + 1) + 0.5, y, r.nextInt(minZ, maxZ + 1) + 0.5);
    }

    // ------------------------------------------------------------------ joueurs

    public boolean isPlaying(Player p) {
        return players.contains(p.getUniqueId());
    }

    public void join(Player p) {
        if (!ready()) {
            Msg.send(p, "&cL'arène de Spleef n'est pas configurée.");
            return;
        }
        if (plugin.inGame(p)) {
            Msg.send(p, "&cTu es déjà en jeu.");
            return;
        }
        if (state == State.RUNNING) {
            Msg.send(p, "&cUne partie est en cours, réessaie dans un instant.");
            return;
        }
        int max = plugin.getConfig().getInt("spleef.max-players", 16);
        if (players.size() >= max) {
            Msg.send(p, "&cLa partie est pleine.");
            return;
        }

        plugin.duels().removeFromQueue(p.getUniqueId());
        players.add(p.getUniqueId());

        p.getInventory().clear();
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.getInventory().addItem(shovel());
        p.teleport(randomSpawn());

        broadcast("&e" + p.getName() + " &7a rejoint le Spleef &8(" + players.size() + "/" + max + ")");

        int min = plugin.getConfig().getInt("spleef.min-players", 2);
        if (state == State.WAITING && players.size() >= min) startCountdown();
    }

    public void leave(Player p) {
        if (!players.remove(p.getUniqueId())) return;
        broadcast("&e" + p.getName() + " &7a quitté le Spleef.");
        if (p.isOnline()) plugin.sendToLobby(p);
        checkWin();
    }

    private ItemStack shovel() {
        ItemStack s = new ItemStack(Material.DIAMOND_SHOVEL);
        s.editMeta(m -> {
            m.displayName(Msg.item("&bPelle de Spleef"));
            // Récupéré par son nom pour fonctionner sur toutes les versions 1.20 / 1.21
            Enchantment eff = Enchantment.getByKey(NamespacedKey.minecraft("efficiency"));
            if (eff != null) m.addEnchant(eff, 5, true);
            m.setUnbreakable(true);
        });
        return s;
    }

    private void broadcast(String msg) {
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) Msg.send(p, msg);
        }
    }

    // ------------------------------------------------------------------ déroulement

    private void startCountdown() {
        state = State.COUNTDOWN;
        int total = plugin.getConfig().getInt("spleef.start-countdown", 15);
        countdownTask = new BukkitRunnable() {
            int t = total;

            @Override
            public void run() {
                int min = plugin.getConfig().getInt("spleef.min-players", 2);
                if (players.size() < min) {
                    state = State.WAITING;
                    broadcast("&cPas assez de joueurs, compte à rebours annulé.");
                    cancel();
                    return;
                }
                if (t <= 0) {
                    cancel();
                    startGame();
                    return;
                }
                if (t <= 5 || t % 5 == 0) broadcast("&6Début dans &e" + t + " &6seconde(s)...");
                t--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startGame() {
        state = State.RUNNING;
        snapshotFloor();
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.teleport(randomSpawn());
        }
        broadcast("&a&lGO ! &7Casse le sol, ne tombe pas !");
    }

    private void tick() {
        if (players.isEmpty() || !ready()) return;
        double minY = Math.min(pos1.getBlockY(), pos2.getBlockY()) - 1;
        for (UUID id : new ArrayList<>(players)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                players.remove(id);
                continue;
            }
            if (p.getLocation().getY() < minY) {
                if (state == State.RUNNING) eliminate(p);
                else p.teleport(randomSpawn());
            }
        }
        checkWin();
    }

    private void eliminate(Player p) {
        players.remove(p.getUniqueId());
        broadcast("&c" + p.getName() + " &7est éliminé ! &8(" + players.size() + " restant(s))");
        Msg.send(p, "&cTu es éliminé.");
        plugin.sendToLobby(p);
        checkWin();
    }

    private void checkWin() {
        if (state != State.RUNNING || players.size() > 1) return;

        Player winner = null;
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) winner = p;
        }
        players.clear();
        state = State.WAITING;
        restoreFloor();

        if (winner != null) {
            plugin.stats().add(winner.getUniqueId(), "spleef_wins");
            Bukkit.broadcast(Msg.c(Msg.PREFIX + "&6" + winner.getName() + " &7remporte le Spleef !"));
            Player w = winner;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (w.isOnline() && !plugin.inGame(w)) plugin.sendToLobby(w);
            }, 60L);
        }
    }

    // ------------------------------------------------------------------ sol

    private void snapshotFloor() {
        snapshot.clear();
        World w = pos1.getWorld();
        for (int x = Math.min(pos1.getBlockX(), pos2.getBlockX()); x <= Math.max(pos1.getBlockX(), pos2.getBlockX()); x++) {
            for (int y = Math.min(pos1.getBlockY(), pos2.getBlockY()); y <= Math.max(pos1.getBlockY(), pos2.getBlockY()); y++) {
                for (int z = Math.min(pos1.getBlockZ(), pos2.getBlockZ()); z <= Math.max(pos1.getBlockZ(), pos2.getBlockZ()); z++) {
                    Block b = w.getBlockAt(x, y, z);
                    snapshot.put(b, b.getBlockData());
                }
            }
        }
    }

    private void restoreFloor() {
        snapshot.forEach((block, data) -> block.setBlockData(data, false));
        snapshot.clear();
    }

    // ------------------------------------------------------------------ événements

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!players.contains(p.getUniqueId())) return;
        if (state != State.RUNNING || !inFloor(e.getBlock().getLocation())) {
            e.setCancelled(true);
            return;
        }
        e.setDropItems(false);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && players.contains(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (players.remove(p.getUniqueId())) {
            broadcast("&e" + p.getName() + " &7a quitté le Spleef.");
            checkWin();
        }
    }
}
