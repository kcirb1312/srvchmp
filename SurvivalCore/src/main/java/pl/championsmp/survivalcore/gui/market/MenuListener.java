package pl.championsmp.survivalcore.gui.market;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import pl.championsmp.survivalcore.SurvivalCore;

public class MenuListener implements Listener {

    private final SurvivalCore plugin;

    public MenuListener(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMarketClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.startsWith(ChatColor.DARK_GRAY + "Rynek (Strona ")) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        Player player = (Player) event.getWhoClicked();

        int currentPage = 1;
        int totalPages = 1;
        try {
            String rawPageData = title.substring(title.indexOf("Strona ") + 7, title.indexOf(")"));
            String[] split = rawPageData.split("/");
            if (split.length == 2) {
                currentPage = Integer.parseInt(split[0]);
                totalPages = Integer.parseInt(split[1]);
            }
        } catch (Exception ignored) {}

        int slot = event.getSlot();

        if (slot < 45) {
            handlePurchase(player, slot);
            return;
        }

        switch (slot) {
            case 45:
                if (currentPage > 1) {
                    refreshMarketView(player, currentPage - 1);
                }
                break;

            case 47:
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Wpisz na czacie nazwę przedmiotu, którego szukasz (lub 'anuluj'):");
                break;

            case 48:
                player.sendMessage(ChatColor.AQUA + "Zmieniono filtr na: Narzędzia i Uzbrojenie");
                break;

            case 49:
                player.closeInventory();
                break;

            case 50:
                player.sendMessage(ChatColor.GOLD + "Otwieranie zarządzania Twoimi ofertami...");
                break;

            case 51:
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Zmieniono filtr na: Przedmioty Specjalne");
                break;

            case 53:
                if (currentPage < totalPages) {
                    refreshMarketView(player, currentPage + 1);
                } else {
                    player.sendMessage(ChatColor.RED + "Brak kolejnych stron z ofertami.");
                }
                break;
        }
    }

    private void refreshMarketView(Player player, int targetPage) {
        // Bezpośrednie wywołanie tworzenia obiektu, aby ominąć błąd braku getMarketGUIManager w SurvivalCore
        new MarketGUIManager().openMarketMenu(player, targetPage, MenuFilter.NONE, "");
    }

    private void handlePurchase(Player player, int slot) {
        player.sendMessage(ChatColor.YELLOW + "Przetwarzanie zakupu przedmiotu...");
    }
}
