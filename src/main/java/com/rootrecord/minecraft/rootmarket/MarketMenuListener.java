package com.rootrecord.minecraft.rootmarket;

import com.rootrecord.minecraft.rootmcshops.RootMcShopsPlugin;
import com.rootrecord.minecraft.rootmcshops.ShopListing;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.List;
import java.util.Locale;

public final class MarketMenuListener implements Listener {

    private final RootMarketPlugin plugin;

    public MarketMenuListener(RootMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MarketMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.getUniqueId().equals(holder.viewerId())) {
            return;
        }
        RootMcShopsPlugin shops = RootMcShopsPlugin.get();
        if (shops == null) {
            player.closeInventory();
            player.sendMessage(plugin.msg("need-chestshops"));
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        if (holder.mode() == MarketMenuHolder.Mode.ITEM && slot == MarketMenus.SLOT_BACK) {
            MarketMenus.openHub(plugin, shops, player);
            return;
        }

        if (slot == MarketMenus.SLOT_PREV && holder.page() > 0) {
            reopen(player, shops, holder, holder.page() - 1);
            return;
        }
        if (slot == MarketMenus.SLOT_NEXT) {
            reopen(player, shops, holder, holder.page() + 1);
            return;
        }
        if (slot >= MarketMenus.PAGE_SIZE) {
            return;
        }

        List<String> keysOrIds = holder.keysOrIds();
        if (slot >= keysOrIds.size()) {
            return;
        }
        String key = keysOrIds.get(slot);

        if (holder.mode() == MarketMenuHolder.Mode.HUB) {
            MarketMenus.openItem(plugin, shops, player, key);
            return;
        }

        ShopListing shop = shops.store().getById(key);
        if (shop == null) {
            return;
        }
        String owner = shop.ownerName() != null ? shop.ownerName() : "Seller";
        player.sendMessage(plugin.msg("coords")
                .replace("{player}", owner)
                .replace("{item}", shop.itemKey() != null ? shop.itemKey().toLowerCase(Locale.ROOT) : "?")
                .replace("{world}", shop.world())
                .replace("{x}", String.valueOf(shop.x()))
                .replace("{y}", String.valueOf(shop.y()))
                .replace("{z}", String.valueOf(shop.z()))
                .replace("{price}", String.format(Locale.US, "%.3f", shop.price())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MarketMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void reopen(Player player, RootMcShopsPlugin shops, MarketMenuHolder holder, int page) {
        switch (holder.mode()) {
            case HUB -> {
                List<MarketMenus.HubRow> rows = MarketMenus.buildHubRows(shops);
                if (rows.isEmpty()) {
                    player.closeInventory();
                    player.sendMessage(plugin.msg("hub-empty"));
                    return;
                }
                MarketMenus.openHubPage(plugin, shops, player, rows, page);
            }
            case ITEM -> {
                List<ShopListing> listings = MarketMenus.listingsForItem(shops, holder.itemKey());
                if (listings.isEmpty()) {
                    player.closeInventory();
                    player.sendMessage(plugin.msg("item-empty")
                            .replace("{item}", holder.itemKey().toLowerCase(Locale.ROOT)));
                    return;
                }
                MarketMenus.openItemPage(plugin, shops, player, holder.itemKey(), listings, page);
            }
            case PLAYER -> {
                MarketMenus.OwnerMatch match =
                        new MarketMenus.OwnerMatch(holder.ownerUuid(), holder.ownerName());
                List<ShopListing> listings =
                        MarketMenus.listingsForOwner(shops, match.ownerUuid(), match.ownerName());
                if (listings.isEmpty()) {
                    player.closeInventory();
                    player.sendMessage(plugin.msg("player-empty").replace("{player}", match.ownerName()));
                    return;
                }
                MarketMenus.openPlayerPage(plugin, shops, player, match, listings, page);
            }
        }
    }
}
