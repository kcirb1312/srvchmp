package pl.championsmp.survivalcore.gui.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class MarketGUIManager {

    private static final int ITEMS_PER_PAGE = 45;

    public static void openMainMenu(Player player) {
        new MarketGUIManager().openMarketMenu(player, 1, MenuFilter.NONE, "");
    }

    public void openMarketMenu(Player player, final int page, MenuFilter filter, String searchName) {
        // Wywołujemy asynchroniczne pobieranie danych z bazy
        pl.championsmp.survivalcore.SurvivalCore.getInstance().getDatabaseManager().getActiveMarketItemsAsync(activeOffers -> {

            // Filtrowanie pobranych przedmiotów po nazwie wyszukiwania
            List<MarketItem> filteredItems = new ArrayList<>();
            for (MarketItem item : activeOffers) {
                if (!searchName.isEmpty() && !item.getItemStack().getType().name().toLowerCase().contains(searchName.toLowerCase())) {
                    continue;
                }
                filteredItems.add(item);
            }

            // Sortowanie na podstawie wybranego filtra ceny lub daty
            if (filter == MenuFilter.PRICE_LOWEST || filter == MenuFilter.LOWEST_PRICE) {
                filteredItems.sort(java.util.Comparator.comparingDouble(MarketItem::getPrice));
            } else if (filter == MenuFilter.PRICE_HIGHEST || filter == MenuFilter.HIGHEST_PRICE) {
                filteredItems.sort((a, b) -> Double.compare(b.getPrice(), a.getPrice()));
            } else if (filter == MenuFilter.DATE_NEWEST || filter == MenuFilter.NEWEST) {
                filteredItems.sort((a, b) -> Integer.compare(b.getId(), a.getId()));
            }

            int totalPages = (int) Math.ceil((double) filteredItems.size() / ITEMS_PER_PAGE);
            if (totalPages == 0) totalPages = 1;

            // Zamiast nadpisywać 'page', tworzymy nową bezpieczną zmienną lokalną
            int targetPage = page;
            if (targetPage > totalPages) targetPage = totalPages;

            String title = ChatColor.DARK_GRAY + "Rynek (Strona " + targetPage + "/" + totalPages + ")";
            Inventory gui = Bukkit.createInventory(null, 54, title);

            // 1. Renderowanie przedmiotów w siatce rynku (sloty 0-44)
            int startIndex = (targetPage - 1) * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredItems.size());

            int slot = 0;
            for (int i = startIndex; i < endIndex; i++) {
                MarketItem marketItem = filteredItems.get(i);
                ItemStack item = marketItem.getItemStack();
                ItemMeta meta = item.getItemMeta();

                if (meta != null) {
                    List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                    lore.add(ChatColor.DARK_GRAY + "----------------------");
                    lore.add(ChatColor.GRAY + " Sprzedawca: " + ChatColor.YELLOW + marketItem.getSellerName());
                    lore.add(ChatColor.GRAY + " Cena: " + ChatColor.GREEN + String.format("%.2f", marketItem.getPrice()) + "$");
                    lore.add(ChatColor.GRAY + " Wygasa za: " + ChatColor.RED + formatTime(marketItem.getExpireTime()));
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "» Kliknij LEWYM, aby zakupić!");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                gui.setItem(slot++, item);
            }

            // 2. Tło paska nawigacji (sloty 45-53)
            ItemStack backgroundPane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
            for (int i = 45; i < 54; i++) {
                if (gui.getItem(i) == null) {
                    gui.setItem(i, backgroundPane);
                }
            }

            // 3. Strzałka wstecz (tylko od strony 2)
            if (targetPage > 1) {
                gui.setItem(45, createGuiItem(Material.ARROW, ChatColor.GREEN + "« Poprzednia strona",
                        ChatColor.GRAY + "Kliknij, aby wrócić do strony " + (targetPage - 1)));
            }

            // 4. Panel dolny z ikonami funkcji
            gui.setItem(47, createGuiItem(Material.COMPASS, ChatColor.AQUA + "🔍 Wyszukaj produkt",
                    ChatColor.GRAY + "Aktualnie: " + ChatColor.YELLOW + (searchName.isEmpty() ? "Wszystko" : searchName), "", ChatColor.YELLOW + "» Kliknij, aby wyszukać"));

            gui.setItem(48, createGuiItem(Material.DIAMOND_SWORD, ChatColor.RED + "⚔ Narzędzia i Uzbrojenie",
                    ChatColor.GRAY + "Pokazuj tylko miecze, pancerze, kilofy itp."));

            gui.setItem(49, createGuiItem(Material.BARRIER, ChatColor.DARK_RED + "" + ChatColor.BOLD + "Zamknij Menu"));

            gui.setItem(50, createGuiItem(Material.CHEST, ChatColor.GOLD + "📦 Twoje aktywne i wygasłe przedmioty",
                    ChatColor.GRAY + "Zarządzaj przedmiotami, które wystawiłeś,", "§7oraz odbierz te, których czas dobiegł końca.", "", ChatColor.YELLOW + "» Kliknij, aby zarządzać"));

            gui.setItem(51, createGuiItem(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "✨ Przedmioty Specjalne",
                    ChatColor.GRAY + "Pokazuj wyłącznie unikalne przedmioty z edycji."));

            // 5. Strzałka w przód (widoczna zawsze)
            gui.setItem(53, createGuiItem(Material.ARROW, ChatColor.GREEN + "Następna strona »",
                    ChatColor.GRAY + "Kliknij, aby przejść do strony " + (targetPage + 1)));

            player.openInventory(gui);
        });
    }

    private ItemStack createGuiItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(line);
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatTime(long expireTime) {
        long diff = expireTime - System.currentTimeMillis();
        if (diff <= 0) return "Wygasło";
        long hours = diff / 3600000;
        long minutes = (diff % 3600000) / 60000;
        return hours + "h " + minutes + "m";
    }
}
