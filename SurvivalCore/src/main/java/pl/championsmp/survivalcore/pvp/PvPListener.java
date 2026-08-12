package pl.championsmp.survivalcore.pvp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.championsmp.survivalcore.SurvivalCore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PvPListener implements Listener {

    private static final int DEFAULT_POINTS = 1000;
    private static final int MAX_KILL_HISTORY = 10;
    private static final int MAX_REPEAT_KILLS = 2;

    private final SurvivalCore plugin;

    private final Map<UUID, LinkedList<UUID>> killHistoryMap =
            new ConcurrentHashMap<>();

    private final Map<UUID, PvPDivision> playerDivisionCache =
            new ConcurrentHashMap<>();

    private final Map<UUID, Integer> playerPointsCache =
            new ConcurrentHashMap<>();

    public PvPListener(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    public PvPDivision getDivision(UUID uuid) {
        return playerDivisionCache.getOrDefault(
                uuid,
                PvPDivision.NORMAL
        );
    }

    public int getPoints(UUID uuid) {
        return playerPointsCache.getOrDefault(
                uuid,
                DEFAULT_POINTS
        );
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                () -> loadPlayerData(player, uuid)
        );
    }

    private void loadPlayerData(
            Player player,
            UUID uuid
    ) {

        int points = DEFAULT_POINTS;
        PvPDivision division = PvPDivision.NORMAL;

        String query = """
                SELECT pvp_points, pvp_division
                FROM users
                WHERE username = ?
                LIMIT 1;
                """;

        try (
                Connection connection =
                        plugin.getDatabaseManager().getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(query)
        ) {

            statement.setString(
                    1,
                    player.getName().toLowerCase()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {

                    points =
                            Math.max(
                                    0,
                                    resultSet.getInt("pvp_points")
                            );

                    String divisionName =
                            resultSet.getString("pvp_division");

                    division =
                            parseDivision(
                                    divisionName,
                                    points
                            );
                }
            }

        } catch (SQLException exception) {

            plugin.getLogger().severe(
                    "Nie udało się załadować danych PvP gracza "
                            + player.getName()
            );

            exception.printStackTrace();

            division =
                    PvPDivision.getByPoints(points);
        }

        final int finalPoints = points;
        final PvPDivision finalDivision = division;

        Bukkit.getScheduler().runTask(
                plugin,
                () -> {

                    if (!player.isOnline()) {
                        return;
                    }

                    playerPointsCache.put(
                            uuid,
                            finalPoints
                    );

                    playerDivisionCache.put(
                            uuid,
                            finalDivision
                    );

                    PvPVisualManager.updatePlayerVisuals(
                            player,
                            finalDivision
                    );
                }
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {

        UUID uuid =
                event.getPlayer().getUniqueId();

        killHistoryMap.remove(uuid);
        playerDivisionCache.remove(uuid);
        playerPointsCache.remove(uuid);
    }

    @EventHandler
    public void onPlayerDeath(
            PlayerDeathEvent event
    ) {

        Player victim =
                event.getEntity();

        Player killer =
                victim.getKiller();

        if (
                killer == null
                        || killer.equals(victim)
        ) {
            return;
        }

        UUID killerUUID =
                killer.getUniqueId();

        UUID victimUUID =
                victim.getUniqueId();

        LinkedList<UUID> history =
                killHistoryMap.computeIfAbsent(
                        killerUUID,
                        ignored -> new LinkedList<>()
                );

        synchronized (history) {

            long repeatedKills =
                    history.stream()
                            .filter(
                                    uuid ->
                                            uuid.equals(victimUUID)
                            )
                            .count();

            if (repeatedKills >= MAX_REPEAT_KILLS) {

                Bukkit.getScheduler().runTask(
                        plugin,
                        () -> killer.sendMessage(
                                "§c§lAnti-Boost §8» §7"
                                        + "Zabójstwo gracza §e"
                                        + victim.getName()
                                        + " §7nie daje punktów "
                                        + "z powodu podejrzenia boostowania."
                        )
                );

                return;
            }

            history.addFirst(victimUUID);

            while (
                    history.size()
                            > MAX_KILL_HISTORY
            ) {
                history.removeLast();
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                () -> processKill(
                        killer,
                        victim
                )
        );
    }

    private void processKill(
            Player killer,
            Player victim
    ) {

        String killerName =
                killer.getName().toLowerCase();

        String victimName =
                victim.getName().toLowerCase();

        try (
                Connection connection =
                        plugin.getDatabaseManager().getConnection()
        ) {

            connection.setAutoCommit(false);

            try {

                int killerPoints =
                        getPointsFromDatabase(
                                connection,
                                killerName
                        );

                int victimPoints =
                        getPointsFromDatabase(
                                connection,
                                victimName
                        );

                int pointDifference =
                        victimPoints - killerPoints;

                int pointsToTransfer =
                        20 + (pointDifference / 20);

                pointsToTransfer =
                        Math.max(
                                5,
                                Math.min(
                                        60,
                                        pointsToTransfer
                                )
                        );

                int newKillerPoints =
                        killerPoints
                                + pointsToTransfer;

                int victimLoss =
                        pointsToTransfer / 2;

                int newVictimPoints =
                        Math.max(
                                0,
                                victimPoints - victimLoss
                        );

                PvPDivision oldKillerDivision =
                        PvPDivision.getByPoints(
                                killerPoints
                        );

                PvPDivision newKillerDivision =
                        PvPDivision.getByPoints(
                                newKillerPoints
                        );

                PvPDivision oldVictimDivision =
                        PvPDivision.getByPoints(
                                victimPoints
                        );

                PvPDivision newVictimDivision =
                        PvPDivision.getByPoints(
                                newVictimPoints
                        );

                updateKiller(
                        connection,
                        killerName,
                        newKillerPoints
                );

                updateVictim(
                        connection,
                        victimName,
                        newVictimPoints
                );

                updateDivision(
                        connection,
                        killerName,
                        newKillerDivision
                );

                updateDivision(
                        connection,
                        victimName,
                        newVictimDivision
                );

                connection.commit();

                final int finalKillerPoints =
                        newKillerPoints;

                final int finalVictimPoints =
                        newVictimPoints;

                final int finalPointsGained =
                        pointsToTransfer;

                Bukkit.getScheduler().runTask(
                        plugin,
                        () -> {

                            playerPointsCache.put(
                                    killer.getUniqueId(),
                                    finalKillerPoints
                            );

                            playerPointsCache.put(
                                    victim.getUniqueId(),
                                    finalVictimPoints
                            );

                            playerDivisionCache.put(
                                    killer.getUniqueId(),
                                    newKillerDivision
                            );

                            playerDivisionCache.put(
                                    victim.getUniqueId(),
                                    newVictimDivision
                            );

                            killer.sendMessage(
                                    "§a§lPvP §8» §7"
                                            + "Zabiłeś gracza §e"
                                            + victim.getName()
                                            + " §a(+"
                                            + finalPointsGained
                                            + " pkt) §8["
                                            + finalKillerPoints
                                            + "]"
                            );

                            victim.sendMessage(
                                    "§c§lPvP §8» §7"
                                            + "Zostałeś zabity przez §e"
                                            + killer.getName()
                                            + " §c(-"
                                            + (finalPointsGained / 2)
                                            + " pkt) §8["
                                            + finalVictimPoints
                                            + "]"
                            );

                            PvPVisualManager
                                    .updatePlayerVisuals(
                                            killer,
                                            newKillerDivision
                                    );

                            PvPVisualManager
                                    .updatePlayerVisuals(
                                            victim,
                                            newVictimDivision
                                    );

                            if (
                                    oldKillerDivision
                                            != newKillerDivision
                            ) {

                                killer.sendMessage(
                                        "§6§lPvP §8» §7"
                                                + "Awansowałeś do dywizji "
                                                + newKillerDivision
                                                .getDisplayName()
                                );

                                if (
                                        newKillerDivision
                                                == PvPDivision.EMERALD
                                ) {

                                    Bukkit.broadcastMessage(
                                            "§a§lCHAMPION KILLS! "
                                                    + "§fGracz §e"
                                                    + killer.getName()
                                                    + " §fawansował do dywizji "
                                                    + newKillerDivision
                                                    .getDisplayName()
                                                    + "§f!"
                                    );

                                    /*
                                     * Nie wykonujemy tutaj ślepo
                                     * komendy z zewnętrznego pluginu.
                                     *
                                     * Gdy będziemy mieć system nagród,
                                     * podepniemy go przez własny manager.
                                     */
                                }
                            }

                            if (
                                    oldVictimDivision
                                            != newVictimDivision
                            ) {

                                victim.sendMessage(
                                        "§c§lPvP §8» §7"
                                                + "Spadłeś do dywizji "
                                                + newVictimDivision
                                                .getDisplayName()
                                );
                            }
                        }
                );

            } catch (Exception exception) {

                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    rollbackException.printStackTrace();
                }

                throw exception;
            }

        } catch (Exception exception) {

            plugin.getLogger().severe(
                    "Błąd podczas przetwarzania PvP kill."
            );

            exception.printStackTrace();

            Bukkit.getScheduler().runTask(
                    plugin,
                    () -> killer.sendMessage(
                            "§c§lPvP §8» §7"
                                    + "Nie udało się zapisać punktów. "
                                    + "Spróbuj ponownie później."
                    )
            );
        }
    }

    private int getPointsFromDatabase(
            Connection connection,
            String username
    ) throws SQLException {

        String query = """
                SELECT pvp_points
                FROM users
                WHERE username = ?
                LIMIT 1;
                """;

        try (
                PreparedStatement statement =
                        connection.prepareStatement(query)
        ) {

            statement.setString(
                    1,
                    username
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (resultSet.next()) {

                    return Math.max(
                            0,
                            resultSet.getInt(
                                    "pvp_points"
                            )
                    );
                }
            }
        }

        return DEFAULT_POINTS;
    }

    private void updateKiller(
            Connection connection,
            String username,
            int points
    ) throws SQLException {

        String query = """
                UPDATE users
                SET kills = kills + 1,
                    pvp_points = ?
                WHERE username = ?;
                """;

        try (
                PreparedStatement statement =
                        connection.prepareStatement(query)
        ) {

            statement.setInt(
                    1,
                    points
            );

            statement.setString(
                    2,
                    username
            );

            statement.executeUpdate();
        }
    }

    private void updateVictim(
            Connection connection,
            String username,
            int points
    ) throws SQLException {

        String query = """
                UPDATE users
                SET deaths = deaths + 1,
                    pvp_points = ?
                WHERE username = ?;
                """;

        try (
                PreparedStatement statement =
                        connection.prepareStatement(query)
        ) {

            statement.setInt(
                    1,
                    points
            );

            statement.setString(
                    2,
                    username
            );

            statement.executeUpdate();
        }
    }

    private void updateDivision(
            Connection connection,
            String username,
            PvPDivision division
    ) throws SQLException {

        String query = """
                UPDATE users
                SET pvp_division = ?
                WHERE username = ?;
                """;

        try (
                PreparedStatement statement =
                        connection.prepareStatement(query)
        ) {

            statement.setString(
                    1,
                    division.name()
            );

            statement.setString(
                    2,
                    username
            );

            statement.executeUpdate();
        }
    }

    private PvPDivision parseDivision(
            String name,
            int points
    ) {

        if (name == null) {
            return PvPDivision.getByPoints(points);
        }

        try {
            return PvPDivision.valueOf(
                    name.toUpperCase()
            );
        } catch (IllegalArgumentException exception) {
            return PvPDivision.getByPoints(points);
        }
    }
}