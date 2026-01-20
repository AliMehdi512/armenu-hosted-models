package com.PulsarLabs.ARMenu;

import android.app.FragmentManager;
import android.os.Bundle;
import android.os.Handler;


/** UnityPlayerNativeActivity.java - Initiates the Unity/AR activity - Launcher
 *
 *  The following UnityPlayerNativeActivity was generated from exporting Unity project as Gradle
 *
 *  Note: @deprecated It's recommended that you base your code directly on UnityPlayerActivity
 *  or make your own NativeActivity implementation.
 */

public class UnityPlayerNativeActivity extends UnityPlayerActivity
{

	@Override protected void onCreate (Bundle savedInstanceState)
	{
		//Log.w("Unity", "UnityPlayerNativeActivity has been deprecated, please update your AndroidManifest to use UnityPlayerActivity instead");
		super.onCreate(savedInstanceState);

		// Handle deep link: armenu://order?food=FoodKey
		if (getIntent() != null && getIntent().getData() != null) {
			android.net.Uri data = getIntent().getData();
			String foodParam = data.getQueryParameter("food");
			if (foodParam != null && foodParam.length() > 0) {
				// Start ReceiptActivity directly with the provided food name
				android.content.Intent intent = new android.content.Intent(this, ReceiptActivity.class);
				intent.putExtra("FoodName", foodParam);
				intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent);
				// finish this activity if you don't want Unity to run immediately
				finish();
			}
		}
	}
}
