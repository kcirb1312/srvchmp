package pl.championsmp.survivalcore.display;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import pl.championsmp.survivalcore.SurvivalCore;
import pl.championsmp.survivalcore.pvp.PvPDivision;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SidebarScoreboard {

    public static void createSidebar(SurvivalCore plugin, Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();

        // Główne obramowanie i nazwa serwera
        Objective obj = board.registerNewObjective("champion_sb", Criteria.DUMMY, "§e§lCHAMPIONSMP.PL");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        UUID uuid = player.getUniqueId();

        // Pobieramy dane z bazy w tle, aby tablica nie wywoływała lagów
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int kills = 0, deaths = 0, pvpPoints = 0, playtime = 0;
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = "SELECT kills, deaths, pvp_points, playtime FROM users WHERE username = ?;";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, player.getName().toLowerCase());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            kills = rs.getInt("kills");
                            deaths = rs.getInt("deaths");
                            pvpPoints = rs.getInt("pvp_points");
                            playtime = rs.getInt("playtime");
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            // Deklaracja zmiennych finalnych do przekazania na wątek główny
            final int fKills = kills;
            final int fDeaths = deaths;
            final int fPoints = pvpPoints;
            final int fTime = playtime;

            Bukkit.getScheduler().runTask(plugin, () -> {
                String rankPremium = plugin.getPlayerManager().getRank(uuid);
                double money = plugin.getPlayerManager().getBalance(uuid);
                PvPDivision div = PvPDivision.getByPoints(fPoints);

                // Zmieniamy czas z minut na czytelny format godzinowy
                String hoursFormatted = (fTime / 60) + "h " + (fTime % 60) + "m";

                replaceScore(obj, "§7§m--------------------", 12);
                replaceScore(obj, "§8• §7Ranga: §e" + rankPremium, 11);
                replaceScore(obj, "§8• §7Portfel: §a" + String.format("%.2f", money) + "zł", 10);
                replaceScore(obj, "§7 ", 9);
                replaceScore(obj, "§6§lSTATYSTYKI PVP", 8);
                replaceScore(obj, "§8• §7Dywizja: " + div.getDisplayName(), 7);
                replaceScore(obj, "§8• §7Punkty Ranking: §b" + fPoints, 6);
                replaceScore(obj, "§8• §7Zabójstwa: §f" + fKills, 5);
                replaceScore(obj, "§8• §7Śmierci: §c" + fDeaths, 4);
                replaceScore(obj, "§0 ", 3);
                replaceScore(obj, "§8• §7Czas Gry: §d" + hoursFormatted, 2);
                replaceScore(obj, "§7§m-------------------- ", 1);

                player.setScoreboard(board);
            });
        });
    }

    private static void replaceScore(Objective obj, String text, int score) {
        obj.getScore(text).setScore(score);
    }
}
