package pl.championsmp.survivalcore;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {

    private final SurvivalCore plugin;

    private final Map<UUID, Integer> playtimeCache =
            new ConcurrentHashMap<>();

    private final Map<UUID, Double> balanceCache =
            new ConcurrentHashMap<>();

    private final Map<UUID, String> rankCache =
            new ConcurrentHashMap<>();

    private final Map<UUID, String> usernameCache =
            new ConcurrentHashMap<>();

    public PlayerManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    public void loadPlayerData(Player player) {

        UUID uuid = player.getUniqueId();
        String username = player.getName().toLowerCase();

        usernameCache.put(uuid, username);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            double money = 0.0;
            String rank = "PLAYER";
            int playtime = 0;

            String query = """
                SELECT money, rank, playtime
                FROM users
                WHERE username = ?
                LIMIT 1;
                """;

            try (Connection connection =
                         plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(query)) {

                statement.setString(1, username);

                try (ResultSet resultSet = statement.executeQuery()) {

                    if (resultSet.next()) {
                        money = resultSet.getDouble("money");

                        String databaseRank =
                                resultSet.getString("rank");

                        if (databaseRank != null &&
                                !databaseRank.isBlank()) {
                            rank = databaseRank.toUpperCase();
                        }

                        playtime =
                                resultSet.getInt("playtime");
                    }
                }

            } catch (SQLException e) {

                plugin.getLogger().severe(
                        "Nie udało się załadować danych gracza "
                                + player.getName()
                );

                e.printStackTrace();
            }

            final double finalMoney = money;
            final String finalRank = rank;
            final int finalPlaytime = playtime;

            Bukkit.getScheduler().runTask(plugin, () -> {

                if (!player.isOnline()) {
                    return;
                }

                balanceCache.put(uuid, finalMoney);
                rankCache.put(uuid, finalRank);
                playtimeCache.put(uuid, finalPlaytime);
            });
        });
    }

    public void removePlayerFromCache(UUID uuid) {
        balanceCache.remove(uuid);
        rankCache.remove(uuid);
        playtimeCache.remove(uuid);
        usernameCache.remove(uuid);
    }

    public double getBalance(UUID uuid) {
        return balanceCache.getOrDefault(uuid, 0.0);
    }

    public void addMoney(UUID uuid, double amount) {

        if (!Double.isFinite(amount) || amount <= 0) {
            return;
        }

        double newBalance =
                getBalance(uuid) + amount;

        balanceCache.put(uuid, newBalance);

        saveFieldAsync(
                uuid,
                "money",
                newBalance
        );
    }

    public boolean removeMoney(UUID uuid, double amount) {

        if (!Double.isFinite(amount) || amount <= 0) {
            return false;
        }

        double current =
                getBalance(uuid);

        if (current < amount) {
            return false;
        }

        double newBalance =
                current - amount;

        balanceCache.put(uuid, newBalance);

        saveFieldAsync(
                uuid,
                "money",
                newBalance
        );

        return true;
    }

    public String getRank(UUID uuid) {
        return rankCache.getOrDefault(
                uuid,
                "PLAYER"
        );
    }

    public void setRank(
            UUID uuid,
            String rankName
    ) {

        if (rankName == null ||
                rankName.isBlank()) {
            return;
        }

        String normalized =
                rankName.trim().toUpperCase();

        rankCache.put(
                uuid,
                normalized
        );

        saveFieldAsync(
                uuid,
                "rank",
                normalized
        );
    }

    public int getPlaytimePoints(UUID uuid) {
        return playtimeCache.getOrDefault(
                uuid,
                0
        );
    }

    public boolean removePlaytimePoints(
            UUID uuid,
            int amount
    ) {

        if (amount <= 0) {
            return false;
        }

        int current =
                getPlaytimePoints(uuid);

        if (current < amount) {
            return false;
        }

        int newValue =
                current - amount;

        playtimeCache.put(
                uuid,
                newValue
        );

        saveFieldAsync(
                uuid,
                "playtime",
                newValue
        );

        return true;
    }

    private void saveFieldAsync(
            UUID uuid,
            String field,
            Object value
    ) {

        String username =
                usernameCache.get(uuid);

        if (username == null) {

            Player player =
                    Bukkit.getPlayer(uuid);

            if (player == null) {
                return;
            }

            username =
                    player.getName().toLowerCase();

            usernameCache.put(
                    uuid,
                    username
            );
        }

        final String finalUsername =
                username;

        Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                () -> {

                    String query =
                            "UPDATE users SET "
                                    + field
                                    + " = ? WHERE username = ?;";

                    try (
                            Connection connection =
                                    plugin.getDatabaseManager()
                                            .getConnection();

                            PreparedStatement statement =
                                    connection.prepareStatement(query)
                    ) {

                        if (value instanceof Integer integer) {
                            statement.setInt(
                                    1,
                                    integer
                            );

                        } else if (value instanceof Long longValue) {
                            statement.setLong(
                                    1,
                                    longValue
                            );

                        } else if (value instanceof Double doubleValue) {
                            statement.setDouble(
                                    1,
                                    doubleValue
                            );

                        } else {
                            statement.setString(
                                    1,
                                    String.valueOf(value)
                            );
                        }

                        statement.setString(
                                2,
                                finalUsername
                        );

                        statement.executeUpdate();

                    } catch (SQLException e) {

                        plugin.getLogger().severe(
                                "Nie udało się zapisać pola "
                                        + field
                                        + " gracza "
                                        + finalUsername
                        );

                        e.printStackTrace();
                    }
                }
        );
    }
}