package pl.championsmp.survivalcore.commands;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import pl.championsmp.survivalcore.SurvivalCore;
import pl.championsmp.survivalcore.gui.market.MarketGUIManager;

public class ShopCommands implements CommandExecutor {

    private final SurvivalCore plugin;

    public ShopCommands(SurvivalCore plugin) {
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
                    "§cTa komenda jest dostepna tylko dla graczy!"
            );

            return true;
        }

        String cmd =
                command.getName().toLowerCase();

        if (cmd.equals("sklep")
                || cmd.equals("rynek")) {

            MarketGUIManager.openMainMenu(player);

            return true;
        }

        if (cmd.equals("wystaw")) {

            if (args.length < 1) {

                player.sendMessage(
                        ChatColor.RED
                                + "Poprawne użycie: /wystaw <cena>"
                );

                return true;
            }

            double price;

            try {

                price =
                        Double.parseDouble(args[0]);

            } catch (NumberFormatException e) {

                player.sendMessage(
                        ChatColor.RED
                                + "Podana cena nie jest prawidłową liczbą!"
                );

                return true;
            }

            if (!Double.isFinite(price)
                    || price <= 0) {

                player.sendMessage(
                        ChatColor.RED
                                + "Cena musi być większa od zera!"
                );

                return true;
            }

            ItemStack itemInHand =
                    player.getInventory()
                            .getItemInMainHand();

            if (itemInHand.getType() == Material.AIR) {

                player.sendMessage(
                        ChatColor.RED
                                + "Musisz trzymać przedmiot "
                                + "w ręce, aby go wystawić!"
                );

                return true;
            }

            /*
             * Robimy pełną kopię przedmiotu przed rozpoczęciem
             * operacji asynchronicznej.
             */
            ItemStack itemToSell =
                    itemInHand.clone();

            String category =
                    detectCategory(itemToSell);

            /*
             * Czas trwania oferty:
             * 48 godzin.
             */
            long expireTime =
                    System.currentTimeMillis()
                            + (48L * 60L * 60L * 1000L);

            /*
             * Rezerwujemy przedmiot natychmiast.
             *
             * Dzięki temu gracz nie może zmienić itemu w ręce
             * podczas oczekiwania na zapis SQLite.
             *
             * Jeżeli zapis się nie powiedzie, item zostanie
             * przywrócony.
             */
            player.getInventory()
                    .setItemInMainHand(
                            new ItemStack(Material.AIR)
                    );

            player.updateInventory();

            player.sendMessage(
                    ChatColor.YELLOW
                            + "Zapisywanie oferty na rynku..."
            );

            plugin.getDatabaseManager()
                    .convertAndSaveMarketItemAsync(
                            player.getName(),
                            itemToSell,
                            price,
                            expireTime,
                            category
                    )
                    .thenAccept(success -> {

                        plugin.getServer()
                                .getScheduler()
                                .runTask(
                                        plugin,
                                        () -> {

                                            if (success) {

                                                player.sendMessage(
                                                        ChatColor.DARK_RED
                                                                + "ChampionSMP.pl "
                                                                + ChatColor.DARK_GRAY
                                                                + "» "
                                                                + ChatColor.GREEN
                                                                + "Pomyślnie wystawiono "
                                                                + "przedmiot na rynek za cenę: "
                                                                + ChatColor.YELLOW
                                                                + String.format(
                                                                "%.2f",
                                                                price
                                                        )
                                                                + "$"
                                                );

                                                return;
                                            }

                                            /*
                                             * SQLite nie zapisał oferty.
                                             *
                                             * Przedmiot musi wrócić do gracza.
                                             */
                                            if (player.isOnline()) {

                                                java.util.HashMap<Integer, ItemStack> leftovers =
                                                        player.getInventory()
                                                                .addItem(
                                                                        itemToSell
                                                                );

                                                /*
                                                 * Jeżeli inventory jest pełne,
                                                 * nie wolno po prostu zgubić
                                                 * reszty przedmiotu.
                                                 */
                                                for (ItemStack leftover :
                                                        leftovers.values()) {

                                                    player.getWorld()
                                                            .dropItemNaturally(
                                                                    player.getLocation(),
                                                                    leftover
                                                            );
                                                }

                                                player.updateInventory();

                                                player.sendMessage(
                                                        ChatColor.RED
                                                                + "Nie udało się "
                                                                + "wystawić przedmiotu. "
                                                                + "Przedmiot został "
                                                                + "zwrócony do ekwipunku."
                                                );

                                            } else {

                                                plugin.getLogger().severe(
                                                        "Nie udało się zapisać "
                                                                + "oferty gracza "
                                                                + player.getName()
                                                                + ", a gracz "
                                                                + "opuścił serwer. "
                                                                + "Przedmiot wymaga "
                                                                + "ręcznego odzyskania."
                                                );
                                            }
                                        }
                                );
                    });

            return true;
        }

        return false;
    }

    /**
     * Automatyczne przypisywanie kategorii rynku.
     */
    private String detectCategory(
            ItemStack item
    ) {

        String typeName =
                item.getType()
                        .name();

        if (typeName.contains("SWORD")
                || typeName.contains("HELMET")
                || typeName.contains("CHESTPLATE")
                || typeName.contains("LEGGINGS")
                || typeName.contains("BOOTS")) {

            return "WEAPONS";
        }

        if (typeName.contains("PICKAXE")
                || typeName.contains("AXE")
                || typeName.contains("SHOVEL")
                || typeName.contains("HOE")) {

            return "TOOLS";
        }

        return "SPECIAL";
    }
}