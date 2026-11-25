package fr.minijeux;

import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /spleef join|leave|pos1|pos2 */
public class SpleefCommand implements CommandExecutor {
    private final MiniJeux plugin;

    public SpleefCommand(MiniJeux plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Msg.send(sender, "&cCommande réservée aux joueurs.");
            return true;
        }
        if (args.length == 0) {
            Msg.send(p, "&e/spleef join &7| &e/spleef leave");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "join" -> plugin.spleef().join(p);
            case "leave" -> {
                if (plugin.spleef().isPlaying(p)) plugin.spleef().leave(p);
                else Msg.send(p, "&cTu n'es pas dans une partie.");
            }
            case "pos1", "pos2" -> {
                if (!p.hasPermission("minijeux.admin")) {
                    Msg.send(p, "&cPermission refusée.");
                    return true;
                }
                Block target = p.getTargetBlockExact(10);
                if (target == null) {
                    Msg.send(p, "&cRegarde un bloc du sol (à moins de 10 blocs).");
                    return true;
                }
                int which = args[0].equalsIgnoreCase("pos1") ? 1 : 2;
                plugin.spleef().setPos(which, target.getLocation());
                Msg.send(p, "&aPosition " + which + " du sol de Spleef définie.");
            }
            default -> Msg.send(p, "&cSous-commande inconnue.");
        }
        return true;
    }
}
