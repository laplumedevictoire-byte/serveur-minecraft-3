package fr.minijeux;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MiniJeux extends JavaPlugin {
    private DuelManager duels;
    private SpleefManager spleef;
    private Stats stats;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        stats = new Stats(this);
        duels = new DuelManager(this);
        spleef = new SpleefManager(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(duels, this);
        pm.registerEvents(spleef, this);
        pm.registerEvents(new LobbyListener(this), this);
        pm.registerEvents(new MenuListener(this), this);

        DuelCommand duelCommand = new DuelCommand(this);
        getCommand("duel").setExecutor(duelCommand);
        getCommand("duelarena").setExecutor(duelCommand);
        getCommand("spleef").setExecutor(new SpleefCommand(this));

        LobbyCommands lobbyCommands = new LobbyCommands(this);
        getCommand("lobby").setExecutor(lobbyCommands);
        getCommand("setlobby").setExecutor(lobbyCommands);
        getCommand("jeux").setExecutor(lobbyCommands);

        getLogger().info("MiniJeux activé.");
    }

    public DuelManager duels() { return duels; }
    public SpleefManager spleef() { return spleef; }
    public Stats stats() { return stats; }

    /** Vrai si le joueur est dans un duel ou une partie de Spleef. */
    public boolean inGame(Player p) {
        return duels.isInDuel(p) || spleef.isPlaying(p);
    }

    /** Remet le joueur à zéro et le téléporte au lobby. */
    public void sendToLobby(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        p.setGameMode(GameMode.ADVENTURE);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.setFallDistance(0f);

        ItemStack compass = new ItemStack(Material.COMPASS);
        compass.editMeta(m -> m.displayName(Msg.item("&6&lMenu des jeux &7(clic droit)")));
        p.getInventory().setItem(4, compass);

        Location lobby = getConfig().getLocation("lobby");
        if (lobby == null) lobby = Bukkit.getWorlds().get(0).getSpawnLocation();
        p.teleport(lobby);
    }
}
