package com.projectguardian.guardian;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
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

    private NavigationView navigationView;

    private final int mInterval = 1000;   // 1000 ms
    private Handler mHandler;

    private boolean boundary_exceeded = false;

    //GUI DECLARATIONS
    private ImageView[] NODES = new ImageView[6];
    private ConstraintLayout RangeIndicator;
    private SeekBar proximitySeekbar;
    private ConstraintLayout alarmBoundaryHelp;

    private ToneGenerator alarm;

    private TextView OutputProximity;
    private TextView RangeTag;

    private final int AnimDur = 800;

    //Declare Zones       // THESE COMMENTS ARE TAKEN AS ABSOLUTE VALUE
    private final double[] ZONES = {
                                    -57.0,  // if less than, then tracked object is within 2 feet
                                    -64.0,  // if less than, then tracked object is within 5 feet
                                    -68.0,  // if less than, then tracked object is within 10 feet
                                    -76.0,  // if less than, then tracked object is within 25 feet
                                    -81.0   // if less than, then tracked object is within 45 feet
                                    };  // if greater than zone 4 then tracked object is 45+ feet

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_range_finder);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        //SET UP GUI AND NODES
        OutputProximity = findViewById(R.id.range);
        NODES[0] = findViewById(R.id.node1);
        NODES[1] = findViewById(R.id.node2);
        NODES[2] = findViewById(R.id.node3);
        NODES[3] = findViewById(R.id.node4);
        NODES[4] = findViewById(R.id.node5);
        NODES[5] = findViewById(R.id.node6);
        RangeIndicator = findViewById(R.id.range_indicator);
        RangeTag = findViewById(R.id.range_tag_text);

        //SET UP HELP SCREEN
        alarmBoundaryHelp = findViewById(R.id.alarm_boundary_help);
        showHelp(); // Show help screen if this is the first startup

        configureSeekBar();

        alarm = new ToneGenerator(AudioManager.STREAM_ALARM,20);

        mHandler = new Handler();
        start_UI_updater();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
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

        if (id == R.id.nav_device_discovery) {
            // Launch DeviceDiscoveryActivity
            Intent deviceDiscovery = new Intent(RangeFinderActivity.this, DeviceDiscoveryActivity.class);
            startActivity(deviceDiscovery);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
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
        navigationView.getMenu().getItem(0).setChecked(true);   // Highlight Rangefinder in nav drawer
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
                    double NewPosition = ((BeaconInterface)getApplicationContext()).RSSIContainer_copy.get(((BeaconInterface)getApplicationContext()).RSSIContainer_copy.size()-1);
                    //logic introduced to move our tracked obj to a proximity node
                    if (NewPosition >= ZONES[0]) //if we are really close!
                    {
                        moveIndicatorGlow(1);
                        OutputProximity.setText((int)NewPosition + " dBm");  // TODO use strings.xml
                        RangeTag.setText("2");
                        
                        boundary_exceeded = false;
                    }
                    else if (NewPosition >= ZONES[1] && NewPosition < ZONES[0])
                    {
                        moveIndicatorGlow(2);
                        OutputProximity.setText((int)NewPosition + " dBm");
                        RangeTag.setText("5");

                        boundary_exceeded = false;
                    }
                    else if (NewPosition >= ZONES[2] && NewPosition < ZONES[1])
                    {
                        moveIndicatorGlow(3);
                        OutputProximity.setText((int)NewPosition + " dBm");
                        RangeTag.setText("10");

                        boundary_exceeded = (proximitySeekbar.getProgress() == 0);
                    }
                    else if (NewPosition >= ZONES[3] && NewPosition < ZONES[2])
                    {
                        moveIndicatorGlow(4);
                        OutputProximity.setText((int)NewPosition + " dBm");
                        RangeTag.setText("25");

                        boundary_exceeded = (proximitySeekbar.getProgress() <= 1);
                    }
                    else if (NewPosition >= ZONES[4] && NewPosition < ZONES[3])
                    {
                        moveIndicatorGlow(5);
                        OutputProximity.setText((int)NewPosition + " dBm");
                        RangeTag.setText("45");

                        boundary_exceeded = (proximitySeekbar.getProgress() <= 2);
                    }
                    else if (NewPosition < ZONES[4])
                    {
                        moveIndicatorGlow(6);
                        OutputProximity.setText((int)NewPosition + " dBm");
                        RangeTag.setText("45+");

                        boundary_exceeded = (proximitySeekbar.getProgress() <= 3);
                    }
                    RangeIndicator.setVisibility(View.VISIBLE);
                }

                if (boundary_exceeded)
                    alarm.startTone(ToneGenerator.TONE_PROP_BEEP,1000);
            }
        });
    }

    public void moveIndicatorGlow(int node)
    {
        RangeIndicator.animate()
                .x(RangeIndicator.getLeft())
                .y(NODES[node-1].getTop()
                        - ((getResources().getDimension(R.dimen.range_indicator_diameter) - getResources().getDimension(R.dimen.node_diameter))/2)
                        * ((NODES[node-1].getBottom() - NODES[node-1].getTop())/getResources().getDimension(R.dimen.node_diameter)))
                .setDuration(AnimDur)
                .start();
    }

    private void configureSeekBar() {
        proximitySeekbar = findViewById(R.id.set_proximity);
        proximitySeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                alarmBoundaryHelp.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private void showHelp() {
        SharedPreferences settings = getSharedPreferences("PrefsFile", 0);

        if (!settings.contains("first_start")) {    // If first start, entry wont exist
            alarmBoundaryHelp.setVisibility(View.VISIBLE);
            settings.edit().putBoolean("first_start", false).commit();  // Create entry
        }
    }
}