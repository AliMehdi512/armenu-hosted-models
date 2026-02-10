package com.PulsarLabs.ARMenu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.text.InputType;

import java.util.List;

/**
 * Simple cart review activity: shows items, total and "Place Order".
 */
public class CartActivity extends Activity {

    private CartManager cartManager;
    private EditText tableInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cartManager = new CartManager(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);

        TextView title = new TextView(this);
        title.setText("Your Cart");
        title.setTextSize(22);
        title.setPadding(0,0,0,12);
        root.addView(title);

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Populate
        List<CartManager.CartEntry> items = cartManager.getCart();
    for (final CartManager.CartEntry e : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0,8,0,8);

            TextView name = new TextView(this);
            name.setText(e.name + " x" + e.qty);
            name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(name);

            TextView price = new TextView(this);
            price.setText(String.format("%s %.2f", e.currency != null && e.currency.length()>0 ? e.currency : "", e.price * e.qty));
            price.setGravity(Gravity.RIGHT);
            row.addView(price);

            list.addView(row);
        }
    TextView total = new TextView(this);
    total.setTextSize(18);
    total.setPadding(0,12,0,12);
    String currency = getCartCurrency();
    total.setText(String.format("Total: %s %.2f", currency, cartManager.getTotalPrice()));
    root.addView(total);

    // Table number input (required before placing order)
    tableInput = new EditText(this);
    tableInput.setHint("Table No (required)");
    tableInput.setInputType(InputType.TYPE_CLASS_TEXT);
    tableInput.setPadding(0,8,0,8);
    root.addView(tableInput);

        Button place = new Button(this);
        place.setText("Place Order");
        place.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String tableNo = tableInput.getText() != null ? tableInput.getText().toString().trim() : "";
                if (tableNo.length() == 0) {
                    Toast.makeText(CartActivity.this, "Please enter table number before placing order.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // For demo: clear cart, pass table number to ThankYouActivity
                cartManager.clearCart();
                Toast.makeText(CartActivity.this, "Order placed!", Toast.LENGTH_SHORT).show();
                Intent i = new Intent(CartActivity.this, ThankYouActivity.class);
                i.putExtra("FoodName", "Your Order");
                i.putExtra("TableNo", tableNo);
                startActivity(i);
                finish();
            }
        });
        root.addView(place);

        setContentView(root);
    }

    private String getCartCurrency() {
        List<CartManager.CartEntry> items = cartManager.getCart();
        for (CartManager.CartEntry e : items) {
            if (e.currency != null && e.currency.length() > 0) return e.currency;
        }
        return "";
    }
}


