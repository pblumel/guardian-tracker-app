package com.projectguardian.guardian;

import android.app.Application;
import android.content.res.Configuration;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

public class BeaconInterface extends Application implements BeaconConsumer {
    String TAG = "BeaconInterface";
    //Beacon Types
    // public static final String BEACON_FORMAT = "m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25";          // ALTBEACON
    public static final String BEACON_FORMAT = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25";             // ALTBEACON2
    // public static final String BEACON_FORMAT =  "x,s:0-1=feaa,m:2-2=20,d:3-3,d:4-5,d:6-7,d:8-11,d:12-15";    // EDDYSTONE_TLM
    // public static final String BEACON_FORMAT = "s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19";               // EDDYSTONE_UID
    // public static final String BEACON_FORMAT = "s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-20v";                      // EDDYSTONE_URL
    // public static final String BEACON_FORMAT = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24";                  // IBEACON

    private static final String TESTBEACON = "88:6B:0F:2E:2F:20"; // RadBeacon USB MAC

    public BeaconManager beaconManager;

    public int numTrackedObj = 0;
    private static final int BINSIZE = 8;

    //Data Processing Variables
    //initial values for the kalman filter
    double x_est_last;
    double P_last;

    //simulated noise in the system
    //initial covariances of state noise, measurement noise
    double Q = 0.001; // noise w's covariance - process noise (determined experimentally) (0.022)
    double V = 0.8; //noise of v's covariance - measurement noise (determined experimentally) (0.617)

    //Kalman Filter Values
    double K; //Kalman Gain
    double P; //Prediction error covariance
    double P_temp;
    double x_temp_est; // holds the temporary estimated value while doing calculations
    double x_est; //our kalman estimate
    double z_measured; //the 'noisy' value we measured
    double z_real; //the ideal value we wish to measure (THIS IS TRUELY UNKNOWN) calculated as the mean of our data set

    double InnerFence_1;
    double InnerFence_3;
    double OutlierCount;

    public ArrayList<String> names = new ArrayList<>(); //a vector of type String
    ArrayList<Double> RSSIContainer = new ArrayList<>(); //a vector of type double
    public ArrayList<Double> RSSIContainer_copy = new ArrayList<>(); //copy of RSSIContainer used for data processing

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
                    /* Update DeviceDiscovery data */
                    String deviceInfo = beacons.iterator().next().getBluetoothName() + " " + beacons.iterator().next().getBluetoothAddress();
                    if (!names.contains(deviceInfo))
                    {
                        Log.i(TAG,"----------" + deviceInfo + " found");
                        names.add(deviceInfo);
                    }

                    /* Update RangeFinder data */
                    //If are receiving packets from the test beacon
                    if (beacons.iterator().next().getBluetoothAddress().equals(TESTBEACON)) {
                        //Fill our ArrayList with (8 - test points)
                        if (RSSIContainer.size() == BINSIZE) {
                            //apply rolling window of 8 data points (know oldest data point off and add newest data point
                            RSSIContainer.add((double) beacons.iterator().next().getRssi()); //appends the latest data point to the end
                            RSSIContainer.remove(0); //removes the oldest data point
                            updateRange();
                        } else //continue filling up our container till 8 data points
                        {
                            RSSIContainer.add((double) beacons.iterator().next().getRssi()); //appends the latest data point to the end

                            if (RSSIContainer.size() == BINSIZE) {
                                //update proximity map for the first time
                                updateRange();
                            }
                        }
                    }
                }
                else    //there are no beacons in the region
                {
                    //waiting for beacons to appear
                    updateRange();
                }
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myMonitoringUniqueId", null, null, null));
        } catch (RemoteException e) { }
    }

    private void updateRange() {
        if (numTrackedObj != 0) {
            RSSIContainer_copy.clear();
            RSSIContainer_copy.addAll(RSSIContainer); //Make a copy of our data so we don't lose the original
            //perform processing
            removeOutliers(RSSIContainer_copy);
            KFilter(RSSIContainer_copy);
            //smoothRSSI(copy); //Change to apply running average
            removeOutliers(RSSIContainer_copy);
        }
    }

    public double getMean(ArrayList<Double> v)
    {
        double mean = 0;

        //sum all values
        for (int i = 0; i < v.size(); i++)
            mean += v.get(i);

        mean = mean / v.size();
        return mean;
    }

    boolean isOutlier(double value)
    {
        if (InnerFence_3 > value && value > InnerFence_1) //if the value is between the inner fences
            return false;

        OutlierCount++;
        return true;
    }

    void removeOutliers( ArrayList<Double> v)
    {
        //make a temp
        ArrayList<Double> temp = v;

        //get quartile positions
        int  Q1 = temp.size() / 4;
        int  Q2 = temp.size() / 2;
        int  Q3 = Q1 + Q2;

        //Sort our data set
        Collections.sort(temp);

        //find IQR and Quartiles
        double Quartile_1 = temp.get(Q1);
        double Quartile_3 = temp.get(Q3);
        double IQR = Quartile_3 - Quartile_1;

        //find inner fences
        InnerFence_1 = Quartile_1 - (1.5*IQR);
        InnerFence_3 = Quartile_3 + (1.5*IQR);

        //Remove all Outliers from original vector v
        for (int i = 0; i < v.size(); i++)
        {

            if (isOutlier(v.get(i))) {
                v.remove(i); //removes outliers
            }
        }
    }

    void KFilter(ArrayList<Double> v)
    {
        z_real = getMean(v); //returns the mean of our data set as the "ideal" value we wish to measure
        x_est_last = z_real; // Initializes a piece of data for our Kalman Filter

        // Filter All of our data
        for (int i = 0; i < v.size(); i++)
        {
            //do a prediction (based solely on past measurements)
            //a priori state estimate
            x_temp_est = x_est_last;  //x^j = ax^(j-1) + bu assuming b = 0 and a = 1

            //a priori covariance
            P_temp = P_last + Q;  //Pj = a^2*P(j-1) + Q //assuming a = 1

            //Calculate Kalman Gain
            K = P_temp / (P_temp + V); //assuming h = 1

            //Measured Data
            z_measured = v.get(i); //our data

            //Refine (correct) the a priori estimate to give us the posteriori estimates
            x_est = x_temp_est + K * (z_measured - x_temp_est); //refined estimate

            //a posteriori covariance
            P = (1 - K) * P_temp;// assuming h = 1

            //update our data set after filtering
            v.set(i,x_est);

            //update our lasts
            P_last = P;
            x_est_last = x_est; // x-1 = x1
        }
    }
}