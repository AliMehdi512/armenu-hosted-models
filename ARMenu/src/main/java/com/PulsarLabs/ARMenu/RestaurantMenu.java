package com.PulsarLabs.ARMenu;

import java.util.List;
import java.util.Map;

public class RestaurantMenu {
    public String restaurantName;
    public List<MenuItem> products;
    public Map<String, String> operatingHours;  // e.g., {"monday": "09:00-23:00", "tuesday": "09:00-23:00"}
    public String phoneNumber;
    public String address;
    public double averageRating;
    
    public RestaurantMenu(String restaurantName, List<MenuItem> products) {
        this.restaurantName = restaurantName;
        this.products = products;
        this.operatingHours = null;
        this.phoneNumber = "";
        this.address = "";
        this.averageRating = 0.0;
    }
    
    public RestaurantMenu(String restaurantName, List<MenuItem> products,
                         Map<String, String> operatingHours, String phoneNumber, 
                         String address, double averageRating) {
        this.restaurantName = restaurantName;
        this.products = products;
        this.operatingHours = operatingHours;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.averageRating = averageRating;
    }
    
    /**
     * Check if restaurant is currently open
     */
    public boolean isCurrentlyOpen() {
        if (operatingHours == null || operatingHours.isEmpty()) {
            return true; // If hours not provided, assume open
        }
        
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
            String[] daysOfWeek = {"", "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
            String today = daysOfWeek[dayOfWeek];
            
            String hoursStr = operatingHours.get(today);
            if (hoursStr == null) {
                return false;
            }
            
            String[] hours = hoursStr.split("-");
            if (hours.length != 2) {
                return true;
            }
            
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm");
            java.util.Date currentTime = cal.getTime();
            java.util.Date openTime = sdf.parse(hours[0]);
            java.util.Date closeTime = sdf.parse(hours[1]);
            
            return currentTime.after(openTime) && currentTime.before(closeTime);
        } catch (Exception e) {
            return true;
        }
    }
}
