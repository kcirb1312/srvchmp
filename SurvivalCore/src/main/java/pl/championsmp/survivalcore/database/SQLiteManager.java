package pl.championsmp.survivalcore.database;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import pl.championsmp.survivalcore.gui.market.MarketCategory;
import pl.championsmp.survivalcore.gui.market.MarketItem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class SQLiteManager {

    private final File dbFile;

    public SQLiteManager(File dbFile) {
        this.dbFile = dbFile;
    }

    /**
     * Inicjalizuje sterownik SQLite i tworzy wymagane tabele.
     *
     * Nie przechowujemy jednego Connection przez cały czas życia pluginu.
     * Każda operacja pobiera własne połączenie i zamyka je po zakończeniu.
     */
    public boolean connect() {

        try {

            Class.forName("org.sqlite.JDBC");

            File parent = dbFile.getParentFile();

            if (parent != null
                    && !parent.exists()
                    && !parent.mkdirs()) {

                throw new SQLException(
                        "Nie można utworzyć katalogu bazy danych: "
                                + parent
                );
            }

            try (Connection connection = openConnection()) {
                createTables(connection);
            }

            return true;

        } catch (Exception e) {

            Bukkit.getLogger().severe(
                    "[SurvivalCore] Nie można uruchomić SQLite."
            );

            e.printStackTrace();

            return false;
        }
    }

    /**
     * Otwiera nowe połączenie SQLite.
     *
     * Kod wywołujący tę metodę powinien zamknąć Connection
     * przez try-with-resources.
     */
    public Connection getConnection() throws SQLException {
        return openConnection();
    }

    private Connection openConnection() throws SQLException {

        Connection connection =
                DriverManager.getConnection(
                        "jdbc:sqlite:"
                                + dbFile.getAbsolutePath()
                );

        try (Statement statement =
                     connection.createStatement()) {

            statement.execute(
                    "PRAGMA busy_timeout = 10000;"
            );

            statement.execute(
                    "PRAGMA journal_mode = WAL;"
            );

            statement.execute(
                    "PRAGMA synchronous = NORMAL;"
            );

            statement.execute(
                    "PRAGMA foreign_keys = ON;"
            );
        }

        return connection;
    }

    /**
     * Nie ma globalnego Connection do zamknięcia.
     * Każde połączenie zamykane jest przez try-with-resources.
     */
    public void disconnect() {
        // Brak globalnego Connection.
    }

    private void createTables(
            Connection connection
    ) throws SQLException {

        String createUsersTable =
                """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password TEXT NOT NULL,
                    rank TEXT NOT NULL DEFAULT 'PLAYER',
                    money REAL NOT NULL DEFAULT 0.0,
                    playtime INTEGER NOT NULL DEFAULT 0,
                    kills INTEGER NOT NULL DEFAULT 0,
                    deaths INTEGER NOT NULL DEFAULT 0,
                    pvp_points INTEGER NOT NULL DEFAULT 0,
                    pvp_division TEXT NOT NULL DEFAULT 'NORMAL'
                );
                """;

        String createMarketTable =
                """
                CREATE TABLE IF NOT EXISTS market (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    seller TEXT NOT NULL,
                    item_data TEXT NOT NULL,
                    price REAL NOT NULL,
                    expire_time BIGINT NOT NULL,
                    category TEXT NOT NULL DEFAULT 'SPECIAL',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                """;

        try (Statement statement =
                     connection.createStatement()) {

            statement.execute(createUsersTable);
            statement.execute(createMarketTable);

            statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_market_expire_time "
                            + "ON market(expire_time);"
            );

            statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_market_category "
                            + "ON market(category);"
            );

            statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_users_username "
                            + "ON users(username);"
            );
        }
    }

    /**
     * Zapisuje ofertę rynku asynchronicznie.
     *
     * TRUE  = oferta została faktycznie zapisana w SQLite.
     * FALSE = zapis się nie udał.
     */
    public CompletableFuture<Boolean> convertAndSaveMarketItemAsync(
            String sellerName,
            ItemStack item,
            double price,
            long expireTime,
            String category
    ) {

        var plugin =
                Bukkit.getPluginManager()
                        .getPlugin("SurvivalCore");

        if (plugin == null || !plugin.isEnabled()) {
            return CompletableFuture.completedFuture(false);
        }

        if (sellerName == null
                || sellerName.isBlank()) {

            return CompletableFuture.completedFuture(false);
        }

        if (item == null
                || item.getType().isAir()) {

            return CompletableFuture.completedFuture(false);
        }

        if (!Double.isFinite(price)
                || price <= 0) {

            return CompletableFuture.completedFuture(false);
        }

        if (expireTime <= System.currentTimeMillis()) {

            return CompletableFuture.completedFuture(false);
        }

        /*
         * Kopia jest tworzona jeszcze przed przejściem
         * na wątek asynchroniczny.
         */
        ItemStack itemCopy = item.clone();

        String finalSellerName =
                sellerName.toLowerCase();

        return CompletableFuture.supplyAsync(() -> {

            String base64Item;

            try {

                ByteArrayOutputStream outputStream =
                        new ByteArrayOutputStream();

                try (BukkitObjectOutputStream dataOutput =
                             new BukkitObjectOutputStream(
                                     outputStream
                             )) {

                    dataOutput.writeObject(
                            itemCopy
                    );
                }

                base64Item =
                        Base64.getEncoder()
                                .encodeToString(
                                        outputStream.toByteArray()
                                );

            } catch (Exception e) {

                Bukkit.getLogger().severe(
                        "[SurvivalCore] "
                                + "Nie udało się serializować "
                                + "przedmiotu rynku."
                );

                e.printStackTrace();

                return false;
            }

            String query =
                    """
                    INSERT INTO market
                    (
                        seller,
                        item_data,
                        price,
                        expire_time,
                        category
                    )
                    VALUES (?, ?, ?, ?, ?);
                    """;

            try (
                    Connection connection =
                            getConnection();

                    PreparedStatement statement =
                            connection.prepareStatement(query)
            ) {

                statement.setString(
                        1,
                        finalSellerName
                );

                statement.setString(
                        2,
                        base64Item
                );

                statement.setDouble(
                        3,
                        price
                );

                statement.setLong(
                        4,
                        expireTime
                );

                statement.setString(
                        5,
                        category
                );

                int affectedRows =
                        statement.executeUpdate();

                return affectedRows == 1;

            } catch (SQLException e) {

                Bukkit.getLogger().severe(
                        "[SurvivalCore] "
                                + "Nie udało się zapisać "
                                + "oferty rynku."
                );

                e.printStackTrace();

                return false;
            }

        });
    }

    public void getActiveMarketItemsAsync(
            Consumer<List<MarketItem>> callback
    ) {

        var plugin =
                Bukkit.getPluginManager()
                        .getPlugin("SurvivalCore");

        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {

                            List<MarketItem> items =
                                    new ArrayList<>();

                            String query =
                                    """
                                    SELECT
                                        id,
                                        seller,
                                        item_data,
                                        price,
                                        expire_time,
                                        category
                                    FROM market
                                    WHERE expire_time > ?
                                    ORDER BY id DESC;
                                    """;

                            try (
                                    Connection connection =
                                            getConnection();

                                    PreparedStatement statement =
                                            connection.prepareStatement(
                                                    query
                                            )
                            ) {

                                statement.setLong(
                                        1,
                                        System.currentTimeMillis()
                                );

                                try (
                                        ResultSet resultSet =
                                                statement.executeQuery()
                                ) {

                                    while (resultSet.next()) {

                                        int id =
                                                resultSet.getInt(
                                                        "id"
                                                );

                                        String seller =
                                                resultSet.getString(
                                                        "seller"
                                                );

                                        String base64Data =
                                                resultSet.getString(
                                                        "item_data"
                                                );

                                        double price =
                                                resultSet.getDouble(
                                                        "price"
                                                );

                                        long expireTime =
                                                resultSet.getLong(
                                                        "expire_time"
                                                );

                                        String categoryString =
                                                resultSet.getString(
                                                        "category"
                                                );

                                        try {

                                            byte[] bytes =
                                                    Base64.getDecoder()
                                                            .decode(
                                                                    base64Data
                                                            );

                                            ItemStack item;

                                            try (
                                                    ByteArrayInputStream inputStream =
                                                            new ByteArrayInputStream(
                                                                    bytes
                                                            );

                                                    BukkitObjectInputStream dataInput =
                                                            new BukkitObjectInputStream(
                                                                    inputStream
                                                            )
                                            ) {

                                                Object object =
                                                        dataInput.readObject();

                                                if (!(object
                                                        instanceof ItemStack loadedItem)) {

                                                    continue;
                                                }

                                                item = loadedItem;
                                            }

                                            MarketCategory category;

                                            try {

                                                category =
                                                        MarketCategory.valueOf(
                                                                categoryString
                                                                        .toUpperCase()
                                                        );

                                            } catch (Exception ignored) {

                                                category =
                                                        MarketCategory.SPECIAL;
                                            }

                                            items.add(
                                                    new MarketItem(
                                                            id,
                                                            0,
                                                            seller,
                                                            item,
                                                            price,
                                                            expireTime,
                                                            category
                                                    )
                                            );

                                        } catch (Exception e) {

                                            Bukkit.getLogger().warning(
                                                    "[SurvivalCore] "
                                                            + "Nie udało się odczytać "
                                                            + "itemu rynku ID "
                                                            + id
                                                            + ". Oferta zostanie pominięta."
                                            );
                                        }
                                    }
                                }

                            } catch (SQLException e) {

                                Bukkit.getLogger().severe(
                                        "[SurvivalCore] "
                                                + "Błąd podczas pobierania rynku."
                                );

                                e.printStackTrace();
                            }

                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {

                                                if (plugin.isEnabled()) {
                                                    callback.accept(
                                                            items
                                                    );
                                                }
                                            }
                                    );
                        }
                );
    }

    public void isRegisteredAsync(
            String username,
            Consumer<Boolean> callback
    ) {

        var plugin =
                Bukkit.getPluginManager()
                        .getPlugin("SurvivalCore");

        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        if (username == null
                || username.isBlank()) {

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> callback.accept(false)
                    );

            return;
        }

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {

                            boolean exists = false;

                            String query =
                                    """
                                    SELECT 1
                                    FROM users
                                    WHERE username = ?
                                    LIMIT 1;
                                    """;

                            try (
                                    Connection connection =
                                            getConnection();

                                    PreparedStatement statement =
                                            connection.prepareStatement(
                                                    query
                                            )
                            ) {

                                statement.setString(
                                        1,
                                        username.toLowerCase()
                                );

                                try (
                                        ResultSet resultSet =
                                                statement.executeQuery()
                                ) {

                                    exists =
                                            resultSet.next();
                                }

                            } catch (SQLException e) {

                                Bukkit.getLogger().severe(
                                        "[SurvivalCore] "
                                                + "Błąd podczas sprawdzania "
                                                + "konta gracza."
                                );

                                e.printStackTrace();
                            }

                            boolean finalExists =
                                    exists;

                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {

                                                if (plugin.isEnabled()) {
                                                    callback.accept(
                                                            finalExists
                                                    );
                                                }
                                            }
                                    );
                        }
                );
    }

    public void saveLastLocationAsync(
            String username,
            org.bukkit.Location location
    ) {

        /*
         * System lokalizacji nie jest jeszcze
         * zaimplementowany.
         */
    }
}