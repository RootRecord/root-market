package com.rootrecord.minecraft.rootmarket;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootmcshops.RootMcShopsPlugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class RootMarketPlugin extends JavaPlugin {

    private RootRecordYamlConfig yaml;

    @Override
    public void onEnable() {
        if (RootMcShopsPlugin.get() == null) {
            getLogger().severe("Root-ChestShops is not enabled — disabling Root-Market.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_MARKET_CONFIG, "root-market.yml");
        yaml.load();

        MarketCommand handler = new MarketCommand(this);
        var market = getCommand("market");
        if (market != null) {
            market.setExecutor(handler);
            market.setTabCompleter(handler);
        }
        var shops = getCommand("shops");
        if (shops != null) {
            shops.setExecutor(handler);
            shops.setTabCompleter(handler);
        }
        getServer().getPluginManager().registerEvents(new MarketMenuListener(this), this);
        getLogger().info("Root-Market enabled — /market /items /shops");
    }

    public String rawMsg(String key) {
        String body = yaml.config().getString("messages." + key);
        if (body == null || body.isBlank()) {
            return key;
        }
        String prefix = yaml.config().getString("messages.prefix", "");
        return prefix + body;
    }

    public String msg(String key) {
        return colorize(rawMsg(key));
    }

    public String colorize(String input) {
        return input == null ? "" : input.replace('&', '\u00A7');
    }
}
