package pl.championsmp.survivalcore.display;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.championsmp.survivalcore.SurvivalCore;
import pl.championsmp.survivalcore.pvp.PvPDivision;

public class VisualRegister implements Listener {

    private final SurvivalCore plugin;

    public VisualRegister(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        plugin.getPlayerManager()
                .loadPlayerData(
                        event.getPlayer()
                );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {

        plugin.getPlayerManager()
                .removePlayerFromCache(
                        event.getPlayer().getUniqueId()
                );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(
            AsyncPlayerChatEvent event
    ) {

        var player =
                event.getPlayer();

        if (
                !plugin.getAuthManager()
                        .isLoggedIn(
                                player.getUniqueId()
                        )
        ) {
            return;
        }

        String rank =
                plugin.getPlayerManager()
                        .getRank(
                                player.getUniqueId()
                        );

        String rankPrefix =
                switch (rank.toUpperCase()) {

                    case "VIP" ->
                            "§e[VIP] ";

                    case "SVIP" ->
                            "§6[SVIP] ";

                    case "GVIP" ->
                            "§d[GVIP] ";

                    case "CHAMPION" ->
                            "§b[CHAMPION] ";

                    case "CHAMPION_PLUS" ->
                            "§3[CHAMPION+] ";

                    default ->
                            "§7[Gracz] ";
                };

        /*
         * Pobieramy dywizję z cache PvP.
         *
         * Zero SQL podczas pisania wiadomości.
         */
        PvPDivision division =
                plugin.getPvPListener()
                        .getDivision(
                                player.getUniqueId()
                        );

        String format =
                division.getDisplayName()
                        + " "
                        + rankPrefix
                        + "§7"
                        + player.getName()
                        + " §8» §f"
                        + event.getMessage();

        event.setFormat(format);
    }
}