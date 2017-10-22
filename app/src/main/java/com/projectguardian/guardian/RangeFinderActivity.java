package com.projectguardian.guardian;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class RangeFinderActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, BeaconConsumer {

    private static final int BINSIZE = 8;
    private static final String TESTBEACON = "88:6B:0F:2E:2F:20"; //test beacons MAC
    private BeaconManager beaconManager2;

    //Beacon Types
    // public static final String ALTBEACON = "m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25";
    public static final String ALTBEACON2 = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25";
    // public static final String EDDYSTONE_TLM =  "x,s:0-1=feaa,m:2-2=20,d:3-3,d:4-5,d:6-7,d:8-11,d:12-15";
    // public static final String EDDYSTONE_UID = "s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19";
    // public static final String EDDYSTONE_URL = "s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-20v";
    // public static final String IBEACON = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24";

    int counter = 0; //this is a test counter
    ArrayList<Double> RSSIContainer = new ArrayList<Double>(); //a vector of type double
    ArrayList<Double> copy = new ArrayList<Double>(); //copy of RSSIContainer used for data processing


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

    //GUI DECLARATIONS
    //ProgressBar loadRegion;
    //ImageView gray_out;
    //ImageView OrangeGlow;
    ImageView OrangeIndicator;
    ImageView[] NODES = new ImageView[7];

    TextView OutputProximity;
    //TextView MeanOutput;

    int AnimDur = 800;
    char numTrackedObj = 0;

    //Declare Zones       // THESE COMMENTS ARE TAKEN AS ABSOLUTE VALUE
    double[] ZONES = {-60.0,  // if less than, then tracked object is within 2 feet
            -68.0,  // if greater than zone 1 but less than this zone, then tracked object is within 5 feet
            -77.0,  // if greater than zone 2 but less than this zone, then tracked object is within 10 feet
            -87.0,  // if greater than zone 3 but less than this zone, then tracked object is within 20 feet
            -95.0   // if greater than zone 4 but less than this zone, then tracked object is within 30 feet
                };  // if greater than zone 4 then tracked object is 40+ feet

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_range_finder);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        //SET UP GUI AND NODES
        OutputProximity = (TextView) findViewById(R.id.range);
        NODES[0] = (ImageView) findViewById(R.id.node1);
        NODES[1] = (ImageView) findViewById(R.id.node2);
        NODES[2] = (ImageView) findViewById(R.id.node3);
        NODES[3] = (ImageView) findViewById(R.id.node4);
        NODES[4] = (ImageView) findViewById(R.id.node5);
        NODES[5] = (ImageView) findViewById(R.id.node6);
        NODES[6] = (ImageView) findViewById(R.id.node7);

        beaconManager2 = beaconManager2.getInstanceForApplication(this);
        beaconManager2.getBeaconParsers().add(new BeaconParser().setBeaconLayout(ALTBEACON2));
        beaconManager2.bind(this); //what does this do?

        //SET THE SCAN APP SCAN PERIOD
        beaconManager2.setForegroundScanPeriod(5000l);
        beaconManager2.setBackgroundBetweenScanPeriod(1100l);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_range_finder) {
            // Already in RangeFinderActivity
        } else if (id == R.id.nav_map) {
            // Launch MapActivity
            Intent map = new Intent(RangeFinderActivity.this, MapActivity.class);
            map.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(map);
        } else if (id == R.id.nav_device_discovery) {
            // Launch DeviceDiscoveryActivity
            Intent deviceDiscovery = new Intent(RangeFinderActivity.this, DeviceDiscoveryActivity.class);
            deviceDiscovery.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(deviceDiscovery);
        }
        beaconManager2.removeAllRangeNotifiers();

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onBeaconServiceConnect() {

        beaconManager2.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {

                if (beacons.size() > 0) {

                    numTrackedObj = (char) beacons.size(); //update the number of beacons we are tracking

                    // Log.i(TAG, "The first beacon I see is about " + beacons.iterator().next().getDistance() + " meters away.");
                    // Log.i(TAG,"The beacons RSSI is " + beacons.iterator().next().getRssi());
                    // Log.i(TAG,"The UUID of the beacon is " + beacons.iterator().next().getBluetoothAddress());

                    //If are receiving packets from the test beacon
                    if (beacons.iterator().next().getBluetoothAddress().equals(TESTBEACON))
                    {
                        //Fill our ArrayList with (8 - test points)
                        if (RSSIContainer.size() == BINSIZE)
                        {
                            //apply rolling window of 8 data points (know oldest data point off and add newest data point
                            RSSIContainer.add((double)beacons.iterator().next().getRssi()); //appends the latest data point to the end
                            RSSIContainer.remove(0); //removes the oldest data point
                            updateUi();
                        }
                        else //continue filling up our container till 8 data points
                        {
                            RSSIContainer.add((double)beacons.iterator().next().getRssi()); //appends the latest data point to the end

                            if (RSSIContainer.size() == BINSIZE)
                            {
                                //update proximity map for the first time
                                updateUi();
                            }
                        }
                    }
                }
                else //there are no beacons in the region
                {
                    //waiting for beacons to appear
                    numTrackedObj = 0;
                    updateUi();
                }
            }
        });

        try {
            beaconManager2.startRangingBeaconsInRegion(new Region("myMonitoringUniqueId", null, null, null));
        } catch (RemoteException e) {    }
    }

    private void updateUi() {
        runOnUiThread(new Runnable() {
            public void run() {

                //OrangeGlow = (ImageView) findViewById(R.id.OrangeGlow);
                OrangeIndicator = (ImageView) findViewById(R.id.OrangeIndicator);
                //loadRegion = (ProgressBar) findViewById(R.id.progressBar);
                //gray_out = (ImageView) findViewById(R.id.gray_out);

                //loadRegion.getIndeterminateDrawable().setColorFilter(Color.parseColor("#1377dc"), android.graphics.PorterDuff.Mode.SRC_ATOP);  //set color of progress bar

                final Animation glow = new AlphaAnimation(1.0f, 0.3f); //f is for floating point
                glow.setDuration(AnimDur); // every 0.8 seconds
                glow.setInterpolator(new LinearInterpolator());
                glow.setRepeatCount(Animation.INFINITE);
                glow.setRepeatMode(Animation.REVERSE);


                if (numTrackedObj == 0) {

                    //OrangeGlow.clearAnimation();


                    //output nothing
                    //OrangeGlow.setVisibility(View.INVISIBLE);
                    OrangeIndicator.setVisibility(View.INVISIBLE);

                    //Display progress bar indicating that we are searching for beacons
                    //loadRegion.setVisibility(View.VISIBLE);
                    //gray_out.setVisibility(View.VISIBLE);


                }
                else {
                    //Set up Beacon Tracker
                    //Create a class for all these attributes
                    //OrangeGlow.setVisibility(View.VISIBLE);
                    OrangeIndicator.setVisibility(View.VISIBLE);
                    //OrangeGlow.startAnimation(glow);

                    //clear progress bar
                    //loadRegion.setVisibility(View.GONE);
                    //gray_out.setVisibility(View.GONE);

                    //set orange glow to blinking if we haven't done it already
                    //SET SOME CONDITION HERE SIGNIFYING THAT THE ANIMATION IS ALREADY SET
                    //OrangeGlow.startAnimation(glow);

                    OrangeIndicator.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            moveIndicatorGlow(counter+1);
                        }
                    });

                    copy.clear();
                    copy.addAll(RSSIContainer); //Make a copy of our data so we don't lose the original
                    //perform processing
                    removeOutliers(copy);
                    KFilter(copy);
                    //smoothRSSI(copy); //Change to apply running average
                    removeOutliers(copy);
                    double NewPosition = getMean(copy);
                    //logic introduced to move our tracked obj to a proximity node
                    if (NewPosition >= ZONES[0]) //if we are really close!
                    {
                        moveIndicatorGlow(1);
                        OutputProximity.setText("2 feet");
                    }
                    else if (NewPosition >= ZONES[1] && NewPosition < ZONES[0])
                    {
                        moveIndicatorGlow(2);
                        OutputProximity.setText("5 feet");
                    }
                    else if (NewPosition >= ZONES[2] && NewPosition < ZONES[1])
                    {
                        moveIndicatorGlow(3);
                        OutputProximity.setText("10 feet");
                    }
                    else if (NewPosition >= ZONES[3] && NewPosition < ZONES[2])
                    {
                        moveIndicatorGlow(4);
                        OutputProximity.setText("20 feet");
                    }
                    else if (NewPosition >= ZONES[4] && NewPosition < ZONES[3])
                    {
                        moveIndicatorGlow(5);
                        OutputProximity.setText("30 feet");

                        //Sound text alarm
                        //ToneGenerator alarm = new ToneGenerator(AudioManager.STREAM_ALARM,100);
                        //alarm.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,200);

                    }
                    else if (NewPosition < ZONES[4])
                    {
                        moveIndicatorGlow(6);
                        OutputProximity.setText("40+ feet");
                    }
                }
            }
        });
    }

    public void moveIndicatorGlow(int node)
    {
        OrangeIndicator.animate()
                .x((NODES[node-1].getX()) - 15f)
                .y((NODES[node-1].getY()) - 15f)
                .setDuration(AnimDur)
                .start();
/*
        OrangeGlow.animate()
            .x((NODES[node-1].getX()) - 60f)
            .y((NODES[node-1].getY()) - 50f)
            .setDuration(AnimDur)
            .start();*/
    }

    double getMean(ArrayList<Double> v)
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