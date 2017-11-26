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

public class BeaconInterface extends Application implements BeaconConsumer, RangeNotifier {
    String TAG = "BeaconInterface";
    //Beacon Types
    // private static final String BEACON_FORMAT = "m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25";          // ALTBEACON
    // private static final String BEACON_FORMAT = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25";          // ALTBEACON2
    // private static final String BEACON_FORMAT =  "x,s:0-1=feaa,m:2-2=20,d:3-3,d:4-5,d:6-7,d:8-11,d:12-15";    // EDDYSTONE_TLM
    // private static final String BEACON_FORMAT = "s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19";               // EDDYSTONE_UID
    // private static final String BEACON_FORMAT = "s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-20v";                      // EDDYSTONE_URL
    // private static final String BEACON_FORMAT = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24";                  // IBEACON
    private static final String BEACON_FORMAT = "m:0-1=00c5," +   // Matching byte sequence
                                                "i:0-0," +        // Must have one byte of identifier
                                                "p:1-1," +        // Calibrated RSSI (power calibration field)
                                                "d:2-2";          // Battery level (data field)

    public String selectedBeacon = "0";

    private BeaconManager beaconManager;

    public int numTrackedObj = 0;
    private int BINSIZE = 30;

    //Data Processing Variables
    //initial values for the kalman filter
    private double x_est_last;
    private double P_last;

    //simulated noise in the system
    //initial covariances of state noise, measurement noise
    private double Q = 0.001; // noise w's covariance - process noise (determined experimentally) (0.022)
    private double V = 0.8; //noise of v's covariance - measurement noise (determined experimentally) (0.617)

    //Kalman Filter Values
    private double K; //Kalman Gain
    private double P; //Prediction error covariance
    private double P_temp;
    private double x_temp_est; // holds the temporary estimated value while doing calculations
    private double x_est; //our kalman estimate
    private double z_measured; //the 'noisy' value we measured
    private double z_real; //the ideal value we wish to measure (THIS IS TRUELY UNKNOWN) calculated as the mean of our data set

    private double InnerFence_1;
    private double InnerFence_3;
    private double OutlierCount;

    public ArrayList<String> deviceMAC = new ArrayList<>();     // A vector of available beacon MAC addresses
    public ArrayList<String> deviceList = new ArrayList<>();    // A vector of available beacon details for display
    private ArrayList<Double> RSSIContainer = new ArrayList<>(); //a vector of type double
    public ArrayList<Double> RSSIContainer_copy = new ArrayList<>(); //copy of RSSIContainer used for data processing

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().clear();   // Remove default beacon parser so only ours is filtered
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BEACON_FORMAT));

        //SET THE SCAN APP SCAN PERIOD
        beaconManager.setForegroundScanPeriod(250L);
        beaconManager.setForegroundBetweenScanPeriod(0L);
        beaconManager.setBackgroundScanPeriod(250L);
        beaconManager.setBackgroundBetweenScanPeriod(0L);
        try {
            beaconManager.updateScanPeriods();
        } catch (RemoteException e) { }

        // Bind service to this thread
        beaconManager.bind(this);

        Log.i(TAG,"---------- running class onCreate");
    }

    @Override
    public void onBeaconServiceConnect() {
        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myMonitoringUniqueId", null, null, null));
        } catch (RemoteException e) { e.printStackTrace(); }
        beaconManager.addRangeNotifier(this);
    }

    @Override
    public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
        numTrackedObj = beacons.size();         // Update the number of tracked beacons
        if (numTrackedObj > 0) {                // If beacons found
            for (Beacon beacon : beacons) {     // Check all beacons
                /* Update DeviceDiscovery data */
                if (!deviceMAC.contains(beacon.getBluetoothAddress()))      // If beacon is not on the list, add it
                {
                    String deviceInfo = beacon.getBluetoothName() + "    " + beacon.getBluetoothAddress() + "    " + beacon.getDataFields().get(0).intValue() + "%";
                    Log.i(TAG,"----------" + deviceInfo + " found");
                    deviceList.add(deviceInfo);
                    deviceMAC.add(beacon.getBluetoothAddress());
                }

                /* Update RangeFinder data */
                if (beacon.getBluetoothAddress().equals(selectedBeacon)) {  //If packet is from the selected beacon
                    RSSIContainer.add((double) beacon.getRssi()); //appends the latest data point to the end
                    //Fill our ArrayList with test points
                    if (RSSIContainer.size() > BINSIZE) {  // If bin is overflowing
                        //apply rolling window of data points (remove oldest data point and add newest data point)
                        RSSIContainer.remove(0); //removes the oldest data point
                        updateRange();      //update proximity map
                    } else { // Bin is not full yet
                        if (RSSIContainer.size() >= 2) {     // 2+ data points required for filtering
                            updateRange();  //update proximity map
                        }
                    }
                }
            }
        }
    }

    private void updateRange() {
        if (numTrackedObj != 0) {
            RSSIContainer_copy.clear();
            RSSIContainer_copy.addAll(RSSIContainer); //Make a copy of our data so we don't lose the original
            //perform processing
            removeOutliers(RSSIContainer_copy);
            KFilter(RSSIContainer_copy);
            smoothRSSI(RSSIContainer_copy); //Change to apply running average
            removeOutliers(RSSIContainer_copy);
        }
    }

    public void resetFilter() {
        RSSIContainer.clear();
        RSSIContainer_copy.clear();
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

    void smoothRSSI(ArrayList<Double> v)
    {
        double alpha = 0.5; //smoothing constant
        int count = 1; //used for running mean
        double RSSI = 0; //temp value used to hold the current RSSI data
        double RSSIRunningMean = RSSI; //initialize running mean

        //smooth every data point iteratively
        for (int i = 0; i < v.size(); i++)
        {
            RSSI = v.get(i);

            //Don't update for our first data point, since its our first we have no RunningMean yet
            if (count > 1)
                v.set(i,(alpha * RSSI + (1 - alpha) * RSSIRunningMean)); //inserts the smoothed value back into the arrayList

            RSSIRunningMean -= RSSIRunningMean / count;
            RSSIRunningMean += RSSI / count; // running mean is up to date
            count++;
        }
    }

    private boolean isOutlier(double value)
    {
        if (InnerFence_3 > value && value > InnerFence_1) //if the value is between the inner fences
            return false;

        OutlierCount++;
        return true;
    }

    private void removeOutliers( ArrayList<Double> v)
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

    private void KFilter(ArrayList<Double> v)
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
