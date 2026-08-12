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
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cTa komenda jest dostepna tylko dla graczy!");
            return true;
        }

        Player player = (Player) sender;
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("sklep") || cmd.equals("rynek")) {
            MarketGUIManager.openMainMenu(player);
            return true;
        }

        // OBSŁUGA KOMENDY /WYSTAW
        if (cmd.equals("wystaw")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Poprawne użycie: /wystaw <cena>");
                return true;
            }

            double price;
            try {
                price = Double.parseDouble(args[0]);
                if (price <= 0) {
                    player.sendMessage(ChatColor.RED + "Cena musi być większa od zera!");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Podana cena nie jest prawidłową liczbą!");
                return true;
            }

            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (itemInHand.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "Musisz trzymać przedmiot w ręce, aby go wystawić!");
                return true;
            }

            // Automatyczne przypisywanie kategorii dla filtrów rynku
            String category = "SPECIAL";
            String typeName = itemInHand.getType().name();
            if (typeName.contains("SWORD") || typeName.contains("HELMET") || typeName.contains("CHESTPLATE") || typeName.contains("LEGGINGS") || typeName.contains("BOOTS")) {
                category = "WEAPONS";
            } else if (typeName.contains("PICKAXE") || typeName.contains("AXE") || typeName.contains("SHOVEL") || typeName.contains("HOE")) {
                category = "TOOLS";
            }

            // Czas trwania oferty: 48 godzin od teraz
            long expireTime = System.currentTimeMillis() + (48L * 60L * 60L * 1000L);

            // Klonujemy przedmiot przed usunięciem go z ekwipunku
            ItemStack itemToSell = itemInHand.clone();

            // Usunięcie przedmiotu z ręki gracza
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

            // Asynchroniczny zapis do SQLite
            plugin.getDatabaseManager().convertAndSaveMarketItemAsync(player.getName(), itemToSell, price, expireTime, category);

            player.sendMessage(ChatColor.DARK_RED + "ChampionSMP.pl " + ChatColor.DARK_GRAY + "» " +
                    ChatColor.GREEN + "Pomyślnie wystawiono przedmiot na rynek za cenę: " + ChatColor.YELLOW + String.format("%.2f", price) + "$");
            return true;
        }

        return false;
    }
}
