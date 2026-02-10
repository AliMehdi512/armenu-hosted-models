package com.PulsarLabs.ARMenu;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages user favorites using SharedPreferences for local storage
 */
public class FavoritesManager {
    private static final String PREFS_NAME = "armenu_favorites";
    private static final String KEY_FAVORITES = "favorite_items";
    
    private SharedPreferences prefs;
    
    public FavoritesManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Add item to favorites
     */
    public void addFavorite(String itemId) {
        Set<String> favorites = getFavorites();
        favorites.add(itemId);
        saveFavorites(favorites);
    }
    
    /**
     * Remove item from favorites
     */
    public void removeFavorite(String itemId) {
        Set<String> favorites = getFavorites();
        favorites.remove(itemId);
        saveFavorites(favorites);
    }
    
    /**
     * Check if item is favorited
     */
    public boolean isFavorite(String itemId) {
        return getFavorites().contains(itemId);
    }
    
    /**
     * Get all favorite item IDs
     */
    public Set<String> getFavorites() {
        Set<String> emptySet = new HashSet<String>();
        return new HashSet<String>(prefs.getStringSet(KEY_FAVORITES, emptySet));
    }
    
    /**
     * Clear all favorites
     */
    public void clearFavorites() {
        prefs.edit().remove(KEY_FAVORITES).apply();
    }
    
    /**
     * Toggle favorite status
     */
    public boolean toggleFavorite(String itemId) {
        if (isFavorite(itemId)) {
            removeFavorite(itemId);
            return false;
        } else {
            addFavorite(itemId);
            return true;
        }
    }
    
    private void saveFavorites(Set<String> favorites) {
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply();
    }
}
