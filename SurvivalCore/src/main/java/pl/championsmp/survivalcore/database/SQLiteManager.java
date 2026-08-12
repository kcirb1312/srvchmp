package pl.championsmp.survivalcore.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class SQLiteManager {

    private final File dbFile;

    public SQLiteManager(File dbFile) {
        this.dbFile = dbFile;
    }

    /**
     * Uruchamia połączenie z SQLite oraz tworzy wymagane tabele.
     *
     * @return true jeżeli baza została poprawnie uruchomiona
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
                                + parent.getAbsolutePath()
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
     * Otwiera nowe połączenie z bazą.
     *
     * Każda operacja otrzymuje własne połączenie,
     * które powinno zostać zamknięte po zakończeniu operacji.
     */
    public Connection getConnection() throws SQLException {
        return openConnection();
    }

    private Connection openConnection() throws SQLException {

        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + dbFile.getAbsolutePath()
        );

        try (Statement statement = connection.createStatement()) {

            statement.execute("PRAGMA busy_timeout = 10000;");
            statement.execute("PRAGMA journal_mode = WAL;");
            statement.execute("PRAGMA synchronous = NORMAL;");
            statement.execute("PRAGMA foreign_keys = ON;");
        }

        return connection;
    }

    /**
     * Połączenia są zamykane lokalnie przez try-with-resources.
     *
     * Metoda pozostawiona dla kompatybilności z resztą pluginu.
     */
    public void disconnect() {
        // Brak globalnego połączenia do zamknięcia.
    }

    /**
     * Tworzy wszystkie wymagane tabele oraz indeksy.
     */
    private void createTables(Connection connection) throws SQLException {

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

        try (Statement statement = connection.createStatement()) {

            statement.execute(createUsersTable);
            statement.execute(createMarketTable);

            statement.execute(
                    "CREATE INDEX IF NOT EXISTS "
                            + "idx_market_expire_time "
                            + "ON market(expire_time);"
            );

            statement.execute(
                    "CREATE INDEX IF NOT EXISTS "
                            + "idx_market_category "
                            + "ON market(category);"
            );

            statement.execute(
                    "CREATE INDEX IF NOT EXISTS "
                            + "idx_users_username "
                            + "ON users(username);"
            );
        }
    }

    /**
     * Zapisuje ofertę rynku w tle.
     *
     * Przedmiot jest serializowany do Base64.
     */
    public CompletableFuture<Boolean> convertAndSaveMarketItemAsync(
            String sellerName,
            ItemStack item,
            double price,
            long expireTime,
            String category
    ) {

        var plugin = Bukkit.getPluginManager()
                .getPlugin("SurvivalCore");

        if (plugin == null || !plugin.isEnabled()) {
            return CompletableFuture.completedFuture(false);
        }

        if (sellerName == null || sellerName.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        if (item == null || item.getType().isAir()) {
            return CompletableFuture.completedFuture(false);
        }

        if (!Double.isFinite(price) || price <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        if (expireTime <= System.currentTimeMillis()) {
            return CompletableFuture.completedFuture(false);
        }

        ItemStack itemCopy = item.clone();

        String finalSellerName = sellerName
                .trim()
                .toLowerCase(Locale.ROOT);

        String finalCategory =
                category == null || category.isBlank()
                        ? MarketCategory.SPECIAL.name()
                        : category.trim().toUpperCase(Locale.ROOT);

        return CompletableFuture.supplyAsync(() -> {

            String base64Item;

            try {

                ByteArrayOutputStream outputStream =
                        new ByteArrayOutputStream();

                try (BukkitObjectOutputStream dataOutput =
                             new BukkitObjectOutputStream(outputStream)) {

                    dataOutput.writeObject(itemCopy);
                }

                base64Item = Base64.getEncoder()
                        .encodeToString(outputStream.toByteArray());

            } catch (Exception e) {

                Bukkit.getLogger().severe(
                        "[SurvivalCore] "
                                + "Nie udało się zserializować "
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
                    Connection connection = getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement(query)
            ) {

                statement.setString(1, finalSellerName);
                statement.setString(2, base64Item);
                statement.setDouble(3, price);
                statement.setLong(4, expireTime);
                statement.setString(5, finalCategory);

                return statement.executeUpdate() == 1;

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

    /**
     * Pobiera wszystkie aktywne oferty rynku.
     */
    public void getActiveMarketItemsAsync(
            Consumer<List<MarketItem>> callback
    ) {

        var plugin = Bukkit.getPluginManager()
                .getPlugin("SurvivalCore");

        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        if (callback == null) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                () -> {

                    List<MarketItem> items = new ArrayList<>();

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
                            Connection connection = getConnection();
                            PreparedStatement statement =
                                    connection.prepareStatement(query)
                    ) {

                        statement.setLong(
                                1,
                                System.currentTimeMillis()
                        );

                        try (ResultSet resultSet =
                                     statement.executeQuery()) {

                            while (resultSet.next()) {

                                int id = resultSet.getInt("id");

                                String seller =
                                        resultSet.getString("seller");

                                String base64Data =
                                        resultSet.getString("item_data");

                                double price =
                                        resultSet.getDouble("price");

                                long expireTime =
                                        resultSet.getLong("expire_time");

                                String categoryString =
                                        resultSet.getString("category");

                                try {

                                    byte[] bytes =
                                            Base64.getDecoder()
                                                    .decode(base64Data);

                                    ItemStack item;

                                    try (
                                            ByteArrayInputStream inputStream =
                                                    new ByteArrayInputStream(bytes);

                                            BukkitObjectInputStream dataInput =
                                                    new BukkitObjectInputStream(
                                                            inputStream
                                                    )
                                    ) {

                                        Object object =
                                                dataInput.readObject();

                                        if (!(object instanceof ItemStack)) {
                                            continue;
                                        }

                                        item = ((ItemStack) object).clone();
                                    }

                                    MarketCategory category =
                                            parseCategory(categoryString);

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
                                                    + "."
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

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> {

                                if (plugin.isEnabled()) {
                                    callback.accept(items);
                                }
                            }
                    );
                }
        );
    }

    /**
     * Bezpieczny zakup oferty.
     *
     * Cała operacja wykonywana jest w jednej transakcji:
     *
     * 1. pobranie oferty,
     * 2. sprawdzenie sprzedawcy,
     * 3. sprawdzenie daty wygaśnięcia,
     * 4. sprawdzenie kupującego,
     * 5. sprawdzenie salda,
     * 6. pobranie pieniędzy,
     * 7. przekazanie pieniędzy sprzedawcy,
     * 8. usunięcie oferty,
     * 9. COMMIT.
     *
     * W przypadku błędu wykonywany jest ROLLBACK.
     */
    public CompletableFuture<PurchaseResult> purchaseMarketItemAsync(
            String buyerName,
            int marketItemId
    ) {

        var plugin = Bukkit.getPluginManager()
                .getPlugin("SurvivalCore");

        if (plugin == null || !plugin.isEnabled()) {

            return CompletableFuture.completedFuture(
                    PurchaseResult.failure(
                            "Plugin jest obecnie niedostępny."
                    )
            );
        }

        if (buyerName == null || buyerName.isBlank()) {

            return CompletableFuture.completedFuture(
                    PurchaseResult.failure(
                            "Nieprawidłowy gracz."
                    )
            );
        }

        if (marketItemId <= 0) {

            return CompletableFuture.completedFuture(
                    PurchaseResult.failure(
                            "Nieprawidłowa oferta."
                    )
            );
        }

        String finalBuyerName = buyerName
                .trim()
                .toLowerCase(Locale.ROOT);

        return CompletableFuture.supplyAsync(() -> {

            try (Connection connection = getConnection()) {

                connection.setAutoCommit(false);

                try {

                    String offerQuery =
                            """
                            SELECT
                                id,
                                seller,
                                item_data,
                                price,
                                expire_time,
                                category
                            FROM market
                            WHERE id = ?
                            LIMIT 1;
                            """;

                    String sellerName;
                    String itemData;
                    double price;
                    long expireTime;
                    String categoryString;

                    try (
                            PreparedStatement statement =
                                    connection.prepareStatement(offerQuery)
                    ) {

                        statement.setInt(1, marketItemId);

                        try (ResultSet resultSet =
                                     statement.executeQuery()) {

                            if (!resultSet.next()) {

                                connection.rollback();

                                return PurchaseResult.failure(
                                        "Oferta nie istnieje lub "
                                                + "została już kupiona."
                                );
                            }

                            sellerName =
                                    resultSet.getString("seller");

                            itemData =
                                    resultSet.getString("item_data");

                            price =
                                    resultSet.getDouble("price");

                            expireTime =
                                    resultSet.getLong("expire_time");

                            categoryString =
                                    resultSet.getString("category");
                        }
                    }

                    if (sellerName == null || sellerName.isBlank()) {

                        connection.rollback();

                        return PurchaseResult.failure(
                                "Oferta ma nieprawidłowego sprzedawcę."
                        );
                    }

                    if (sellerName.equalsIgnoreCase(finalBuyerName)) {

                        connection.rollback();

                        return PurchaseResult.failure(
                                "Nie możesz kupić własnej oferty."
                        );
                    }

                    if (expireTime <= System.currentTimeMillis()) {

                        connection.rollback();

                        return PurchaseResult.failure(
                                "Ta oferta wygasła."
                        );
                    }

                    if (!Double.isFinite(price) || price <= 0) {

                        connection.rollback();

                        return PurchaseResult.failure(
                                "Oferta ma nieprawidłową cenę."
                        );
                    }

                    String buyerQuery =
                            """
                            SELECT money
                            FROM users
                            WHERE username = ?
                            LIMIT 1;
                            """;

                    double buyerBalance;

                    try (
                            PreparedStatement statement =
                                    connection.prepareStatement(buyerQuery)
                    ) {

                        statement.setString(1, finalBuyerName);

                        try (ResultSet resultSet =
                                     statement.executeQuery()) {

                            if (!resultSet.next()) {

                                connection.rollback();

                                return PurchaseResult.failure(
                                        "Nie znaleziono konta kupującego."
                                );
                            }

                            buyerBalance =
                                    resultSet.getDouble("money");
                        }
                    }

                    if (!Double.isFinite(buyerBalance)
                            || buyerBalance < price) {

                        connection.rollback();

                        return PurchaseResult.failure(
                                "Nie masz wystarczającej ilości pieniędzy."
                        );
                    }

                    String sellerQuery =
                            """
                            SELECT id
                            FROM users
                            WHERE username = ?
                            LIMIT 1;
                            """;

                    String finalSellerName = sellerName
                            .trim()
                            .toLowerCase(Locale.ROOT);

                    try (
                            PreparedStatement statement =
                                    connection.prepareStatement(sellerQuery)
                    ) {

                        statement.setString(1, finalSellerName);

                        try (ResultSet resultSet =
                                     statement.executeQuery()) {

                            if (!resultSet.next()) {

                                connection.rollback();

                                return PurchaseResult.failure(
                                        "Nie znaleziono konta sprzedawcy."
                                );
                            }
                        }
                    }

                    ItemStack item;

                    try {

                        byte[] bytes =
                                Base64.getDecoder()
                                        .decode(itemData);

                        try (
                                ByteArrayInputStream inputStream =
                                        new ByteArrayInputStream(bytes);

                                BukkitObjectInputStream dataInput =
                                        new BukkitObjectInputStream(
                                                inputStream
                                        )
                        ) {

                            Object object =
                                    dataInput.readObject();

                            if (!(object instanceof ItemStack)) {

                                connection.rollback();

                                return PurchaseResult.failure(
                                        "Oferta zawiera "
                                                + "uszkodzony przedmiot."
                                );
                            }

                            item = ((ItemStack) object).clone();
                        }

                    } catch (Exception e) {

                        connection.rollback();

                        Bukkit.getLogger().warning(
                                "[SurvivalCore] "
                                        + "Nie można odczytać przedmiotu "
                                        + "oferty ID "
                                        + marketItemId
                        );

                        return PurchaseResult.failure(
                                "Nie można odczytać przedmiotu oferty."
                        );
                    }

                    String removeMoneyQuery =
                            """
                            UPDATE users
                            SET money = money - ?
                            WHERE username = ?
                              AND money >= ?;
                            """;

                    try (
                            PreparedStatement statement =
                                    connection.prepareStatement(
                                            removeMoneyQuery
                                    )
                    ) {

                        statement.setDouble(1, price);
                        statement.setString(2, finalBuyerName);
                        statement.setDouble(3, price);

                        int updated =
                                statement.executeUpdate();

                        if (updated != 1) {

                            connection.rollback();

                            return PurchaseResult.failure(
                                    "Nie udało się pobrać pieniędzy."
                            );
                        }
                    }

                    String addMoneyQuery =
                            """
                            UPDATE users
                            SET money = money + ?
                            WHERE username = ?;
                            """;

                    try (
                            PreparedStatement statement =
                                    connection.prepareStatement(
                                            addMoneyQuery
                                    )
                    ) {

                        statement.setDouble(1, price);
                        statement.setString(2, finalSellerName);

                        int updated =
                                statement.executeUpdate();

                        if (updated != 1) {

                            connection.rollback();

                            return PurchaseResult.failure(
                                    "Nie udało się przekazać "
                                            + "pieniędzy sprzedającemu."
                            );
                        }
                    }

                    String deleteOfferQuery =
                            """
                            DELETE FROM market
                            WHERE id = ?;
                            """;

                    try (
                            PreparedStatement statement =
                                    connection.prepareStatement(
                                            deleteOfferQuery
                                    )
                    ) {

                        statement.setInt(1, marketItemId);

                        int deleted =
                                statement.executeUpdate();

                        if (deleted != 1) {

                            connection.rollback();

                            return PurchaseResult.failure(
                                    "Oferta została zmieniona "
                                            + "lub kupiona przez "
                                            + "innego gracza."
                            );
                        }
                    }

                    connection.commit();

                    MarketCategory category =
                            parseCategory(categoryString);

                    MarketItem purchasedItem =
                            new MarketItem(
                                    marketItemId,
                                    0,
                                    sellerName,
                                    item,
                                    price,
                                    expireTime,
                                    category
                            );

                    return PurchaseResult.success(
                            purchasedItem,
                            price
                    );

                } catch (Exception e) {

                    try {
                        connection.rollback();
                    } catch (SQLException rollbackException) {
                        rollbackException.printStackTrace();
                    }

                    Bukkit.getLogger().severe(
                            "[SurvivalCore] "
                                    + "Błąd podczas zakupu oferty ID "
                                    + marketItemId
                    );

                    e.printStackTrace();

                    return PurchaseResult.failure(
                            "Wystąpił błąd podczas zakupu."
                    );

                } finally {

                    try {
                        connection.setAutoCommit(true);
                    } catch (SQLException ignored) {
                    }
                }

            } catch (SQLException e) {

                Bukkit.getLogger().severe(
                        "[SurvivalCore] "
                                + "Nie można rozpocząć transakcji "
                                + "zakupu."
                );

                e.printStackTrace();

                return PurchaseResult.failure(
                        "Nie udało się połączyć z bazą danych."
                );
            }
        });
    }

    /**
     * Sprawdza asynchronicznie, czy gracz posiada konto.
     */
    public void isRegisteredAsync(
            String username,
            Consumer<Boolean> callback
    ) {

        var plugin = Bukkit.getPluginManager()
                .getPlugin("SurvivalCore");

        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        if (callback == null) {
            return;
        }

        if (username == null || username.isBlank()) {

            Bukkit.getScheduler().runTask(
                    plugin,
                    () -> callback.accept(false)
            );

            return;
        }

        String finalUsername = username
                .trim()
                .toLowerCase(Locale.ROOT);

        Bukkit.getScheduler().runTaskAsynchronously(
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
                            Connection connection = getConnection();
                            PreparedStatement statement =
                                    connection.prepareStatement(query)
                    ) {

                        statement.setString(
                                1,
                                finalUsername
                        );

                        try (ResultSet resultSet =
                                     statement.executeQuery()) {

                            exists = resultSet.next();
                        }

                    } catch (SQLException e) {

                        Bukkit.getLogger().severe(
                                "[SurvivalCore] "
                                        + "Błąd podczas sprawdzania "
                                        + "konta gracza."
                        );

                        e.printStackTrace();
                    }

                    boolean finalExists = exists;

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> {

                                if (plugin.isEnabled()) {
                                    callback.accept(finalExists);
                                }
                            }
                    );
                }
        );
    }

    /**
     * Miejsce na zapis ostatniej lokalizacji gracza.
     *
     * Aktualnie system lokalizacji nie jest jeszcze podłączony
     * do bazy danych.
     */
    public void saveLastLocationAsync(
            String username,
            Location location
    ) {
        // System lokalizacji zostanie podłączony później.
    }

    /**
     * Bezpieczne odczytanie kategorii rynku.
     */
    private MarketCategory parseCategory(String categoryString) {

        if (categoryString == null || categoryString.isBlank()) {
            return MarketCategory.SPECIAL;
        }

        try {

            return MarketCategory.valueOf(
                    categoryString
                            .trim()
                            .toUpperCase(Locale.ROOT)
            );

        } catch (IllegalArgumentException ignored) {

            return MarketCategory.SPECIAL;
        }
    }

    /**
     * Wynik zakupu oferty.
     */
    public record PurchaseResult(
            boolean success,
            String message,
            MarketItem item,
            double price
    ) {

        public static PurchaseResult success(
                MarketItem item,
                double price
        ) {

            return new PurchaseResult(
                    true,
                    "",
                    item,
                    price
            );
        }

        public static PurchaseResult failure(
                String message
        ) {

            return new PurchaseResult(
                    false,
                    message,
                    null,
                    0.0
            );
        }
    }
}