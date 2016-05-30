package com.example.android.sunshine.app;

import android.content.Intent;

import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

public class MyService extends WearableListenerService {

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
//        super.onDataChanged(dataEvents);
        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                String path = dataEvent.getDataItem().getUri().getPath();
                if (path.equals("/temperature-data")) {
                    float highTemp;
                    highTemp = dataMap.getFloat("high_temp");
                    float lowTemp;
                    lowTemp = dataMap.getFloat("low_temp");
                    Asset asset = dataMap.getAsset("weather_image");

                    Intent intent = new Intent("com.sheajohn.wear.DATA_UPDATED");
                    intent.putExtra("low-temp", lowTemp);
                    intent.putExtra("high-temp", highTemp);
                    intent.putExtra("weather-image", asset);

                    sendBroadcast(intent);
                }

            }
        }
    }
}
