package com.androidweardocs.wearabledatamap;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONException;
import org.json.JSONObject;

public class WatchDataMapActivity extends WearableActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        MessageApi.MessageListener {

    GoogleApiClient googleClient;

    private TextView statustext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_map);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                statustext = (TextView) stub.findViewById(R.id.statustext);
            }
        });

        setAmbientEnabled();

        // Register the local broadcast receiver
        IntentFilter messageFilter = new IntentFilter(Intent.ACTION_SENDTO);
        MessageReceiver messageReceiver = new MessageReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter);

        googleClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        startService(new Intent(this, SensorService.class));

        //set up listener for MessageApi
        Wearable.MessageApi.addListener(googleClient, this);
    }

    // Connect to the data layer when the Activity starts
    @Override
    protected void onStart() {
        super.onStart();
        googleClient.connect();
    }

    @Override
    public void onConnected(Bundle connectionHint) {


    }

    // Disconnect from the data layer when the Activity stops
    @Override
    protected void onStop() {
        if (null != googleClient && googleClient.isConnected()) {
            googleClient.disconnect();
        }
        super.onStop();
    }

    // Placeholders for required connection callbacks
    @Override
    public void onConnectionSuspended(int cause) { }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.wtf("MQP", connectionResult.getErrorMessage());
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {}


    public void sendDataToPhone(Bundle data) {
        String MOBILE_DATA_PATH = "/mobile_data";

        new SendToPhone(MOBILE_DATA_PATH, data).start();
    }

    /**
     * Convert a bundle to JSON. Messsage API only takes strings, may be easier
     * to convert JSON to string and back than converting bundle to string and back
     * @param bundle
     * @return
     */
    public JSONObject bundleToJSON(Bundle bundle) {
        JSONObject json = new JSONObject();

        for(String key : bundle.keySet()) {
            try {
                json.put(key, JSONObject.wrap(bundle.get(key)));

            } catch(JSONException e) {
                //handle exception
            }
        }

        return json;
    }

    public class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle data = intent.getBundleExtra("datamap");

            if (data.getString("type").equals("status")) {
//                final TextView tv = (TextView) findViewById(R.id.statustext);
//                tv.setText(data.getString("content"));
            } else if (data.getString("type").equals("data")) {
                sendDataToPhone(data);
            }

        }
    }


    class SendToPhone extends Thread {
        String path;
        Bundle data;

        // Constructor for sending data objects to the data layer
        SendToPhone(String p, Bundle data) {
            path = p;
            this.data = data;
        }

        public void run() {

            String DATA_RECEIVER_CAPABILITY = "data_receiver";

            //retrieve node that can receive data(phone)
            CapabilityApi.GetCapabilityResult result =
                    Wearable.CapabilityApi.getCapability(
                            googleClient, DATA_RECEIVER_CAPABILITY,
                            CapabilityApi.FILTER_REACHABLE).await();
            String nodeId = result.getCapability().getNodes().iterator().next().getId();

            //send data to phone
            JSONObject dataJson = bundleToJSON(data);
            Wearable.MessageApi.sendMessage(googleClient, nodeId,
                    path, dataJson.toString().getBytes());
        }
    }

}

