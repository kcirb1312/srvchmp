package pl.championsmp.survivalcore.pvp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class PvPVisualManager {

    // Aktualizuje wygląd gracza nad głową, na TABie oraz ustawia format nicku
    public static void updatePlayerVisuals(Player player, PvPDivision division) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "pvp_" + division.name().toLowerCase();

        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        // Prefiks wyświetlany na TABie oraz nad głową gracza
        String prefix = division.getDisplayName() + " §r";
        team.setPrefix(prefix);

        // Przypisujemy gracza do odpowiedniego teamu wizualnego
        team.addEntry(player.getName());

        // Aktualizacja formatu na liście TAB (górna tabela)
        player.setPlayerListName(prefix + player.getName());
    }
}
