package fr.minijeux;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.*;

import net.kyori.adventure.text.event.ClickEvent;

public class DuelManager implements Listener {
    enum State { COUNTDOWN, FIGHT, ENDING }

    static class Arena {
        final String name;
        Location s1, s2;
        boolean busy;

        Arena(String name) { this.name = name; }

        boolean ready() { return s1 != null && s2 != null; }
    }

    static class Duel {
        final UUID a, b;
        final Arena arena;
        State state = State.COUNTDOWN;

        Duel(UUID a, UUID b, Arena arena) {
            this.a = a;
            this.b = b;
            this.arena = arena;
        }

        UUID other(UUID id) { return id.equals(a) ? b : a; }

        List<Player> online() {
            List<Player> list = new ArrayList<>();
            for (UUID id : List.of(a, b)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) list.add(p);
            }
            return list;
        }
    }

    private static final Title.Times TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO);

    private final MiniJeux plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<UUID, Duel> duels = new HashMap<>();
    private final Map<UUID, UUID> invites = new HashMap<>(); // cible -> challenger
    private final Deque<UUID> queue = new ArrayDeque<>();

    public DuelManager(MiniJeux plugin) {
        this.plugin = plugin;
        loadArenas();
    }

    // ------------------------------------------------------------------ arènes

    private void loadArenas() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("duel.arenas");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            Arena a = new Arena(key);
            a.s1 = sec.getLocation(key + ".spawn1");
            a.s2 = sec.getLocation(key + ".spawn2");
            arenas.put(key.toLowerCase(), a);
        }
    }

    public void setSpawn(String name, int which, Location loc) {
        String key = name.toLowerCase();
        Arena a = arenas.computeIfAbsent(key, Arena::new);
        if (which == 1) a.s1 = loc; else a.s2 = loc;
        plugin.getConfig().set("duel.arenas." + key + ".spawn1", a.s1);
        plugin.getConfig().set("duel.arenas." + key + ".spawn2", a.s2);
        plugin.saveConfig();
    }

    public boolean deleteArena(String name) {
        Arena a = arenas.remove(name.toLowerCase());
        if (a == null) return false;
        plugin.getConfig().set("duel.arenas." + name.toLowerCase(), null);
        plugin.saveConfig();
        return true;
    }

    public List<String> listArenas() {
        List<String> out = new ArrayList<>();
        for (Arena a : arenas.values()) {
            out.add(a.name + (a.ready() ? "" : " (incomplète)") + (a.busy ? " [occupée]" : ""));
        }
        return out;
    }

    private Arena freeArena() {
        for (Arena a : arenas.values()) {
            if (!a.busy && a.ready()) return a;
        }
        return null;
    }

    // ------------------------------------------------------------------ état

    public boolean isInDuel(Player p) {
        return duels.containsKey(p.getUniqueId());
    }

    // ------------------------------------------------------------------ défis

    public void invite(Player from, Player to) {
        if (from.equals(to)) {
            Msg.send(from, "&cTu ne peux pas te défier toi-même.");
            return;
        }
        if (plugin.inGame(from) || plugin.inGame(to)) {
            Msg.send(from, "&cUn des deux joueurs est déjà en jeu.");
            return;
        }
        invites.put(to.getUniqueId(), from.getUniqueId());
        Msg.send(from, "&aDéfi envoyé à &e" + to.getName() + "&a.");
        to.sendMessage(Msg.c(Msg.PREFIX + "&e" + from.getName() + " &7te défie en duel ! ")
                .append(Msg.c("&a[ACCEPTER] ").clickEvent(ClickEvent.runCommand("/duel accept")))
                .append(Msg.c("&c[REFUSER]").clickEvent(ClickEvent.runCommand("/duel deny"))));

        int seconds = plugin.getConfig().getInt("duel.invite-expire-seconds", 30);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (from.getUniqueId().equals(invites.get(to.getUniqueId()))) {
                invites.remove(to.getUniqueId());
                Msg.send(from, "&7Ton défi à &e" + to.getName() + " &7a expiré.");
            }
        }, seconds * 20L);
    }

    public void accept(Player to) {
        UUID fromId = invites.remove(to.getUniqueId());
        Player from = fromId == null ? null : Bukkit.getPlayer(fromId);
        if (from == null) {
            Msg.send(to, "&cAucun défi en attente.");
            return;
        }
        if (plugin.inGame(from) || plugin.inGame(to)) {
            Msg.send(to, "&cImpossible : un des joueurs est déjà en jeu.");
            return;
        }
        if (!start(from, to)) {
            Msg.send(to, "&cAucune arène libre pour le moment.");
            Msg.send(from, "&cAucune arène libre pour le moment.");
        }
    }

    public void deny(Player to) {
        UUID fromId = invites.remove(to.getUniqueId());
        Player from = fromId == null ? null : Bukkit.getPlayer(fromId);
        if (from == null) {
            Msg.send(to, "&cAucun défi en attente.");
            return;
        }
        Msg.send(to, "&7Défi refusé.");
        Msg.send(from, "&e" + to.getName() + " &7a refusé ton défi.");
    }

    // ------------------------------------------------------------------ file d'attente

    public void joinQueue(Player p) {
        if (plugin.inGame(p)) {
            Msg.send(p, "&cTu es déjà en jeu.");
            return;
        }
        if (queue.contains(p.getUniqueId())) {
            Msg.send(p, "&cTu es déjà dans la file.");
            return;
        }
        queue.add(p.getUniqueId());
        Msg.send(p, "&aTu as rejoint la file de duel &7(" + queue.size() + " en attente)&a.");
        tryMatch();
    }

    public void leaveQueue(Player p) {
        if (queue.remove(p.getUniqueId())) {
            Msg.send(p, "&7Tu as quitté la file de duel.");
        }
    }

    public void removeFromQueue(UUID id) {
        queue.remove(id);
    }

    private void tryMatch() {
        while (queue.size() >= 2 && freeArena() != null) {
            Player a = Bukkit.getPlayer(queue.poll());
            Player b = Bukkit.getPlayer(queue.poll());
            if (a == null || b == null) {
                if (a != null) queue.addFirst(a.getUniqueId());
                if (b != null) queue.addFirst(b.getUniqueId());
                continue;
            }
            start(a, b);
        }
    }

    // ------------------------------------------------------------------ déroulement

    private boolean start(Player a, Player b) {
        Arena arena = freeArena();
        if (arena == null) return false;

        arena.busy = true;
        Duel d = new Duel(a.getUniqueId(), b.getUniqueId(), arena);
        duels.put(a.getUniqueId(), d);
        duels.put(b.getUniqueId(), d);
        queue.remove(a.getUniqueId());
        queue.remove(b.getUniqueId());
        invites.remove(a.getUniqueId());
        invites.remove(b.getUniqueId());

        prepare(a, arena.s1);
        prepare(b, arena.s2);
        Msg.send(a, "&6Duel contre &e" + b.getName() + " &7(arène &e" + arena.name + "&7)");
        Msg.send(b, "&6Duel contre &e" + a.getName() + " &7(arène &e" + arena.name + "&7)");
        countdown(d);
        return true;
    }

    private void prepare(Player p, Location spawn) {
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setFireTicks(0);
        giveKit(p);
        p.teleport(spawn);
    }

    private void giveKit(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setHelmet(new ItemStack(Material.IRON_HELMET));
        inv.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        inv.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        inv.setBoots(new ItemStack(Material.IRON_BOOTS));
        inv.addItem(
                new ItemStack(Material.IRON_SWORD),
                new ItemStack(Material.BOW),
                new ItemStack(Material.ARROW, 16),
                new ItemStack(Material.GOLDEN_APPLE, 3),
                new ItemStack(Material.COOKED_BEEF, 16));
    }

    private void countdown(Duel d) {
        new BukkitRunnable() {
            int t = plugin.getConfig().getInt("duel.countdown", 5);

            @Override
            public void run() {
                if (d.state != State.COUNTDOWN) {
                    cancel();
                    return;
                }
                if (t <= 0) {
                    d.state = State.FIGHT;
                    for (Player p : d.online()) {
                        p.showTitle(Title.title(Msg.c("&a&lGO !"), Component.empty(), TIMES));
                    }
                    cancel();
                    return;
                }
                for (Player p : d.online()) {
                    p.showTitle(Title.title(Msg.c("&e" + t), Component.empty(), TIMES));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                }
                t--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void end(Duel d, UUID winner, UUID loser) {
        if (d.state == State.ENDING) return;
        d.state = State.ENDING;

        plugin.stats().add(winner, "wins");
        plugin.stats().add(loser, "losses");

        Msg.all(d.online(), "&6" + name(winner) + " &7a gagné le duel contre &c" + name(loser) + "&7 !");

        Player w = Bukkit.getPlayer(winner);
        if (w != null) {
            w.setHealth(20.0);
            w.showTitle(Title.title(Msg.c("&6&lVICTOIRE"), Component.empty()));
        }
        Player l = Bukkit.getPlayer(loser);
        if (l != null) {
            l.setHealth(20.0);
            l.showTitle(Title.title(Msg.c("&c&lDÉFAITE"), Component.empty()));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID id : List.of(d.a, d.b)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) plugin.sendToLobby(p);
                duels.remove(id);
            }
            d.arena.busy = false;
            tryMatch();
        }, 60L);
    }

    private String name(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    public void showStats(Player p) {
        int w = plugin.stats().get(p.getUniqueId(), "wins");
        int l = plugin.stats().get(p.getUniqueId(), "losses");
        Msg.send(p, "&6Duels : &a" + w + " victoire(s) &7/ &c" + l + " défaite(s)");
    }

    // ------------------------------------------------------------------ événements

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Duel d = duels.get(p.getUniqueId());
        if (d == null) return;

        if (d.state != State.FIGHT) {
            e.setCancelled(true);
            return;
        }
        if (p.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true); // pas d'écran de mort
            end(d, d.other(p.getUniqueId()), p.getUniqueId());
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Duel d = duels.get(e.getPlayer().getUniqueId());
        if (d == null || d.state != State.COUNTDOWN) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
            Location fixed = from.clone();
            fixed.setYaw(to.getYaw());
            fixed.setPitch(to.getPitch());
            e.setTo(fixed);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        queue.remove(id);
        invites.remove(id);
        Duel d = duels.get(id);
        if (d != null && d.state != State.ENDING) {
            end(d, d.other(id), id);
        }
    }
}
