package pl.championsmp.survivalcore.auth;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import pl.championsmp.survivalcore.SurvivalCore;

public class AuthListener implements Listener {

    private final SurvivalCore plugin;

    public AuthListener(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();

        plugin.getAuthManager()
                .logout(player.getUniqueId());

        event.setJoinMessage(
                ChatColor.GRAY + "normal "
                        + ChatColor.YELLOW
                        + player.getName()
                        + ChatColor.GRAY
                        + " dołączył do gry."
        );

        World lobbyWorld =
                Bukkit.getWorld("lobby");

        if (lobbyWorld != null) {

            Location lobbySpawn =
                    new Location(
                            lobbyWorld,
                            0.5,
                            64.0,
                            0.5
                    );

            player.teleport(lobbySpawn);
        }

        plugin.getDatabaseManager()
                .isRegisteredAsync(
                        player.getName(),
                        exists -> {

                            if (!player.isOnline()) {
                                return;
                            }

                            if (exists) {

                                player.sendMessage(
                                        ChatColor.DARK_RED
                                                + "ChampionSMP.pl "
                                                + ChatColor.DARK_GRAY
                                                + "» "
                                                + ChatColor.GRAY
                                                + "Posiadasz już konto."
                                );

                                player.sendMessage(
                                        ChatColor.DARK_RED
                                                + "ChampionSMP.pl "
                                                + ChatColor.DARK_GRAY
                                                + "» "
                                                + ChatColor.GRAY
                                                + "Aby się zalogować wpisz: "
                                                + ChatColor.YELLOW
                                                + "/login <haslo>"
                                );

                            } else {

                                int captcha =
                                        plugin.getAuthManager()
                                                .generateCaptcha(
                                                        player.getUniqueId()
                                                );

                                player.sendMessage(
                                        ChatColor.DARK_RED
                                                + "ChampionSMP.pl "
                                                + ChatColor.DARK_GRAY
                                                + "» "
                                                + ChatColor.GRAY
                                                + "Nie posiadasz jeszcze konta."
                                );

                                player.sendMessage(
                                        ChatColor.DARK_RED
                                                + "ChampionSMP.pl "
                                                + ChatColor.DARK_GRAY
                                                + "» "
                                                + ChatColor.GRAY
                                                + "Twój kod anty-botowy: "
                                                + ChatColor.YELLOW
                                                + captcha
                                );

                                player.sendMessage(
                                        ChatColor.DARK_RED
                                                + "ChampionSMP.pl "
                                                + ChatColor.DARK_GRAY
                                                + "» "
                                                + ChatColor.GRAY
                                                + "Zarejestruj się: "
                                                + ChatColor.YELLOW
                                                + "/register <haslo> <powtórzHaslo> "
                                                + captcha
                                );
                            }
                        }
                );

        player.updateCommands();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {

        Player player =
                event.getPlayer();

        event.setQuitMessage(
                ChatColor.GRAY
                        + "normal "
                        + ChatColor.YELLOW
                        + player.getName()
                        + ChatColor.GRAY
                        + " opuścił grę."
        );

        plugin.getAuthManager()
                .logout(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(
            PlayerCommandPreprocessEvent event
    ) {

        Player player =
                event.getPlayer();

        String message =
                event.getMessage();

        String[] args =
                message.trim().split("\\s+");

        if (args.length == 0) {
            return;
        }

        String command =
                args[0].toLowerCase();

        boolean loggedIn =
                plugin.getAuthManager()
                        .isLoggedIn(
                                player.getUniqueId()
                        );

        if (!loggedIn) {

            if (!command.equals("/login")
                    && !command.equals("/register")) {

                player.sendMessage(
                        ChatColor.RED
                                + "Musisz się zalogować, aby użyć tej komendy!"
                );

                event.setCancelled(true);
                return;
            }
        }

        if (
                command.contains(":")
                        || command.equals("/pl")
                        || command.equals("/plugins")
                        || command.equals("/help")
                        || command.equals("/?")
        ) {

            if (!player.hasPermission(
                    "championsmp.admin"
            )) {

                player.sendMessage(
                        ChatColor.RED
                                + "Brak uprawnień do wykonania tej komendy."
                );

                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onCommandSend(
            PlayerCommandSendEvent event
    ) {

        Player player =
                event.getPlayer();

        if (!player.hasPermission(
                "championsmp.admin"
        )) {

            event.getCommands()
                    .removeIf(command ->
                            command.contains(":")
                                    || command.equalsIgnoreCase("plugins")
                                    || command.equalsIgnoreCase("pl")
                                    || command.equalsIgnoreCase("help")
                                    || command.equalsIgnoreCase("?")
                    );

            if (!plugin.getAuthManager()
                    .isLoggedIn(
                            player.getUniqueId()
                    )) {

                event.getCommands()
                        .removeIf(command ->
                                !command.equalsIgnoreCase("login")
                                        && !command.equalsIgnoreCase("register")
                        );
            }
        }
    }

    @EventHandler
    public void onPlayerMove(
            PlayerMoveEvent event
    ) {

        Player player =
                event.getPlayer();

        if (plugin.getAuthManager()
                .isLoggedIn(
                        player.getUniqueId()
                )) {
            return;
        }

        Location from =
                event.getFrom();

        Location to =
                event.getTo();

        if (to == null) {
            return;
        }

        if (
                from.getX() != to.getX()
                        || from.getZ() != to.getZ()
        ) {

            event.setTo(from);
        }
    }
}