package fr.minijeux;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;

/** Petits outils pour les messages (codes couleur avec &). */
public final class Msg {
    public static final String PREFIX = "&8[&6MiniJeux&8] &7";

    private Msg() {}

    public static Component c(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    /** Pour les noms/lores d'items (sans italique par défaut). */
    public static Component item(String s) {
        return c(s).decoration(TextDecoration.ITALIC, false);
    }

    public static void send(CommandSender to, String message) {
        to.sendMessage(c(PREFIX + message));
    }

    public static void all(Collection<? extends Player> players, String message) {
        for (Player p : players) send(p, message);
    }
}
