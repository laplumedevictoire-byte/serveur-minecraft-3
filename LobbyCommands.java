package fr.minijeux;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /lobby, /setlobby, /jeux */
public class LobbyCommands implements CommandExecutor {
    private final MiniJeux plugin;

    public LobbyCommands(MiniJeux plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Msg.send(sender, "&cCommande réservée aux joueurs.");
            return true;
        }
        switch (cmd.getName().toLowerCase()) {
            case "lobby" -> {
                if (plugin.spleef().isPlaying(p)) {
                    plugin.spleef().leave(p);
                } else if (plugin.duels().isInDuel(p)) {
                    Msg.send(p, "&cTu ne peux pas quitter un duel en cours.");
                } else {
                    plugin.duels().removeFromQueue(p.getUniqueId());
                    plugin.sendToLobby(p);
                }
            }
            case "setlobby" -> {
                if (!p.hasPermission("minijeux.admin")) {
                    Msg.send(p, "&cPermission refusée.");
                    return true;
                }
                plugin.getConfig().set("lobby", p.getLocation());
                plugin.saveConfig();
                Msg.send(p, "&aLobby défini ici.");
            }
            case "jeux" -> MenuListener.open(p);
            default -> { }
        }
        return true;
    }
}
