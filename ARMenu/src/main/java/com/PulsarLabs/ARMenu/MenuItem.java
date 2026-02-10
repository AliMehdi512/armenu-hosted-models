package com.PulsarLabs.ARMenu;

public class MenuItem {
    public String id;
    public String name;
    public String model;
    public String thumbnail;
    public double price; // base price
    public String currency;
    public String description;
    public String category;
    public boolean isAvailable;
    
    public MenuItem(String id, String name, String model, String thumbnail) {
        this(id, name, model, thumbnail, 0.0, "", "", "", true);
    }

    public MenuItem(String id, String name, String model, String thumbnail, double price, String currency, String description, String category, boolean isAvailable) {
        this.id = id;
        this.name = name;
        this.model = model;
        this.thumbnail = thumbnail;
        this.price = price;
        this.currency = currency;
        this.description = description;
        this.category = category;
        this.isAvailable = isAvailable;
    }

    public void setPrice(double price, String currency) {
        this.price = price;
        this.currency = currency != null ? currency : "";
    }

    public org.json.JSONObject toJson() {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("model", model);
            o.put("thumbnail", thumbnail);
            o.put("price", price);
            o.put("currency", currency);
            o.put("description", description);
            o.put("category", category);
            o.put("isAvailable", isAvailable);
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    public static MenuItem fromJson(org.json.JSONObject prod) {
        try {
            String id = prod.optString("id", "");
            String name = prod.optString("name", "");
            String model = prod.optString("model", "");
            String thumbnail = prod.optString("thumbnail", "");
            MenuItem mi = new MenuItem(id, name, model, thumbnail);
            if (prod.has("price")) {
                try { mi.price = prod.getDouble("price"); } catch (Exception ignored) {}
            }
            mi.currency = prod.optString("currency", "");
            mi.description = prod.optString("description", "");
            mi.category = prod.optString("category", "");
            mi.isAvailable = prod.optBoolean("isAvailable", true);
            return mi;
        } catch (Exception e) {
            return null;
        }
    }
}
