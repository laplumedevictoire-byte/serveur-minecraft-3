package fr.minijeux;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Statistiques simples (victoires / défaites) dans stats.yml. */
public class Stats {
    private final MiniJeux plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public Stats(MiniJeux plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public int get(UUID id, String key) {
        return yaml.getInt(id + "." + key, 0);
    }

    public void add(UUID id, String key) {
        yaml.set(id + "." + key, get(id, key) + 1);
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Impossible de sauvegarder stats.yml : " + ex.getMessage());
        }
    }
}
