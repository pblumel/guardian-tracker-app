package com.projectguardian.guardian;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

public class RangeFinderActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    String TAG = "RangeFinder";

    private int mInterval = 5000;   // 5000 ms
    private Handler mHandler;

    int counter = 0; //this is a test counter

    //GUI DECLARATIONS
    //ProgressBar loadRegion;
    //ImageView gray_out;
    //ImageView OrangeGlow;
    ImageView OrangeIndicator;
    ImageView[] NODES = new ImageView[7];

    TextView OutputProximity;
    //TextView MeanOutput;

    int AnimDur = 800;
    //char numTrackedObj = 0;

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

        mHandler = new Handler();
        start_UI_updater();
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
            startActivity(map);
        } else if (id == R.id.nav_device_discovery) {
            // Launch DeviceDiscoveryActivity
            Intent deviceDiscovery = new Intent(RangeFinderActivity.this, DeviceDiscoveryActivity.class);
            startActivity(deviceDiscovery);
        }

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
    protected void onDestroy() {
        super.onDestroy();
        stop_UI_updater();
    }

    Runnable mStatusChecker = new Runnable() {
        @Override
        public void run() {
            try {
                updateUi();
                Log.i(TAG, "----------" + "Updating UI");
            } finally {
                mHandler.postDelayed(mStatusChecker, mInterval);
            }
        }
    };

    void start_UI_updater() {
        mStatusChecker.run();
    }

    void stop_UI_updater() {
        mHandler.removeCallbacks(mStatusChecker);
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


                if (((BeaconInterface)getApplicationContext()).numTrackedObj == 0) {

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

                    double NewPosition = ((BeaconInterface)getApplicationContext()).getMean(((BeaconInterface)getApplicationContext()).RSSIContainer_copy);
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
                .x(OrangeIndicator.getLeft())
                .y(NODES[node-1].getTop()
                        - ((getResources().getDimension(R.dimen.range_indicator_diameter) - getResources().getDimension(R.dimen.node_diameter))/2)
                        * ((NODES[node-1].getBottom() - NODES[node-1].getTop())/getResources().getDimension(R.dimen.node_diameter)))
                .setDuration(AnimDur)
                .start();
    }
}