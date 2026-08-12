package pl.championsmp.survivalcore;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import pl.championsmp.survivalcore.auth.AuthListener;
import pl.championsmp.survivalcore.auth.AuthManager;
import pl.championsmp.survivalcore.commands.AuthCommands;
import pl.championsmp.survivalcore.commands.ShopCommands;
import pl.championsmp.survivalcore.database.SQLiteManager;
import pl.championsmp.survivalcore.display.DisplayTask;
import pl.championsmp.survivalcore.display.VisualRegister;
import pl.championsmp.survivalcore.gui.market.MarketGUIManager;
import pl.championsmp.survivalcore.gui.market.MenuListener;
import pl.championsmp.survivalcore.pvp.PvPListener;

import java.io.File;

public final class SurvivalCore extends JavaPlugin {

    private static SurvivalCore instance;

    private SQLiteManager databaseManager;
    private AuthManager authManager;
    private PlayerManager playerManager;
    private MarketGUIManager marketGUIManager;
    private PvPListener pvpListener;

    @Override
    public void onEnable() {

        instance = this;

        if (!getDataFolder().exists()
                && !getDataFolder().mkdirs()) {

            getLogger().severe(
                    "Nie można utworzyć folderu pluginu."
            );

            getServer()
                    .getPluginManager()
                    .disablePlugin(this);

            return;
        }

        databaseManager =
                new SQLiteManager(
                        new File(
                                getDataFolder(),
                                "storage.db"
                        )
                );

        if (!databaseManager.connect()) {

            getLogger().severe(
                    "Nie udało się uruchomić SQLite. "
                            + "Plugin zostanie wyłączony."
            );

            getServer()
                    .getPluginManager()
                    .disablePlugin(this);

            return;
        }

        authManager =
                new AuthManager();

        playerManager =
                new PlayerManager(this);

        marketGUIManager =
                new MarketGUIManager();

        registerListeners();
        registerCommands();

        new DisplayTask(this)
                .runTaskTimer(
                        this,
                        20L,
                        20L
                );

        getLogger().info(
                "[+] SurvivalCore został uruchomiony."
        );
    }

    private void registerListeners() {

        getServer()
                .getPluginManager()
                .registerEvents(
                        new AuthListener(this),
                        this
                );

        getServer()
                .getPluginManager()
                .registerEvents(
                        new VisualRegister(this),
                        this
                );

        /*
         * PvPListener musi być utworzony tylko raz.
         *
         * Inne klasy korzystają z tej samej instancji
         * przez getPvPListener(), dlatego zapisujemy ją
         * w polu klasy.
         */
        pvpListener =
                new PvPListener(this);

        getServer()
                .getPluginManager()
                .registerEvents(
                        pvpListener,
                        this
                );

        getServer()
                .getPluginManager()
                .registerEvents(
                        new MenuListener(this),
                        this
                );
    }

    private void registerCommands() {

        AuthCommands authCommands =
                new AuthCommands(this);

        registerExecutor(
                "register",
                authCommands
        );

        registerExecutor(
                "login",
                authCommands
        );

        ShopCommands shopCommands =
                new ShopCommands(this);

        registerExecutor(
                "sklep",
                shopCommands
        );

        registerExecutor(
                "rynek",
                shopCommands
        );

        registerExecutor(
                "wystaw",
                shopCommands
        );
    }

    private void registerExecutor(
            String commandName,
            org.bukkit.command.CommandExecutor executor
    ) {

        PluginCommand command =
                getCommand(commandName);

        if (command == null) {

            getLogger().severe(
                    "Nie znaleziono komendy /"
                            + commandName
                            + " w plugin.yml!"
            );

            return;
        }

        command.setExecutor(executor);
    }

    @Override
    public void onDisable() {

        if (authManager != null) {
            authManager.clear();
        }

        if (databaseManager != null) {
            databaseManager.disconnect();
        }

        instance = null;

        getLogger().info(
                "[-] SurvivalCore został wyłączony."
        );
    }

    public static SurvivalCore getInstance() {
        return instance;
    }

    public SQLiteManager getDatabaseManager() {
        return databaseManager;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public MarketGUIManager getMarketGUIManager() {
        return marketGUIManager;
    }

    public PvPListener getPvPListener() {
        return pvpListener;
    }
}