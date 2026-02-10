package com.PulsarLabs.ARMenu;

import java.util.List;

/**
 * Minimal placeholder search/filter. Implement richer filtering later.
 */
public class MenuSearchFilter {
    public static List<MenuItem> filter(List<MenuItem> items, String query) {
        // simple case-insensitive name contains
        if (items == null) return items;
        if (query == null || query.trim().length() == 0) return items;
        java.util.ArrayList<MenuItem> out = new java.util.ArrayList<>();
        String q = query.toLowerCase();
        for (MenuItem it : items) {
            if (it.name != null && it.name.toLowerCase().contains(q)) out.add(it);
        }
        return out;
    }
}
