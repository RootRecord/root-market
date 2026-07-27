package com.rootrecord.minecraft.rootmarket;

import com.rootrecord.minecraft.rootmcshops.RootMcShopsPlugin;
import com.rootrecord.minecraft.rootmcshops.ShopContainers;
import com.rootrecord.minecraft.rootmcshops.ShopItemKeys;
import com.rootrecord.minecraft.rootmcshops.ShopListing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MarketMenus {

    static final int PAGE_SIZE = 45;
    static final int SLOT_PREV = 45;
    static final int SLOT_INFO = 49;
    static final int SLOT_NEXT = 53;
    static final int SLOT_BACK = 48;

    private MarketMenus() {}

    public static void openHub(RootMarketPlugin plugin, RootMcShopsPlugin shops, Player viewer) {
        List<HubRow> rows = buildHubRows(shops);
        if (rows.isEmpty()) {
            viewer.sendMessage(plugin.msg("hub-empty"));
            return;
        }
        openHubPage(plugin, shops, viewer, rows, 0);
    }

    public static void openHubPage(
            RootMarketPlugin plugin,
            RootMcShopsPlugin shops,
            Player viewer,
            List<HubRow> rows,
            int page) {
        int totalPages = Math.max(1, (int) Math.ceil(rows.size() / (double) PAGE_SIZE));
        int safePage = Math.min(Math.max(0, page), totalPages - 1);
        int from = safePage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, rows.size());
        List<HubRow> pageRows = rows.subList(from, to);
        List<String> keys = pageRows.stream().map(HubRow::itemKey).toList();

        String titleRaw = plugin.rawMsg("hub-title")
                .replace("{page}", String.valueOf(safePage + 1))
                .replace("{pages}", String.valueOf(totalPages));
        MarketMenuHolder holder = MarketMenuHolder.hub(viewer.getUniqueId(), safePage, keys);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(strip(titleRaw), NamedTextColor.DARK_GREEN));
        holder.bind(inv);

        for (int i = 0; i < pageRows.size(); i++) {
            inv.setItem(i, hubItem(pageRows.get(i)));
        }
        if (safePage > 0) {
            inv.setItem(SLOT_PREV, nav(Material.ARROW, plugin.rawMsg("hub-prev")));
        }
        inv.setItem(SLOT_INFO, infoBook(
                strip(plugin.rawMsg("hub-info")),
                List.of(
                        rows.size() + " item type(s) listed",
                        "Page " + (safePage + 1) + " / " + totalPages,
                        strip(plugin.rawMsg("hub-hint")))));
        if (safePage < totalPages - 1) {
            inv.setItem(SLOT_NEXT, nav(Material.ARROW, plugin.rawMsg("hub-next")));
        }
        viewer.openInventory(inv);
    }

    public static void openItem(RootMarketPlugin plugin, RootMcShopsPlugin shops, Player viewer, String itemKey) {
        List<ShopListing> listings = listingsForItem(shops, itemKey);
        if (listings.isEmpty()) {
            viewer.sendMessage(plugin.msg("item-empty").replace("{item}", itemKey.toLowerCase(Locale.ROOT)));
            return;
        }
        openItemPage(plugin, shops, viewer, itemKey, listings, 0);
    }

    public static void openItemPage(
            RootMarketPlugin plugin,
            RootMcShopsPlugin shops,
            Player viewer,
            String itemKey,
            List<ShopListing> listings,
            int page) {
        int totalPages = Math.max(1, (int) Math.ceil(listings.size() / (double) PAGE_SIZE));
        int safePage = Math.min(Math.max(0, page), totalPages - 1);
        int from = safePage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, listings.size());
        List<ShopListing> pageList = listings.subList(from, to);
        List<String> ids = pageList.stream().map(ShopListing::id).toList();

        double cheapest = listings.stream().mapToDouble(ShopListing::price).min().orElse(0);
        String titleRaw = plugin.rawMsg("item-title")
                .replace("{item}", pretty(itemKey))
                .replace("{page}", String.valueOf(safePage + 1))
                .replace("{pages}", String.valueOf(totalPages));
        MarketMenuHolder holder = MarketMenuHolder.item(viewer.getUniqueId(), itemKey, safePage, ids);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(strip(titleRaw), NamedTextColor.DARK_GREEN));
        holder.bind(inv);

        for (int i = 0; i < pageList.size(); i++) {
            inv.setItem(i, listingItem(shops, pageList.get(i), true));
        }
        if (safePage > 0) {
            inv.setItem(SLOT_PREV, nav(Material.ARROW, plugin.rawMsg("item-prev")));
        }
        inv.setItem(SLOT_BACK, nav(Material.BARRIER, plugin.rawMsg("item-back")));
        inv.setItem(SLOT_INFO, infoBook(
                pretty(itemKey),
                List.of(
                        strip(plugin.rawMsg("item-info")
                                .replace("{count}", String.valueOf(listings.size()))
                                .replace("{price}", String.format(Locale.US, "%.3f", cheapest))),
                        "Page " + (safePage + 1) + " / " + totalPages,
                        strip(plugin.rawMsg("item-hint").replace("{item}", itemKey.toLowerCase(Locale.ROOT))))));
        if (safePage < totalPages - 1) {
            inv.setItem(SLOT_NEXT, nav(Material.ARROW, plugin.rawMsg("item-next")));
        }
        viewer.openInventory(inv);
    }

    public static void openPlayer(RootMarketPlugin plugin, RootMcShopsPlugin shops, Player viewer, String ownerQuery) {
        OwnerMatch match = resolveOwner(shops, ownerQuery);
        if (match == null) {
            viewer.sendMessage(plugin.msg("player-not-found").replace("{player}", ownerQuery));
            return;
        }
        List<ShopListing> listings = listingsForOwner(shops, match.ownerUuid(), match.ownerName());
        if (listings.isEmpty()) {
            viewer.sendMessage(plugin.msg("player-empty").replace("{player}", match.ownerName()));
            return;
        }
        openPlayerPage(plugin, shops, viewer, match, listings, 0);
    }

    public static void openPlayerPage(
            RootMarketPlugin plugin,
            RootMcShopsPlugin shops,
            Player viewer,
            OwnerMatch match,
            List<ShopListing> listings,
            int page) {
        int totalPages = Math.max(1, (int) Math.ceil(listings.size() / (double) PAGE_SIZE));
        int safePage = Math.min(Math.max(0, page), totalPages - 1);
        int from = safePage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, listings.size());
        List<ShopListing> pageList = listings.subList(from, to);
        List<String> ids = pageList.stream().map(ShopListing::id).toList();

        String titleRaw = plugin.rawMsg("player-title")
                .replace("{player}", match.ownerName())
                .replace("{page}", String.valueOf(safePage + 1))
                .replace("{pages}", String.valueOf(totalPages));
        MarketMenuHolder holder = MarketMenuHolder.player(
                viewer.getUniqueId(), match.ownerUuid(), match.ownerName(), safePage, ids);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(strip(titleRaw), NamedTextColor.DARK_GREEN));
        holder.bind(inv);

        for (int i = 0; i < pageList.size(); i++) {
            inv.setItem(i, listingItem(shops, pageList.get(i), false));
        }
        if (safePage > 0) {
            inv.setItem(SLOT_PREV, nav(Material.ARROW, plugin.rawMsg("player-prev")));
        }
        inv.setItem(SLOT_INFO, infoBook(
                match.ownerName() + "'s shops",
                List.of(
                        strip(plugin.rawMsg("player-info").replace("{count}", String.valueOf(listings.size()))),
                        "Page " + (safePage + 1) + " / " + totalPages,
                        strip(plugin.rawMsg("player-hint")))));
        if (safePage < totalPages - 1) {
            inv.setItem(SLOT_NEXT, nav(Material.ARROW, plugin.rawMsg("player-next")));
        }
        viewer.openInventory(inv);
    }

    public static List<HubRow> buildHubRows(RootMcShopsPlugin shops) {
        Map<String, HubRow> map = new HashMap<>();
        for (ShopListing shop : shops.store().all()) {
            if (!shop.isSellShop() || shop.itemKey() == null || shop.itemKey().isBlank()) {
                continue;
            }
            String key = shop.itemKey().toUpperCase(Locale.ROOT);
            int stock = shops.countStock(shop);
            HubRow row = map.get(key);
            if (row == null) {
                map.put(key, new HubRow(key, shop.price(), stock, 1, stock > 0 ? 1 : 0));
            } else {
                map.put(key, new HubRow(
                        key,
                        Math.min(row.cheapestPrice(), shop.price()),
                        row.totalStock() + stock,
                        row.sellerCount() + 1,
                        row.inStockCount() + (stock > 0 ? 1 : 0)));
            }
        }
        List<HubRow> rows = new ArrayList<>(map.values());
        rows.sort(Comparator
                .comparingInt(HubRow::inStockCount).reversed()
                .thenComparing(HubRow::itemKey, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    public static List<ShopListing> listingsForItem(RootMcShopsPlugin shops, String itemKey) {
        List<ShopListing> out = new ArrayList<>();
        for (ShopListing shop : shops.store().all()) {
            if (shop.isSellShop() && shop.itemKey() != null && shop.itemKey().equalsIgnoreCase(itemKey)) {
                out.add(shop);
            }
        }
        out.sort(Comparator
                .comparingInt((ShopListing s) -> shops.countStock(s) > 0 ? 0 : 1)
                .thenComparingDouble(ShopListing::price)
                .thenComparing(s -> s.ownerName() == null ? "" : s.ownerName(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public static List<ShopListing> listingsForOwner(RootMcShopsPlugin shops, String ownerUuid, String ownerName) {
        List<ShopListing> out = new ArrayList<>();
        for (ShopListing shop : shops.store().all()) {
            if (!shop.isSellShop()) {
                continue;
            }
            if (ownerUuid != null && !ownerUuid.isBlank()
                    && shop.ownerUuid() != null
                    && shop.ownerUuid().equalsIgnoreCase(ownerUuid)) {
                out.add(shop);
                continue;
            }
            if (ownerName != null && shop.ownerName() != null && shop.ownerName().equalsIgnoreCase(ownerName)) {
                out.add(shop);
            }
        }
        out.sort(Comparator
                .comparing((ShopListing s) -> s.itemKey(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingDouble(ShopListing::price));
        return out;
    }

    public static OwnerMatch resolveOwner(RootMcShopsPlugin shops, String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String q = query.trim();
        Player online = Bukkit.getPlayerExact(q);
        if (online == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(q)) {
                    online = p;
                    break;
                }
            }
        }
        if (online != null) {
            return new OwnerMatch(online.getUniqueId().toString(), online.getName());
        }
        for (ShopListing shop : shops.store().all()) {
            if (shop.ownerName() != null && shop.ownerName().equalsIgnoreCase(q)) {
                return new OwnerMatch(
                        shop.ownerUuid() != null ? shop.ownerUuid() : "",
                        shop.ownerName());
            }
        }
        try {
            @SuppressWarnings("deprecation")
            var offline = Bukkit.getOfflinePlayer(q);
            if (offline != null && (offline.hasPlayedBefore() || offline.isOnline())) {
                UUID id = offline.getUniqueId();
                String name = offline.getName() != null ? offline.getName() : q;
                for (ShopListing shop : shops.store().all()) {
                    if (shop.ownerUuid() != null && shop.ownerUuid().equalsIgnoreCase(id.toString())) {
                        return new OwnerMatch(id.toString(), shop.ownerName() != null ? shop.ownerName() : name);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static ItemStack hubItem(HubRow row) {
        ItemStack stack = ShopItemKeys.stackForKey(row.itemKey());
        if (stack == null) {
            Material mat = ShopItemKeys.baseMaterial(row.itemKey());
            stack = new ItemStack(mat != null ? mat : Material.CHEST, 1);
        } else {
            stack = stack.clone();
            stack.setAmount(1);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(pretty(row.itemKey()), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(
                            String.format(Locale.US, "From %.3f G · %d seller(s)", row.cheapestPrice(), row.sellerCount()),
                            NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(
                            "In stock: " + row.inStockCount() + " shop(s) · " + row.totalStock() + " items",
                            row.inStockCount() > 0 ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click to see sellers", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack listingItem(RootMcShopsPlugin shops, ShopListing shop, boolean showOwner) {
        String itemKey = shops.itemKeyForStock(shop);
        ItemStack stack = ShopContainers.firstMatchingStack(shop, itemKey);
        if (stack == null) {
            stack = ShopItemKeys.stackForKey(itemKey);
        }
        if (stack == null) {
            Material mat = ShopItemKeys.baseMaterial(itemKey);
            stack = new ItemStack(mat != null ? mat : Material.CHEST, 1);
        } else {
            stack = stack.clone();
            stack.setAmount(1);
        }
        int stock = shops.countStock(shop);
        int qty = Math.max(1, shop.saleQty());
        String priceLine = qty > 1
                ? String.format(Locale.US, "%.3f G each (%d per trade)", shop.price(), qty)
                : String.format(Locale.US, "%.3f G", shop.price());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(
                            showOwner
                                    ? (shop.ownerName() != null ? shop.ownerName() : "Seller")
                                    : pretty(itemKey),
                            NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            if (showOwner) {
                lore.add(Component.text(pretty(itemKey), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text("Price: " + priceLine, NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Stock: " + stock, stock > 0 ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(
                            shop.world() + " " + shop.x() + ", " + shop.y() + ", " + shop.z(),
                            NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click for coords in chat", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack nav(Material material, String name) {
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(strip(name), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack infoBook(String title, List<String> loreLines) {
        ItemStack stack = new ItemStack(Material.BOOK, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(title, NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    static String pretty(String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return "Unknown";
        }
        String pretty = itemKey.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String part : pretty.split(" ")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    static String strip(String input) {
        if (input == null) {
            return "";
        }
        return input.replace('&', '§').replaceAll("§[0-9a-fk-or]", "");
    }

    public record HubRow(String itemKey, double cheapestPrice, int totalStock, int sellerCount, int inStockCount) {}

    public record OwnerMatch(String ownerUuid, String ownerName) {}
}
