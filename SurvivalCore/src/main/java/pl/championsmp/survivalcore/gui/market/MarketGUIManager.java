package pl.championsmp.survivalcore.gui.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.championsmp.survivalcore.SurvivalCore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MarketGUIManager {

    private static final int ITEMS_PER_PAGE = 45;

    public static void openMainMenu(Player player) {
        new MarketGUIManager().openMarketMenu(
                player,
                1,
                MenuFilter.NONE,
                ""
        );
    }

    public void openMarketMenu(
            Player player,
            int page,
            MenuFilter filter,
            String searchName
    ) {
        if (player == null || !player.isOnline()) {
            return;
        }

        if (filter == null) {
            filter = MenuFilter.NONE;
        }

        if (searchName == null) {
            searchName = "";
        }

        final MenuFilter finalFilter = filter;
        final String finalSearchName = searchName;

        SurvivalCore plugin = SurvivalCore.getInstance();

        plugin.getDatabaseManager().getActiveMarketItemsAsync(
                activeOffers -> {

                    if (!player.isOnline()) {
                        return;
                    }

                    List<MarketItem> filteredItems =
                            new ArrayList<>();

                    for (MarketItem item : activeOffers) {

                        if (item == null
                                || item.getItemStack() == null
                                || item.getItemStack().getType().isAir()) {
                            continue;
                        }

                        if (!finalSearchName.isEmpty()) {

                            String itemName =
                                    item.getItemStack()
                                            .getType()
                                            .name()
                                            .toLowerCase();

                            if (!itemName.contains(
                                    finalSearchName.toLowerCase()
                            )) {
                                continue;
                            }
                        }

                        if (!matchesFilter(
                                item,
                                finalFilter
                        )) {
                            continue;
                        }

                        filteredItems.add(item);
                    }

                    sortItems(
                            filteredItems,
                            finalFilter
                    );

                    int totalPages = Math.max(
                            1,
                            (int) Math.ceil(
                                    (double) filteredItems.size()
                                            / ITEMS_PER_PAGE
                            )
                    );

                    int targetPage = Math.max(
                            1,
                            Math.min(page, totalPages)
                    );

                    String title =
                            ChatColor.DARK_GRAY
                                    + "Rynek (Strona "
                                    + targetPage
                                    + "/"
                                    + totalPages
                                    + ")";

                    Inventory gui =
                            Bukkit.createInventory(
                                    null,
                                    54,
                                    title
                            );

                    int startIndex =
                            (targetPage - 1)
                                    * ITEMS_PER_PAGE;

                    int endIndex =
                            Math.min(
                                    startIndex + ITEMS_PER_PAGE,
                                    filteredItems.size()
                            );

                    int slot = 0;

                    for (int i = startIndex;
                         i < endIndex;
                         i++) {

                        MarketItem marketItem =
                                filteredItems.get(i);

                        ItemStack displayItem =
                                marketItem.getItemStack();

                        ItemMeta meta =
                                displayItem.getItemMeta();

                        if (meta != null) {

                            List<String> lore =
                                    meta.hasLore()
                                            && meta.getLore() != null
                                            ? new ArrayList<>(
                                            meta.getLore()
                                    )
                                            : new ArrayList<>();

                            lore.add(
                                    ChatColor.DARK_GRAY
                                            + "----------------------"
                            );

                            lore.add(
                                    ChatColor.GRAY
                                            + " Sprzedawca: "
                                            + ChatColor.YELLOW
                                            + marketItem.getSellerName()
                            );

                            lore.add(
                                    ChatColor.GRAY
                                            + " Cena: "
                                            + ChatColor.GREEN
                                            + String.format(
                                            "%.2f",
                                            marketItem.getPrice()
                                    )
                                            + "$"
                            );

                            lore.add(
                                    ChatColor.GRAY
                                            + " Wygasa za: "
                                            + ChatColor.RED
                                            + formatTime(
                                            marketItem.getExpireTime()
                                    )
                            );

                            lore.add("");

                            lore.add(
                                    ChatColor.YELLOW
                                            + "» Kliknij LEWYM, aby zakupić!"
                            );

                            meta.setLore(lore);

                            displayItem.setItemMeta(meta);
                        }

                        gui.setItem(
                                slot,
                                displayItem
                        );

                        slot++;
                    }

                    ItemStack backgroundPane =
                            createGuiItem(
                                    Material.GRAY_STAINED_GLASS_PANE,
                                    " "
                            );

                    for (int i = 45; i < 54; i++) {

                        if (gui.getItem(i) == null) {
                            gui.setItem(
                                    i,
                                    backgroundPane
                            );
                        }
                    }

                    if (targetPage > 1) {

                        gui.setItem(
                                45,
                                createGuiItem(
                                        Material.ARROW,
                                        ChatColor.GREEN
                                                + "« Poprzednia strona",
                                        ChatColor.GRAY
                                                + "Kliknij, aby wrócić do strony "
                                                + (targetPage - 1)
                                )
                        );
                    }

                    gui.setItem(
                            47,
                            createGuiItem(
                                    Material.COMPASS,
                                    ChatColor.AQUA
                                            + "🔍 Wyszukaj produkt",
                                    ChatColor.GRAY
                                            + "Aktualnie: "
                                            + ChatColor.YELLOW
                                            + (
                                            finalSearchName.isEmpty()
                                                    ? "Wszystko"
                                                    : finalSearchName
                                    ),
                                    "",
                                    ChatColor.YELLOW
                                            + "» Kliknij, aby wyszukać"
                            )
                    );

                    gui.setItem(
                            48,
                            createGuiItem(
                                    Material.DIAMOND_SWORD,
                                    ChatColor.RED
                                            + "⚔ Narzędzia i Uzbrojenie",
                                    ChatColor.GRAY
                                            + "Pokazuj tylko "
                                            + "narzędzia i uzbrojenie."
                            )
                    );

                    gui.setItem(
                            49,
                            createGuiItem(
                                    Material.BARRIER,
                                    ChatColor.DARK_RED
                                            + ChatColor.BOLD
                                            + "Zamknij Menu"
                            )
                    );

                    gui.setItem(
                            50,
                            createGuiItem(
                                    Material.CHEST,
                                    ChatColor.GOLD
                                            + "📦 Twoje aktywne i wygasłe przedmioty",
                                    ChatColor.GRAY
                                            + "Zarządzaj swoimi ofertami.",
                                    "",
                                    ChatColor.YELLOW
                                            + "» Kliknij, aby zarządzać"
                            )
                    );

                    gui.setItem(
                            51,
                            createGuiItem(
                                    Material.NETHER_STAR,
                                    ChatColor.LIGHT_PURPLE
                                            + "✨ Przedmioty Specjalne",
                                    ChatColor.GRAY
                                            + "Pokazuj wyłącznie "
                                            + "przedmioty specjalne."
                            )
                    );

                    if (targetPage < totalPages) {

                        gui.setItem(
                                53,
                                createGuiItem(
                                        Material.ARROW,
                                        ChatColor.GREEN
                                                + "Następna strona »",
                                        ChatColor.GRAY
                                                + "Kliknij, aby przejść "
                                                + "do strony "
                                                + (targetPage + 1)
                                )
                        );

                    } else {

                        gui.setItem(
                                53,
                                createGuiItem(
                                        Material.ARROW,
                                        ChatColor.DARK_GRAY
                                                + "Następna strona »",
                                        ChatColor.GRAY
                                                + "Brak kolejnych stron."
                                )
                        );
                    }

                    player.openInventory(gui);
                }
        );
    }

    private boolean matchesFilter(
            MarketItem item,
            MenuFilter filter
    ) {
        if (filter == null
                || filter == MenuFilter.NONE) {
            return true;
        }

        Material material =
                item.getItemStack().getType();

        String name =
                material.name().toLowerCase();

        if (filter == MenuFilter.PRICE_LOWEST
                || filter == MenuFilter.LOWEST_PRICE
                || filter == MenuFilter.PRICE_HIGHEST
                || filter == MenuFilter.HIGHEST_PRICE
                || filter == MenuFilter.DATE_NEWEST
                || filter == MenuFilter.NEWEST) {
            return true;
        }

        if (filter == MenuFilter.TOOLS) {
            return isToolOrWeapon(material, name);
        }

        if (filter == MenuFilter.SPECIAL) {
            return item.getCategory() == MarketCategory.SPECIAL;
        }

        return true;
    }

    private boolean isToolOrWeapon(
            Material material,
            String name
    ) {
        if (name.contains("SWORD")
                || name.contains("AXE")
                || name.contains("PICKAXE")
                || name.contains("SHOVEL")
                || name.contains("HOE")
                || name.contains("BOW")
                || name.contains("CROSSBOW")
                || name.contains("TRIDENT")
                || name.contains("HELMET")
                || name.contains("CHESTPLATE")
                || name.contains("LEGGINGS")
                || name.contains("BOOTS")
                || name.contains("SHIELD")) {
            return true;
        }

        return material.name().contains("ARMOR");
    }

    private void sortItems(
            List<MarketItem> items,
            MenuFilter filter
    ) {
        if (filter == MenuFilter.PRICE_LOWEST
                || filter == MenuFilter.LOWEST_PRICE) {

            items.sort(
                    Comparator.comparingDouble(
                            MarketItem::getPrice
                    )
            );

        } else if (filter == MenuFilter.PRICE_HIGHEST
                || filter == MenuFilter.HIGHEST_PRICE) {

            items.sort(
                    Comparator.comparingDouble(
                            MarketItem::getPrice
                    ).reversed()
            );

        } else if (filter == MenuFilter.DATE_NEWEST
                || filter == MenuFilter.NEWEST) {

            items.sort(
                    Comparator.comparingInt(
                            MarketItem::getId
                    ).reversed()
            );
        }
    }

    private ItemStack createGuiItem(
            Material material,
            String name,
            String... loreLines
    ) {
        ItemStack item =
                new ItemStack(material);

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            meta.setDisplayName(name);

            List<String> lore =
                    new ArrayList<>();

            for (String line : loreLines) {
                lore.add(line);
            }

            meta.setLore(lore);

            item.setItemMeta(meta);
        }

        return item;
    }

    private String formatTime(long expireTime) {

        long diff =
                expireTime
                        - System.currentTimeMillis();

        if (diff <= 0) {
            return "Wygasło";
        }

        long hours =
                diff / 3_600_000L;

        long minutes =
                (diff % 3_600_000L)
                        / 60_000L;

        return hours + "h " + minutes + "m";
    }
}