package pl.championsmp.survivalcore.gui.market;

import org.bukkit.ChatColor;

public enum MenuFilter {
    NONE(ChatColor.YELLOW + "▶ Brak motywu", "Wyświetla przedmioty w kolejności dodania."),
    PRICE_HIGHEST(ChatColor.WHITE + "▶ Najwyższej ceny", "Sortuje od najdroższych przedmiotów."),
    PRICE_LOWEST(ChatColor.WHITE + "▶ Najniższej ceny", "Sortuje od najtańszych przedmiotów."),
    DATE_NEWEST(ChatColor.WHITE + "▶ Najnowsze", "Pokazuje najświeższe oferty na początku."),
    DATE_OLDEST(ChatColor.WHITE + "▶ Najstarsze", "Pokazuje oferty, które zaraz wygasną."),

    // Dodane stałe dla pełnej kompatybilności ze starym kodem rynkowym:
    NEWEST(ChatColor.WHITE + "▶ Najnowsze", "Pokazuje najświeższe oferty na początku."),
    OLDEST(ChatColor.WHITE + "▶ Najstarsze", "Pokazuje oferty, które zaraz wygasną."),
    HIGHEST_PRICE(ChatColor.WHITE + "▶ Najwyższej ceny", "Sortuje od najdroższych przedmiotów."),
    LOWEST_PRICE(ChatColor.WHITE + "▶ Najniższej ceny", "Sortuje od najtańszych przedmiotów.");

    private final String displayName;
    private final String description;

    MenuFilter(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public MenuFilter next() {
        MenuFilter[] values = values();
        int nextOrdinal = (this.ordinal() + 1) % values.length;
        return values[nextOrdinal];
    }
}
