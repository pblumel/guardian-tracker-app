package com.projectguardian.guardian;

import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
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
import android.widget.SeekBar;
import android.widget.TextView;

public class RangeFinderActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private final String TAG = "RangeFinder";

    private final int mInterval = 1000;   // 1000 ms
    private Handler mHandler;

    private boolean boundary_exceeded = false;

    //GUI DECLARATIONS
    private ImageView[] NODES = new ImageView[6];
    private ImageView OrangeIndicator;
    private SeekBar proximitySeekbar;

    private ToneGenerator alarm;

    private TextView OutputProximity;

    private final int AnimDur = 800;

    //Declare Zones       // THESE COMMENTS ARE TAKEN AS ABSOLUTE VALUE
    private final double[] ZONES = {
                                    -57.0,  // if less than, then tracked object is within 2 feet
                                    -61.0,  // if less than, then tracked object is within 5 feet
                                    -67.0,  // if less than, then tracked object is within 10 feet
                                    -75.0,  // if less than, then tracked object is within 25 feet
                                    -79.0   // if less than, then tracked object is within 45 feet
                                    };  // if greater than zone 4 then tracked object is 45+ feet

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
        OrangeIndicator = (ImageView) findViewById(R.id.OrangeIndicator);
        proximitySeekbar = (SeekBar) findViewById(R.id.set_proximity);

        alarm = new ToneGenerator(AudioManager.STREAM_ALARM,20);

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

        if (id == R.id.nav_map) {
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
        start_UI_updater();
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
                //Log.i(TAG, "----------" + "Updating UI");
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
                final Animation glow = new AlphaAnimation(1.0f, 0.3f); //f is for floating point
                glow.setDuration(AnimDur); // every 0.8 seconds
                glow.setInterpolator(new LinearInterpolator());
                glow.setRepeatCount(Animation.INFINITE);
                glow.setRepeatMode(Animation.REVERSE);

                if (((BeaconInterface)getApplicationContext()).numTrackedObj != 0 && !((BeaconInterface)getApplicationContext()).RSSIContainer_copy.isEmpty()) {
                    double NewPosition = ((BeaconInterface)getApplicationContext()).getMean(((BeaconInterface)getApplicationContext()).RSSIContainer_copy);
                    //logic introduced to move our tracked obj to a proximity node
                    if (NewPosition >= ZONES[0]) //if we are really close!
                    {
                        moveIndicatorGlow(1);
                        OutputProximity.setText("2 feet" + "\n" + (int)NewPosition + " dBm");  // TODO use strings.xml
                        
                        boundary_exceeded = false;
                    }
                    else if (NewPosition >= ZONES[1] && NewPosition < ZONES[0])
                    {
                        moveIndicatorGlow(2);
                        OutputProximity.setText("5 feet" + "\n" + (int)NewPosition + " dBm");

                        boundary_exceeded = false;
                    }
                    else if (NewPosition >= ZONES[2] && NewPosition < ZONES[1])
                    {
                        moveIndicatorGlow(3);
                        OutputProximity.setText("10 feet" + "\n" + (int)NewPosition + " dBm");

                        boundary_exceeded = (proximitySeekbar.getProgress() == 0);
                    }
                    else if (NewPosition >= ZONES[3] && NewPosition < ZONES[2])
                    {
                        moveIndicatorGlow(4);
                        OutputProximity.setText("25 feet" + "\n" + (int)NewPosition + " dBm");

                        boundary_exceeded = (proximitySeekbar.getProgress() <= 1);
                    }
                    else if (NewPosition >= ZONES[4] && NewPosition < ZONES[3])
                    {
                        moveIndicatorGlow(5);
                        OutputProximity.setText("45 feet" + "\n" + (int)NewPosition + " dBm");

                        boundary_exceeded = (proximitySeekbar.getProgress() <= 2);
                    }
                    else if (NewPosition < ZONES[4])
                    {
                        moveIndicatorGlow(6);
                        OutputProximity.setText("45+ feet" + "\n" + (int)NewPosition + " dBm");

                        boundary_exceeded = (proximitySeekbar.getProgress() <= 3);
                    }
                    OrangeIndicator.setVisibility(View.VISIBLE);
                }

                if (boundary_exceeded)
                    alarm.startTone(ToneGenerator.TONE_PROP_BEEP,1000);
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