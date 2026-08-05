package com.mercdev.playerlog;

import org.bukkit.plugin.java.JavaPlugin;

import com.mercdev.playerlog.command.PlayerLogCommand;
import com.mercdev.playerlog.storage.LogStorage;

import java.sql.SQLException;

public class PlayerLogPlugin extends JavaPlugin {

    private LogStorage storage;

    @Override
    public void onEnable() {
        storage = new LogStorage(this);
        try {
            storage.init();
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize logs.db, disabling PlayerLog: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PlayerLogCommand executor = new PlayerLogCommand(this, storage);
        var cmd = getCommand("playerlog");
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);

        getLogger().info("PlayerLog enabled - " +
                storage.getKnownPlayerNames().size() + " players already have log entries.");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.close();
        }
    }
}
