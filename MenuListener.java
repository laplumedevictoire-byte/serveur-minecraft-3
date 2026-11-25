package fr.minijeux;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Menu des jeux (inventaire 27 cases). */
public class MenuListener implements Listener {
    private final MiniJeux plugin;

    public MenuListener(MiniJeux plugin) {
        this.plugin = plugin;
    }

    static class MenuHolder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public static void open(Player p) {
        MenuHolder holder = new MenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Msg.c("&6&lMini-jeux"));
        holder.inv = inv;
        inv.setItem(11, icon(Material.IRON_SWORD, "&cDuel", "&7Rejoindre la file d'attente", "&7et affronter un joueur."));
        inv.setItem(13, icon(Material.DIAMOND_SHOVEL, "&bSpleef", "&7Casse le sol sous tes adversaires !"));
        inv.setItem(15, icon(Material.BARRIER, "&7Quitter", "&7Quitter la file ou la partie."));
        p.openInventory(inv);
    }

    private static ItemStack icon(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        it.editMeta(m -> {
            m.displayName(Msg.item(name));
            m.lore(java.util.Arrays.stream(lore).map(Msg::item).toList());
        });
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        switch (e.getSlot()) {
            case 11 -> {
                p.closeInventory();
                plugin.duels().joinQueue(p);
            }
            case 13 -> {
                p.closeInventory();
                plugin.spleef().join(p);
            }
            case 15 -> {
                p.closeInventory();
                plugin.duels().leaveQueue(p);
                if (plugin.spleef().isPlaying(p)) plugin.spleef().leave(p);
            }
            default -> { }
        }
    }
}
