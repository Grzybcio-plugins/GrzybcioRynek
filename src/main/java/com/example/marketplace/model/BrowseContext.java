package com.example.marketplace.model;

import com.example.marketplace.inventory.MarketCategory;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder(toBuilder = true)
public class BrowseContext {
    @Builder.Default
    private MarketCategory category = MarketCategory.ALL;
    @Builder.Default
    private SortMode sortMode = SortMode.NEWEST;
    @Builder.Default
    private int page = 1;
    private String searchQuery;
    private UUID sellerFilter;
    private String sellerFilterName;
    @Builder.Default
    private boolean favoritesOnly = false;

    public static BrowseContext defaults() {
        return BrowseContext.builder().build();
    }

    public BrowseContext withPage(int newPage) {
        return toBuilder().page(newPage).build();
    }

    public BrowseContext withCategory(MarketCategory newCategory) {
        return toBuilder().category(newCategory).page(1).build();
    }

    public BrowseContext withSort(SortMode mode) {
        return toBuilder().sortMode(mode).page(1).build();
    }

    public BrowseContext withSearch(String query) {
        return toBuilder().searchQuery(query).page(1).build();
    }
}
