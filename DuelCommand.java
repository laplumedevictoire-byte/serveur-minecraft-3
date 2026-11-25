package fr.minijeux;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /duel et /duelarena */
public class DuelCommand implements CommandExecutor {
    private final MiniJeux plugin;

    public DuelCommand(MiniJeux plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("duelarena")) {
            return arena(sender, args);
        }
        if (!(sender instanceof Player p)) {
            Msg.send(sender, "&cCommande réservée aux joueurs.");
            return true;
        }
        DuelManager mgr = plugin.duels();
        if (args.length == 0) {
            Msg.send(p, "&e/duel <joueur> &7| &e/duel accept &7| &e/duel deny &7| &e/duel queue &7| &e/duel leave &7| &e/duel stats");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "accept" -> mgr.accept(p);
            case "deny" -> mgr.deny(p);
            case "queue" -> mgr.joinQueue(p);
            case "leave" -> mgr.leaveQueue(p);
            case "stats" -> mgr.showStats(p);
            default -> {
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    Msg.send(p, "&cJoueur introuvable.");
                } else {
                    mgr.invite(p, target);
                }
            }
        }
        return true;
    }

    private boolean arena(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minijeux.admin")) {
            Msg.send(sender, "&cPermission refusée.");
            return true;
        }
        DuelManager mgr = plugin.duels();
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            Msg.send(sender, "&6Arènes : &f" + String.join(", ", mgr.listArenas()));
            return true;
        }
        if (args.length < 2) {
            Msg.send(sender, "&e/duelarena spawn1|spawn2|delete <nom> &7| &e/duelarena list");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "spawn1", "spawn2" -> {
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, "&cCommande réservée aux joueurs.");
                    return true;
                }
                int which = args[0].equalsIgnoreCase("spawn1") ? 1 : 2;
                mgr.setSpawn(args[1], which, p.getLocation());
                Msg.send(p, "&aSpawn " + which + " de l'arène &e" + args[1] + " &adéfini.");
            }
            case "delete" -> Msg.send(sender, mgr.deleteArena(args[1])
                    ? "&aArène supprimée."
                    : "&cArène introuvable.");
            default -> Msg.send(sender, "&cSous-commande inconnue.");
        }
        return true;
    }
}
