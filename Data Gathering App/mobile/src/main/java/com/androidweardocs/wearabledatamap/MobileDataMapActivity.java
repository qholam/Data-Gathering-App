package com.androidweardocs.wearabledatamap;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Chronometer;
import android.widget.EditText;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;

import android.net.Uri;

import weka.core.Attribute;
import weka.core.Instances;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.converters.ArffSaver;


public class MobileDataMapActivity extends AppCompatActivity implements
        SensorEventListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener{

    GoogleApiClient googleClient;

    private Chronometer chronometer;

    private EditText labelField;

    ArrayList<Attribute> attributes;
    Instances mDataset;

    int hasPermission = 0;

    boolean serviceStarted = false;

    String mClass = "no_goggles";

    Intent serviceIntent;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private FileWriter writer;
    private File csvOutput;

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent se) {
        if(!serviceStarted) return;
        if (se.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {

            try {
                float pax = se.values[0];
                float pay = se.values[1];
                float paz = se.values[2];
                long t = se.timestamp;
                writer.write("acc,"+t+','+pax+','+pay+','+paz+','+mClass+'\n');
                Log.wtf("PHONE", "writting accel data");
            } catch (IOException e) {
                Log.wtf("PHONE",e);
            }
        }
        else if (se.sensor.getType() == Sensor.TYPE_GYROSCOPE) {

            try {
                float pgx = se.values[0];
                float pgy = se.values[1];
                float pgz = se.values[2];
                long t = se.timestamp;
                writer.write("gyr,"+t+','+pgx+','+pgy+','+pgz+','+mClass+'\n');
                Log.wtf("PHONE", "writting gyro data");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer,
                SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mGyroscope,
                SensorManager.SENSOR_DELAY_FASTEST);
        try {
            if (serviceStarted) {
                writer = new FileWriter(csvOutput.getAbsolutePath(),true);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        if(writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_map);
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        attributes = new ArrayList<>();
        attributes.add(new Attribute("dt"));
        attributes.add(new Attribute("wax"));
        attributes.add(new Attribute("way"));
        attributes.add(new Attribute("waz"));
        attributes.add(new Attribute("wgx"));
        attributes.add(new Attribute("wgy"));
        attributes.add(new Attribute("wgz"));
        ArrayList class_nominal_values = new ArrayList<String>(5);
        class_nominal_values.add("no_goggles");
        class_nominal_values.add("green_goggles");
        class_nominal_values.add("black_goggles");
        class_nominal_values.add("red_goggles");
        class_nominal_values.add("orange_goggles");
        attributes.add(new Attribute("goggles_class", class_nominal_values));

        mDataset = new Instances("mqp_features", attributes, 10000);
        mDataset.setClassIndex(mDataset.numAttributes() - 1);

        chronometer = (Chronometer) findViewById(R.id.chronometer);
        labelField = (EditText) findViewById(R.id.editText);

        // Register the local broadcast receiver
        IntentFilter messageFilter = new IntentFilter(Intent.ACTION_SEND);
        MessageReceiver messageReceiver = new MessageReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter);

        // Build a new GoogleApiClient for the the Wearable API
        googleClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();



        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                    hasPermission);
        }

        serviceIntent = new Intent(this, MobileListenerService.class);
        startService(serviceIntent);

        final RadioButton radioNoGoggles = (RadioButton) findViewById(R.id.radioButton);
        final RadioButton radioGreenGoggles = (RadioButton) findViewById(R.id.radioButton2);
        final RadioButton radioBlackGoggles = (RadioButton) findViewById(R.id.radioButton3);
        final RadioButton radioRedGoggles = (RadioButton) findViewById(R.id.radioButton4);
        final RadioButton radioOrangeGoggles = (RadioButton) findViewById(R.id.radioButton5);
        radioNoGoggles.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                mClass = "no_goggles";
            }
        });
        radioGreenGoggles.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                mClass = "green_goggles";
            }
        });
        radioBlackGoggles.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                mClass = "black_goggles";
            }
        });
        radioRedGoggles.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                mClass = "red_goggles";
            }
        });
        radioOrangeGoggles.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){ mClass = "orange_goggles";    }
        });

        final Button button = (Button) findViewById(R.id.sendButton);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serviceStarted) {
                    radioNoGoggles.setEnabled(true);
                    radioGreenGoggles.setEnabled(true);
                    radioBlackGoggles.setEnabled(true);
                    radioRedGoggles.setEnabled(true);
                    radioOrangeGoggles.setEnabled(true);
                    button.setText("Start Collecting");
                    serviceStarted = false;
                    chronometer.stop();
                    saveToFile();
                } else {
                    csvOutput = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), labelField.getText() + "-" + System.currentTimeMillis() + "phone.csv");
                    try {
                        writer = new FileWriter(csvOutput.getAbsolutePath(),true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    radioNoGoggles.setEnabled(false);
                    radioGreenGoggles.setEnabled(false);
                    radioBlackGoggles.setEnabled(false);
                    radioRedGoggles.setEnabled(false);
                    radioOrangeGoggles.setEnabled(false);
                    serviceIntent.putExtra("class", mClass);
                    button.setText("Stop Collecting");
                    chronometer.setBase(SystemClock.elapsedRealtime());
                    chronometer.start();
                    mDataset.delete();
                    mDataset = new Instances("mqp_features", attributes, 10000);
                    mDataset.setClassIndex(mDataset.numAttributes() - 1);
                    serviceStarted = true;
                }
            }
        });

    }

    protected void saveToFile(){
        File outputFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), labelField.getText() + "-" + System.currentTimeMillis() + "watch.arff");
        if (mDataset != null) {
            ArffSaver saver = new ArffSaver();
            saver.setInstances(mDataset);


            Log.wtf("MQP", "FILE " + outputFile.getAbsolutePath());
            try {
                saver.setFile(outputFile);
                saver.writeBatch();

                Intent intent =
                        new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(Uri.fromFile(outputFile));
                sendBroadcast(intent);
            } catch (IOException e) {
                Log.wtf("MQP", "error saving");
                e.printStackTrace();
            }

        } else {
            Log.wtf("MQP", "Dataset NULL");
        }
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

    public void onDestroy(){
        stopService(serviceIntent);
        super.onDestroy();
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
    public void onConnectionFailed(ConnectionResult connectionResult) { }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_data_map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public class MessageReceiver extends BroadcastReceiver {

        public final DecimalFormat df = new DecimalFormat("#.##");
        public CountDownTimer cdt = null;

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle data = intent.getBundleExtra("datamap");
            if (data.getString("type").equals("status")) {
                //
            } else if (data.getString("type").equals("data")) {

                 // Log the data
                long[] dt = data.getLongArray("dt");
                float[] wax = data.getFloatArray("wax");
                float[] way = data.getFloatArray("way");
                float[] waz = data.getFloatArray("waz");
                float[] wgx = data.getFloatArray("wgx");
                float[] wgy = data.getFloatArray("wgy");
                float[] wgz = data.getFloatArray("wgz");

                String output = "Receiving Data";
                TextView tv = (TextView) findViewById(R.id.output);
                tv.setText(output);
                Button startButton = (Button) findViewById(R.id.sendButton);
                startButton.setEnabled(true);

                if (serviceStarted) {
                    if (attributes == null) {
                        Log.wtf("MQP", "attributes null");
                        return;
                    }

                    for (int i = 0; i < wax.length; i++) {
                        Instance inst = new DenseInstance(attributes.size());
                        inst.setDataset(mDataset);
                        inst.setValue(mDataset.attribute("dt"), dt[i]);
                        inst.setValue(mDataset.attribute("wax"), wax[i]);
                        inst.setValue(mDataset.attribute("way"), way[i]);
                        inst.setValue(mDataset.attribute("waz"), waz[i]);
                        inst.setValue(mDataset.attribute("wgx"), wgx[i]);
                        inst.setValue(mDataset.attribute("wgy"), wgy[i]);
                        inst.setValue(mDataset.attribute("wgz"), wgz[i]);
                        inst.setValue(mDataset.attribute("goggles_class"), mClass);
                        mDataset.add(inst);
                    }
                }
                if(cdt!=null) cdt.cancel();
                cdt = new CountDownTimer(3000, 3000) {
                    public void onTick(long millisUntilFinished) { }
                    public void onFinish() {
                        String cl = "Connection lost";
                        TextView tv = (TextView) findViewById(R.id.output);
                        tv.setText(cl);
                        Button startButton = (Button) findViewById(R.id.sendButton);
                        startButton.setEnabled(false);
                    }
                }.start();
            }
        }
    }
}
