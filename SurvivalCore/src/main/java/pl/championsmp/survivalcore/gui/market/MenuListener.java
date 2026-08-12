package pl.championsmp.survivalcore.gui.market;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import pl.championsmp.survivalcore.SurvivalCore;
import pl.championsmp.survivalcore.database.SQLiteManager;

import java.util.Comparator;

public class MenuListener implements Listener {

    private static final int ITEMS_PER_PAGE = 45;

    private final SurvivalCore plugin;

    public MenuListener(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMarketClick(InventoryClickEvent event) {

        String title = event.getView().getTitle();

        if (!title.startsWith(
                ChatColor.DARK_GRAY + "Rynek (Strona "
        )) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getCurrentItem() == null
                || event.getCurrentItem().getType().isAir()) {
            return;
        }

        int currentPage = getCurrentPage(title);
        int totalPages = getTotalPages(title);

        int slot = event.getRawSlot();

        /*
         * Kliknięcie w przedmiot z rynku.
         */
        if (slot >= 0 && slot < ITEMS_PER_PAGE) {

            handlePurchase(
                    player,
                    currentPage,
                    slot
            );

            return;
        }

        /*
         * Dolny pasek.
         */
        switch (slot) {

            /*
             * Poprzednia strona.
             */
            case 45 -> {

                if (currentPage > 1) {

                    refreshMarketView(
                            player,
                            currentPage - 1
                    );
                }
            }

            /*
             * Wyszukiwanie.
             *
             * Na razie nie uruchamiamy tutaj czatu,
             * ponieważ obecny projekt nie ma jeszcze
             * systemu ChatInput.
             */
            case 47 -> {

                player.closeInventory();

                player.sendMessage(
                        ChatColor.AQUA
                                + "════════════════════════════"
                );

                player.sendMessage(
                        ChatColor.YELLOW
                                + "WYSZUKIWANIE RYNKU"
                );

                player.sendMessage(
                        ChatColor.GRAY
                                + "Wyszukiwanie z czatu zostanie "
                                + "dodane w kolejnym kroku."
                );

                player.sendMessage(
                        ChatColor.GRAY
                                + "Wpisz "
                                + ChatColor.YELLOW
                                + "/market search <nazwa>"
                );

                player.sendMessage(
                        ChatColor.AQUA
                                + "════════════════════════════"
                );
            }

            /*
             * Kategorie.
             */
            case 48 -> {

                player.sendMessage(
                        ChatColor.RED
                                + "Filtr: Broń i Pancerz."
                );

                refreshMarketView(
                        player,
                        1
                );
            }

            /*
             * Zamknięcie.
             */
            case 49 -> player.closeInventory();

            /*
             * Moje oferty.
             *
             * Nie otwieramy nieistniejącego GUI.
             */
            case 50 -> {

                player.closeInventory();

                player.sendMessage(
                        ChatColor.GOLD
                                + "Twoje oferty:"
                );

                player.sendMessage(
                        ChatColor.GRAY
                                + "System zarządzania ofertami "
                                + "nie jest jeszcze podpięty."
                );
            }

            /*
             * Przedmioty specjalne.
             */
            case 51 -> {

                player.sendMessage(
                        ChatColor.LIGHT_PURPLE
                                + "Filtr: Przedmioty Specjalne."
                );

                refreshMarketView(
                        player,
                        1
                );
            }

            /*
             * Następna strona.
             */
            case 53 -> {

                if (currentPage < totalPages) {

                    refreshMarketView(
                            player,
                            currentPage + 1
                    );

                } else {

                    player.sendMessage(
                            ChatColor.RED
                                    + "Brak kolejnych stron."
                    );
                }
            }

            default -> {
            }
        }
    }

    /**
     * Obsługa zakupu konkretnego przedmiotu.
     *
     * Najpierw pobieramy aktualną listę ofert z bazy,
     * a następnie ustalamy ID oferty na podstawie strony
     * i slotu.
     */
    private void handlePurchase(
            Player player,
            int page,
            int slot
    ) {

        player.sendMessage(
                ChatColor.YELLOW
                        + "Sprawdzam ofertę..."
        );

        plugin.getDatabaseManager()
                .getActiveMarketItemsAsync(
                        activeOffers -> {

                            /*
                             * Sortowanie musi być takie samo,
                             * jak w MarketGUIManager.
                             *
                             * Obecnie GUI wyświetla oferty
                             * w kolejności ID DESC.
                             */
                            activeOffers.sort(
                                    Comparator.comparingInt(
                                            MarketItem::getId
                                    ).reversed()
                            );

                            int index =
                                    (page - 1)
                                            * ITEMS_PER_PAGE
                                            + slot;

                            /*
                             * Oferta mogła zniknąć między
                             * otwarciem GUI a kliknięciem.
                             */
                            if (index < 0
                                    || index >= activeOffers.size()) {

                                player.sendMessage(
                                        ChatColor.RED
                                                + "Ta oferta nie jest już dostępna."
                                );

                                refreshMarketView(
                                        player,
                                        page
                                );

                                return;
                            }

                            MarketItem marketItem =
                                    activeOffers.get(index);

                            int marketItemId =
                                    marketItem.getId();

                            /*
                             * Zakup wykonujemy na osobnym
                             * wątku przez SQLiteManager.
                             */
                            plugin.getDatabaseManager()
                                    .purchaseMarketItemAsync(
                                            player.getName(),
                                            marketItemId
                                    )
                                    .thenAccept(
                                            result -> {

                                                /*
                                                 * Bukkit API musi być
                                                 * wykonywane na głównym
                                                 * wątku.
                                                 */
                                                plugin.getServer()
                                                        .getScheduler()
                                                        .runTask(
                                                                plugin,
                                                                () -> handlePurchaseResult(
                                                                        player,
                                                                        result,
                                                                        page
                                                                )
                                                        );
                                            }
                                    );
                        }
                );
    }

    /**
     * Obsługa wyniku transakcji.
     */
    private void handlePurchaseResult(
            Player player,
            SQLiteManager.PurchaseResult result,
            int page
    ) {

        if (!player.isOnline()) {
            return;
        }

        /*
         * Zakup się nie udał.
         */
        if (!result.success()) {

            player.sendMessage(
                    ChatColor.RED
                            + "✖ "
                            + result.message()
            );

            /*
             * Odświeżamy GUI, bo oferta mogła
             * zostać kupiona przez kogoś innego.
             */
            refreshMarketView(
                    player,
                    page
            );

            return;
        }

        MarketItem purchasedItem =
                result.item();

        if (purchasedItem == null
                || purchasedItem.getItemStack() == null) {

            player.sendMessage(
                    ChatColor.RED
                            + "Zakup wykonano, ale "
                            + "przedmiot jest nieprawidłowy."
            );

            refreshMarketView(
                    player,
                    page
            );

            return;
        }

        ItemStack item =
                purchasedItem
                        .getItemStack()
                        .clone();

        /*
         * Dodajemy przedmiot do ekwipunku.
         */
        java.util.HashMap<Integer, ItemStack> leftovers =
                player.getInventory()
                        .addItem(item);

        /*
         * Jeżeli ekwipunek jest pełny,
         * wyrzucamy resztę na ziemię.
         *
         * Dzięki temu przedmiot nie znika.
         */
        if (!leftovers.isEmpty()) {

            for (ItemStack leftover :
                    leftovers.values()) {

                if (leftover == null
                        || leftover.getType().isAir()) {
                    continue;
                }

                player.getWorld()
                        .dropItemNaturally(
                                player.getLocation(),
                                leftover
                        );
            }

            player.sendMessage(
                    ChatColor.YELLOW
                            + "⚠ Ekwipunek był pełny."
            );

            player.sendMessage(
                    ChatColor.GRAY
                            + "Przedmiot został wyrzucony "
                            + "obok Ciebie."
            );
        }

        /*
         * Wiadomość o sukcesie.
         */
        player.sendMessage(
                ChatColor.GREEN
                        + "✔ Zakupiono "
                        + ChatColor.YELLOW
                        + getItemName(item)
                        + ChatColor.GREEN
                        + " za "
                        + ChatColor.GOLD
                        + String.format(
                        "%.2f",
                        result.price()
                )
                        + "$."
        );

        /*
         * Odświeżamy rynek.
         */
        refreshMarketView(
                player,
                page
        );
    }

    /**
     * Odświeżenie GUI rynku.
     */
    private void refreshMarketView(
            Player player,
            int targetPage
    ) {

        new MarketGUIManager()
                .openMarketMenu(
                        player,
                        Math.max(1, targetPage),
                        MenuFilter.NONE,
                        ""
                );
    }

    /**
     * Pobiera numer strony z tytułu GUI.
     */
    private int getCurrentPage(String title) {

        try {

            int start =
                    title.indexOf("Strona ") + 7;

            int end =
                    title.indexOf("/");

            if (start < 7 || end <= start) {
                return 1;
            }

            return Math.max(
                    1,
                    Integer.parseInt(
                            title.substring(
                                    start,
                                    end
                            )
                    )
            );

        } catch (Exception ignored) {

            return 1;
        }
    }

    /**
     * Pobiera całkowitą liczbę stron z tytułu GUI.
     */
    private int getTotalPages(String title) {

        try {

            int start =
                    title.indexOf("/") + 1;

            int end =
                    title.indexOf(")");

            if (start <= 0 || end <= start) {
                return 1;
            }

            return Math.max(
                    1,
                    Integer.parseInt(
                            title.substring(
                                    start,
                                    end
                            )
                    )
            );

        } catch (Exception ignored) {

            return 1;
        }
    }

    /**
     * Nazwa przedmiotu do wiadomości.
     */
    private String getItemName(ItemStack item) {

        if (item.hasItemMeta()
                && item.getItemMeta() != null
                && item.getItemMeta().hasDisplayName()) {

            return item.getItemMeta()
                    .getDisplayName();
        }

        String name =
                item.getType()
                        .name()
                        .toLowerCase()
                        .replace('_', ' ');

        return name.substring(0, 1).toUpperCase()
                + name.substring(1);
    }
}