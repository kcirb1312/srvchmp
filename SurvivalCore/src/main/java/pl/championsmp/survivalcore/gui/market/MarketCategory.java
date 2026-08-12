package pl.championsmp.survivalcore.gui.market;

import org.bukkit.ChatColor;

public enum MarketCategory {
    ALL(ChatColor.GRAY + "Wszystko"),
    WEAPONS(ChatColor.RED + "Broń i Pancerz"),
    TOOLS(ChatColor.AQUA + "Narzędzia"),
    SPECIAL(ChatColor.LIGHT_PURPLE + "Przedmioty Specjalne");

    private final String displayName;

    MarketCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
