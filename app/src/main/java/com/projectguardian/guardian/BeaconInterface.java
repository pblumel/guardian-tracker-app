package com.projectguardian.guardian;

import android.app.Application;
import android.content.res.Configuration;
import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

public class BeaconInterface extends Application implements BeaconConsumer, Serializable {
    String TAG = "RESULT";
    //Beacon Types
    // public static final String BEACON_FORMAT = "m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25";          // ALTBEACON
    public static final String BEACON_FORMAT = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25";             // ALTBEACON2
    // public static final String BEACON_FORMAT =  "x,s:0-1=feaa,m:2-2=20,d:3-3,d:4-5,d:6-7,d:8-11,d:12-15";    // EDDYSTONE_TLM
    // public static final String BEACON_FORMAT = "s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19";               // EDDYSTONE_UID
    // public static final String BEACON_FORMAT = "s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-20v";                      // EDDYSTONE_URL
    // public static final String BEACON_FORMAT = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24";                  // IBEACON

    public BeaconManager beaconManager;

    public int numTrackedObj = 0;
    public ArrayList<String> names = new ArrayList<>(); //a vector of type String

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BEACON_FORMAT));

        // Bind service to this thread
        beaconManager.bind(this);

        //SET THE SCAN APP SCAN PERIOD
        beaconManager.setForegroundScanPeriod(5000L);
        beaconManager.setBackgroundBetweenScanPeriod(1100L);

        Log.i(TAG,"---------- running class onCreate");
    }

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                numTrackedObj = beacons.size(); //update the number of beacons we are tracking
                Log.i(TAG,"----------" + numTrackedObj + " beacon(s) found");
                if (numTrackedObj > 0) {
                    // Add Beacon to list
                    String deviceInfo = beacons.iterator().next().getBluetoothName() + " " + beacons.iterator().next().getBluetoothAddress();
                    if (!names.contains(deviceInfo))
                    {
                        Log.i(TAG,"----------" + deviceInfo + " found");
                        names.add(deviceInfo);
                    }
                }
            }
        });
    }
}
