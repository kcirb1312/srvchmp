package pl.championsmp.survivalcore.display;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import pl.championsmp.survivalcore.SurvivalCore;
import pl.championsmp.survivalcore.pvp.PvPDivision;

public class DisplayTask extends BukkitRunnable {

    private final SurvivalCore plugin;

    public DisplayTask(
            SurvivalCore plugin
    ) {
        this.plugin = plugin;
    }

    @Override
    public void run() {

        for (
                Player player :
                Bukkit.getOnlinePlayers()
        ) {

            if (
                    !plugin.getAuthManager()
                            .isLoggedIn(
                                    player.getUniqueId()
                            )
            ) {
                continue;
            }

            updateSidebar(player);
        }
    }

    private void updateSidebar(
            Player player
    ) {

        ScoreboardManager manager =
                Bukkit.getScoreboardManager();

        if (manager == null) {
            return;
        }

        Scoreboard board =
                player.getScoreboard();

        Objective objective =
                board.getObjective(
                        "champion_sb"
                );

        if (objective == null) {

            objective =
                    board.registerNewObjective(
                            "champion_sb",
                            Criteria.DUMMY,
                            ChatColor.DARK_RED
                                    + ""
                                    + ChatColor.BOLD
                                    + "CHAMPION"
                                    + ChatColor.WHITE
                                    + ""
                                    + ChatColor.BOLD
                                    + "SMP.PL"
                    );

            objective.setDisplaySlot(
                    DisplaySlot.SIDEBAR
            );
        }

        /*
         * Bukkit scoreboard API ma problem z aktualizacją
         * identycznych score'ów przy zmienianiu tekstu.
         *
         * Dlatego na tym etapie czyścimy stare linie
         * i ustawiamy nowe.
         */
        for (
                String entry :
                board.getEntries()
        ) {
            board.resetScores(entry);
        }

        String rank =
                plugin.getPlayerManager()
                        .getRank(
                                player.getUniqueId()
                        );

        double balance =
                plugin.getPlayerManager()
                        .getBalance(
                                player.getUniqueId()
                        );

        PvPDivision division =
                plugin.getPvPListener()
                        .getDivision(
                                player.getUniqueId()
                        );

        int pvpPoints =
                plugin.getPvPListener()
                        .getPoints(
                                player.getUniqueId()
                        );

        int score = 10;

        setLine(
                objective,
                ChatColor.DARK_RED
                        + "» "
                        + ChatColor.GRAY
                        + "Ranga: "
                        + ChatColor.RED
                        + rank,
                score--
        );

        setLine(
                objective,
                ChatColor.DARK_RED
                        + "» "
                        + ChatColor.GRAY
                        + "Portfel: "
                        + ChatColor.GREEN
                        + String.format(
                        "%.2f$",
                        balance
                ),
                score--
        );

        setLine(
                objective,
                ChatColor.DARK_GRAY
                        + "----------------",
                score--
        );

        setLine(
                objective,
                ChatColor.DARK_RED
                        + ""
                        + ChatColor.BOLD
                        + " STATYSTYKI PVP",
                score--
        );

        setLine(
                objective,
                ChatColor.GRAY
                        + " • Punkty: "
                        + ChatColor.LIGHT_PURPLE
                        + pvpPoints,
                score--
        );

        setLine(
                objective,
                ChatColor.GRAY
                        + " • Dywizja: "
                        + division.getDisplayName(),
                score--
        );

        setLine(
                objective,
                ChatColor.DARK_GRAY
                        + "----------------",
                score--
        );

        setLine(
                objective,
                ChatColor.GRAY
                        + " Tryb: "
                        + ChatColor.RED
                        + "SURVIVAL",
                score--
        );

        player.setScoreboard(board);
    }

    private void setLine(
            Objective objective,
            String text,
            int score
    ) {

        objective
                .getScore(text)
                .setScore(score);
    }
}