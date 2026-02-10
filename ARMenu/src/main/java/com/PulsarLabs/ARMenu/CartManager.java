package com.PulsarLabs.ARMenu;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple cart manager persisted in SharedPreferences as a JSON array.
 */
public class CartManager {
    private static final String PREFS = "ARMenuCartPrefs";
    private static final String KEY_CART = "cart_v1";
    private SharedPreferences prefs;

    public static class CartEntry {
        public String id;
        public String name;
        public double price;
        public String currency;
        public int qty;
    }

    public CartManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private JSONArray loadArray() {
        try {
            String s = prefs.getString(KEY_CART, "[]");
            return new JSONArray(s);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveArray(JSONArray a) {
        prefs.edit().putString(KEY_CART, a.toString()).apply();
    }

    public synchronized void addItem(MenuItem item, int qty) {
        if (item == null) return;
        try {
            JSONArray a = loadArray();
            // find existing
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.optString("id", "").equals(item.id)) {
                    int cur = o.optInt("qty", 1);
                    o.put("qty", cur + qty);
                    saveArray(a);
                    return;
                }
            }
            JSONObject o = new JSONObject();
            o.put("id", item.id);
            o.put("name", item.name);
            o.put("price", item.price);
            o.put("currency", item.currency != null ? item.currency : "");
            o.put("qty", qty);
            a.put(o);
            saveArray(a);
        } catch (Exception e) { }
    }

    public synchronized List<CartEntry> getCart() {
        List<CartEntry> out = new ArrayList<>();
        try {
            JSONArray a = loadArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                CartEntry e = new CartEntry();
                e.id = o.optString("id", "");
                e.name = o.optString("name", "");
                e.price = o.optDouble("price", 0.0);
                e.currency = o.optString("currency", "");
                e.qty = o.optInt("qty", 1);
                out.add(e);
            }
        } catch (Exception e) { }
        return out;
    }

    public synchronized int getTotalQuantity() {
        int s = 0;
        for (CartEntry e : getCart()) s += e.qty;
        return s;
    }

    public synchronized double getTotalPrice() {
        double tot = 0.0;
        for (CartEntry e : getCart()) tot += e.price * e.qty;
        return tot;
    }

    public synchronized void clearCart() {
        saveArray(new JSONArray());
    }

    public synchronized void removeItem(String id) {
        try {
            JSONArray a = loadArray();
            JSONArray na = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (!o.optString("id", "").equals(id)) na.put(o);
            }
            saveArray(na);
        } catch (Exception e) { }
    }
}

