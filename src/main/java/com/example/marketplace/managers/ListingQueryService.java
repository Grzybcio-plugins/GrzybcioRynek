package com.example.marketplace.managers;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.inventory.MarketCategory;
import com.example.marketplace.model.BrowseContext;
import com.example.marketplace.model.MarketListing;
import com.example.marketplace.model.SortMode;
import com.example.marketplace.util.ItemSearchUtil;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ListingQueryService {
    private final MarketPlace plugin;

    public ListingQueryService(MarketPlace plugin) {
        this.plugin = plugin;
    }

    public List<MarketListing> query(BrowseContext context, java.util.UUID viewer) {
        List<MarketListing> listings = plugin.getStorageManager().getAllListings();

        return listings.stream()
            .filter(listing -> context.getCategory() == null || context.getCategory().matches(listing))
            .filter(listing -> context.getSellerFilter() == null || listing.getSeller().equals(context.getSellerFilter()))
            .filter(listing -> ItemSearchUtil.matches(listing, context.getSearchQuery()))
            .filter(listing -> !context.isFavoritesOnly() || isFavorite(viewer, listing.getId()))
            .sorted(resolveSort(context.getSortMode()).comparator())
            .collect(Collectors.toList());
    }

    private boolean isFavorite(java.util.UUID viewer, int listingId) {
        if (viewer == null) {
            return false;
        }
        Set<Integer> favorites = plugin.getExtendedDataStore().getFavorites(viewer);
        return favorites.contains(listingId);
    }

    private SortMode resolveSort(SortMode mode) {
        return mode == null ? SortMode.NEWEST : mode;
    }

    public List<MarketListing> getPlayerListings(java.util.UUID seller) {
        return plugin.getStorageManager().getListingsBySeller(seller).stream()
            .sorted(SortMode.NEWEST.comparator())
            .collect(Collectors.toList());
    }
}
