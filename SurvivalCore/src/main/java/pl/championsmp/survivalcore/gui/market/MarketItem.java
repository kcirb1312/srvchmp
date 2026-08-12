package pl.championsmp.survivalcore.gui.market;

import org.bukkit.inventory.ItemStack;

public class MarketItem {
    private final int id;
    private final int sellerId;
    private final String sellerName;
    private final ItemStack itemStack;
    private final double price;
    private final long expireTime;
    private final MarketCategory category;

    // Konstruktor przyjmujący argumenty ładowane z bazy danych
    public MarketItem(int id, int sellerId, String sellerName, ItemStack itemStack, double price, long expireTime, MarketCategory category) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.itemStack = itemStack;
        this.price = price;
        this.expireTime = expireTime;
        this.category = category;
    }

    public int getId() {
        return id;
    }

    public int getSellerId() {
        return sellerId;
    }

    public String getSellerName() {
        return sellerName;
    }

    public ItemStack getItemStack() {
        return itemStack.clone();
    }

    public double getPrice() {
        return price;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public MarketCategory getCategory() {
        return category;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expireTime;
    }
}
