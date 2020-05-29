package org.screamingsandals.bedwars;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.screamingsandals.bedwars.api.Permissions;
import org.screamingsandals.bedwars.commands.BedWarsCommand;
import org.screamingsandals.bedwars.commands.CommandsLanguage;
import org.screamingsandals.bedwars.config.MainConfig;
import org.screamingsandals.bedwars.config.VisualsConfig;
import org.screamingsandals.bedwars.game.Game;
import org.screamingsandals.bedwars.listeners.PlayerCoreListener;
import org.screamingsandals.lib.commands.Commands;
import org.screamingsandals.lib.config.ConfigAdapter;
import org.screamingsandals.lib.debug.Debug;
import org.screamingsandals.lib.gamecore.GameCore;
import org.screamingsandals.lib.gamecore.core.GameManager;
import org.screamingsandals.lib.gamecore.core.GameType;
import org.screamingsandals.lib.gamecore.exceptions.GameCoreException;
import org.screamingsandals.lib.gamecore.language.GameLanguage;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

public class Main extends JavaPlugin {
    private static Main instance;
    private MainConfig mainConfig;
    private VisualsConfig visualsConfig;
    @Getter
    private GameCore gameCore;
    private GameManager<Game> gameManager;
    @Getter
    private GameLanguage language;
    private Commands commands;

    @Getter
    private File shopFile;
    @Getter
    private File upgradesFile;
    @Getter
    private boolean bungee;

    @Override
    @SuppressWarnings("unchecked")
    public void onEnable() {
        Debug.setFallbackName("[SBedWars] ");
        instance = this;
        try {
            mainConfig = new MainConfig(ConfigAdapter.createFile(getDataFolder(), "config.yml"));
            mainConfig.load();
            bungee = mainConfig.getBoolean(MainConfig.ConfigPaths.BUNGEE_ENABLED);

            visualsConfig = new VisualsConfig(ConfigAdapter.createFile(getDataFolder(), "visuals.yml"));
            visualsConfig.load();

            var shopFileName = "shop.yml";
            var upgradesFileName = "upgrades.yml";

            if (mainConfig.getBoolean(MainConfig.ConfigPaths.GROOVY)) {
                shopFileName = "shop.groovy";
                upgradesFileName = "upgrades.groovy";
            }

            shopFile = checkIfExistsOrCopyDefault(getDataFolder(), shopFileName);
            upgradesFile = checkIfExistsOrCopyDefault(getDataFolder(), upgradesFileName);

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        language = new GameLanguage(this, mainConfig.getString("language"), mainConfig.getString("prefix"));

        Debug.init(getName());
        Debug.setDebug(mainConfig.getBoolean("debug"));

        commands = new Commands(this);
        commands.load();
        commands.setCommandLanguage(new CommandsLanguage());

        //Make sure that we destroy existing instance of the  core, fucking reloads
        if (gameCore != null) {
            gameCore.destroy();
            Debug.info("GameCore already exists, destroying and loading new!");
        }

        gameCore = new GameCore(this, BedWarsCommand.COMMAND_NAME, Permissions.ADMIN_COMMAND, mainConfig.getBoolean(MainConfig.ConfigPaths.VERBOSE));
        gameCore.setVisualsConfig(visualsConfig);

        try {
            commands.loadScreamingCommands(gameCore.getClass());
        } catch (URISyntaxException | IOException e) {
            e.printStackTrace();
        }

        try {
            gameCore.load(new File(getDataFolder(), "games"), Game.class, getGameType());
        } catch (GameCoreException e) {
            Debug.info("This is some way of fuck up.. Please report that to our GitHub or Discord!", true);
            e.printStackTrace();
            return;
        }

        gameManager = (GameManager<Game>) GameCore.getGameManager();
        gameManager.loadGames();

        //Beware of plugin reloading..
        final Collection<Player> onlinePlayers = (Collection<Player>) Bukkit.getOnlinePlayers();
        if (onlinePlayers.size() > 0) {
            onlinePlayers.forEach(player -> GameCore.getPlayerManager().registerPlayer(player));
            System.out.println(GameCore.getPlayerManager().getRegisteredPlayers());
        }

        registerListeners();

        Debug.info("&e------------ &aEverything is loaded! :) &e------------");
    }

    @Override
    public void onDisable() {
        //TODO: use paper's getServer().isStopping(); to see if we are reloading
        gameCore.destroy();
        commands.destroy();
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerCoreListener(), this);
    }

    private File checkIfExistsOrCopyDefault(File folder, String fileName) {
        final var file = new File(folder, fileName);
        if (file.exists()) {
            return file;
        } else {
            saveResource(fileName, false);
        }

        return file;
    }

    private GameType getGameType() {
        if (bungee) {
            if (mainConfig.getBoolean(MainConfig.ConfigPaths.BUNGEE_MULTI_GAME_MODE)) {
                return GameType.MULTI_GAME_BUNGEE;
            }
            return GameType.SINGLE_GAME_BUNGEE;
        } else {
            return GameType.MULTI_GAME;
        }
    }

    public static Main getInstance() {
        return instance;
    }

    public static GameManager<Game> getGameManager() {
        return instance.gameManager;
    }

    public static MainConfig getMainConfig() {
        return instance.mainConfig;
    }

    public static VisualsConfig getVisualsConfig() {
        return instance.visualsConfig;
    }
}
