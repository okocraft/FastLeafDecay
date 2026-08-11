package com.cavetale.fastleafdecay;

import com.cavetale.fastleafdecay.config.FastLeafDecayConfig;
import com.cavetale.fastleafdecay.config.FastLeafDecayConfigHolder;
import com.cavetale.fastleafdecay.config.FastLeafDecayConfigLoader;
import com.cavetale.fastleafdecay.decay.LeafDecayExecutor;
import com.cavetale.fastleafdecay.decay.LeafDecayHandler;
import org.bukkit.Tag;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurateException;

import java.nio.file.Files;
import java.util.logging.Level;

public final class FastLeafDecayPlugin extends JavaPlugin {

    private static final String CONFIG_FILENAME = "config.yml";

    private LeafDecayHandler handler;

    @Override
    public void onEnable() {
        var configHolder = new FastLeafDecayConfigHolder(loadConfig());
        var executor = new LeafDecayExecutor(getServer().getPluginManager(), configHolder, Tag.LEAVES::isTagged);

        this.handler = new LeafDecayHandler(this, configHolder, executor, Tag.LOGS::isTagged, Tag.LEAVES::isTagged);
        getServer().getPluginManager().registerEvents(this.handler, this);
    }

    @Override
    public void onDisable() {
        if (this.handler != null) {
            this.handler.clear();
            this.handler = null;
        }
    }

    private @NotNull FastLeafDecayConfig loadConfig() {
        var configPath = getDataFolder().toPath().resolve(CONFIG_FILENAME);

        if (!Files.isRegularFile(configPath)) {
            saveResource(CONFIG_FILENAME, false);
        }

        try {
            var result = FastLeafDecayConfigLoader.load(configPath);
            result.warnings().forEach(getLogger()::warning);
            return result.config();
        } catch (ConfigurateException e) {
            getLogger().log(Level.WARNING, "Could not load " + CONFIG_FILENAME + ", using the default values.", e);
            return FastLeafDecayConfig.defaults();
        }
    }
}
