package pl.championsmp.survivalcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import pl.championsmp.survivalcore.SurvivalCore;
import pl.championsmp.survivalcore.auth.PasswordUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthCommands implements CommandExecutor {

    private final SurvivalCore plugin;

    public AuthCommands(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {

        if (!(sender instanceof Player player)) {

            sender.sendMessage(
                    "§cTa komenda jest dostępna tylko dla graczy!"
            );

            return true;
        }

        String commandName =
                command.getName().toLowerCase();

        if (commandName.equals("register")) {

            if (plugin.getAuthManager()
                    .isLoggedIn(
                            player.getUniqueId()
                    )) {

                player.sendMessage(
                        "§cJesteś już zalogowany!"
                );

                return true;
            }

            if (args.length < 3) {

                player.sendMessage(
                        "§c§lBłąd! §7Poprawne użycie: "
                                + "§e/register <hasło> <powtórzHasło> <kod>"
                );

                return true;
            }

            String password =
                    args[0];

            String confirmPassword =
                    args[1];

            String captchaInput =
                    args[2];

            Integer correctCaptcha =
                    plugin.getAuthManager()
                            .getCaptcha(
                                    player.getUniqueId()
                            );

            if (correctCaptcha == null) {

                player.sendMessage(
                        "§cBrak aktywnego kodu anty-botowego. "
                                + "Wejdź ponownie na serwer."
                );

                return true;
            }

            if (!captchaInput.equals(
                    String.valueOf(correctCaptcha)
            )) {

                player.sendMessage(
                        "§cPodany kod anty-botowy jest niepoprawny!"
                );

                return true;
            }

            if (password.length() < 6) {

                player.sendMessage(
                        "§cHasło musi mieć minimum 6 znaków."
                );

                return true;
            }

            if (password.length() > 72) {

                player.sendMessage(
                        "§cHasło jest zbyt długie."
                );

                return true;
            }

            if (!password.equals(
                    confirmPassword
            )) {

                player.sendMessage(
                        "§cPodane hasła nie są takie same!"
                );

                return true;
            }

            String username =
                    player.getName().toLowerCase();

            String passwordHash =
                    PasswordUtils.hashPassword(
                            password
                    );

            Bukkit.getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {

                                try (
                                        Connection connection =
                                                plugin.getDatabaseManager()
                                                        .getConnection()
                                ) {

                                    String checkQuery =
                                            "SELECT id FROM users "
                                                    + "WHERE username = ? LIMIT 1;";

                                    try (
                                            PreparedStatement statement =
                                                    connection.prepareStatement(
                                                            checkQuery
                                                    )
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

                                                sendSync(
                                                        player,
                                                        "§cTo konto już istnieje! "
                                                                + "Użyj /login <hasło>"
                                                );

                                                return;
                                            }
                                        }
                                    }

                                    String insertQuery = """
                                        INSERT INTO users
                                        (username, password, rank, money,
                                         playtime, kills, deaths,
                                         pvp_points, pvp_division)
                                        VALUES (?, ?, 'PLAYER', 0.0,
                                                0, 0, 0, 0, 'NORMAL');
                                        """;

                                    try (
                                            PreparedStatement statement =
                                                    connection.prepareStatement(
                                                            insertQuery
                                                    )
                                    ) {

                                        statement.setString(
                                                1,
                                                username
                                        );

                                        statement.setString(
                                                2,
                                                passwordHash
                                        );

                                        statement.executeUpdate();
                                    }

                                } catch (SQLException e) {

                                    plugin.getLogger().severe(
                                            "Błąd podczas rejestracji "
                                                    + username
                                    );

                                    e.printStackTrace();

                                    sendSync(
                                            player,
                                            "§cWystąpił błąd bazy danych."
                                    );

                                    return;
                                }

                                Bukkit.getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {

                                                    if (!player.isOnline()) {
                                                        return;
                                                    }

                                                    plugin.getAuthManager()
                                                            .login(
                                                                    player.getUniqueId()
                                                            );

                                                    player.removePotionEffect(
                                                            PotionEffectType.BLINDNESS
                                                    );

                                                    player.sendMessage(
                                                            "§a§lSukces! "
                                                                    + "§7Konto zostało utworzone. "
                                                                    + "Zostałeś zalogowany."
                                                    );
                                                }
                                        );
                            }
                    );

            return true;
        }

        if (commandName.equals("login")) {

            if (plugin.getAuthManager()
                    .isLoggedIn(
                            player.getUniqueId()
                    )) {

                player.sendMessage(
                        "§cJesteś już zalogowany!"
                );

                return true;
            }

            if (args.length < 1) {

                player.sendMessage(
                        "§c§lUżycie: §7/login <hasło>"
                );

                return true;
            }

            String password =
                    args[0];

            String username =
                    player.getName().toLowerCase();

            String passwordHash =
                    PasswordUtils.hashPassword(
                            password
                    );

            Bukkit.getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {

                                String databaseHash;

                                try (
                                        Connection connection =
                                                plugin.getDatabaseManager()
                                                        .getConnection()
                                ) {

                                    String query =
                                            "SELECT password FROM users "
                                                    + "WHERE username = ? LIMIT 1;";

                                    try (
                                            PreparedStatement statement =
                                                    connection.prepareStatement(
                                                            query
                                                    )
                                    ) {

                                        statement.setString(
                                                1,
                                                username
                                        );

                                        try (
                                                ResultSet resultSet =
                                                        statement.executeQuery()
                                        ) {

                                            if (!resultSet.next()) {

                                                sendSync(
                                                        player,
                                                        "§cTwoje konto nie istnieje! "
                                                                + "Zarejestruj się: /register"
                                                );

                                                return;
                                            }

                                            databaseHash =
                                                    resultSet.getString(
                                                            "password"
                                                    );
                                        }
                                    }

                                } catch (SQLException e) {

                                    plugin.getLogger().severe(
                                            "Błąd podczas logowania "
                                                    + username
                                    );

                                    e.printStackTrace();

                                    sendSync(
                                            player,
                                            "§cWystąpił błąd bazy danych."
                                    );

                                    return;
                                }

                                boolean correct =
                                        databaseHash != null
                                                && databaseHash.equals(
                                                passwordHash
                                        );

                                Bukkit.getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {

                                                    if (!player.isOnline()) {
                                                        return;
                                                    }

                                                    if (!correct) {

                                                        player.sendMessage(
                                                                "§cBłędne hasło! "
                                                                        + "Spróbuj ponownie."
                                                        );

                                                        return;
                                                    }

                                                    plugin.getAuthManager()
                                                            .login(
                                                                    player.getUniqueId()
                                                            );

                                                    plugin.getPlayerManager()
                                                            .loadPlayerData(
                                                                    player
                                                            );

                                                    player.removePotionEffect(
                                                            PotionEffectType.BLINDNESS
                                                    );

                                                    player.sendMessage(
                                                            "§a§lSukces! "
                                                                    + "§7Zostałeś pomyślnie zalogowany."
                                                    );
                                                }
                                        );
                            }
                    );

            return true;
        }

        return false;
    }

    private void sendSync(
            Player player,
            String message
    ) {

        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {

                            if (player.isOnline()) {
                                player.sendMessage(message);
                            }
                        }
                );
    }
}