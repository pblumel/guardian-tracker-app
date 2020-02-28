package com.projectguardian.guardian;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import com.google.android.material.navigation.NavigationView;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class DeviceDiscoveryActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, AdapterView.OnItemClickListener {

    String TAG = "DeviceDiscovery";

    //Bluetooth Request Codes
    private static final int PERMISSION_REQUEST_BLUETOOTH = 100;
    private static final int REQUEST_ENABLE_BT = 111;

    // Coarse location permission request code
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 101;

    NavigationView navigationView;  // Nav Drawer

    private int mInterval = 1000;   // 1000 ms UI refresh period
    private Handler mHandler;

    public ArrayAdapter<String> deviceNames;    // ArrayAdapter between ListView and device list
    public ListView deviceList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_discovery); // Specify XML layout to use
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);  // Specify nav drawer layout
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();     // Hide drawer by default

        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this); // Nav drawer item selection listener

        CheckBTPermissions();
        CheckLocationPermissions();

        deviceNames = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, ((BeaconInterface)getApplicationContext()).deviceList);
        deviceList = findViewById(R.id.deviceList);
        deviceList.setAdapter(deviceNames);
        deviceList.setOnItemClickListener(this);

        mHandler = new Handler();
        start_UI_updater();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stop_UI_updater();
    }

    @Override
    protected void onResume() {
        super.onResume();
        navigationView.getMenu().getItem(1).setChecked(true);   // Highlight Device Discovery in nav drawer
        start_UI_updater();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stop_UI_updater();
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

        if (id == R.id.nav_range_finder) {
            if (((BeaconInterface)getApplicationContext()).selectedBeacon.equals("0")) {
                final AlertDialog.Builder noDeviceSelected = new AlertDialog.Builder(this);
                noDeviceSelected.setTitle(R.string.noDeviceSelected_title);
                noDeviceSelected.setMessage(R.string.noDeviceSelected_message);
                noDeviceSelected.setPositiveButton(android.R.string.ok, null);
                noDeviceSelected.show();
            } else {
                // Launch RangeFinderActivity
                Intent rangefinder = new Intent(DeviceDiscoveryActivity.this, RangeFinderActivity.class);
                startActivity(rangefinder);
            }
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    // triggered by startActivityForResult()
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode)
        {
            case REQUEST_ENABLE_BT:
            {
                if (resultCode == RESULT_OK)
                {
                    Log.d(TAG, "-----------------bluetooth enabled");
                }
                else
                {
                    final AlertDialog.Builder bluetoothDisabled = new AlertDialog.Builder(this);
                    bluetoothDisabled.setTitle(R.string.bluetoothDisabled_title);
                    bluetoothDisabled.setMessage(R.string.bluetoothDisabled_message);
                    bluetoothDisabled.setPositiveButton(android.R.string.ok, null);
                    bluetoothDisabled.setOnDismissListener(new DialogInterface.OnDismissListener()
                    {
                        @Override
                        public void onDismiss(DialogInterface dialog)
                        {
                            // BlueTooth disabled, close app
                            Log.d(TAG, "-----------------bluetooth disabled");
                            finish();
                        }
                    });
                    bluetoothDisabled.show();
                }
                break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode)
        {
            case PERMISSION_REQUEST_BLUETOOTH:
            {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Log.d(TAG, "-------------------bluetooth permission granted");
                }
                else
                {
                    final AlertDialog.Builder bluetoothDenied = new AlertDialog.Builder(this);
                    bluetoothDenied.setTitle(R.string.bluetoothDenied_title);
                    bluetoothDenied.setMessage(R.string.bluetoothDenied_message);
                    bluetoothDenied.setPositiveButton(android.R.string.ok, null);
                    bluetoothDenied.setOnDismissListener(new DialogInterface.OnDismissListener()
                    {
                        @Override
                        public void onDismiss(DialogInterface dialog)
                        {
                            // BlueTooth permission denied, close app
                            Log.d(TAG, "-------------------bluetooth permission denied");
                            finish();
                        }
                    });
                    bluetoothDenied.show();
                }
                break;
            }
            case PERMISSION_REQUEST_COARSE_LOCATION:
            {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Log.d(TAG, "-----------------------coarse location permission granted");
                }
                else
                {
                    final AlertDialog.Builder locationDenied = new AlertDialog.Builder(this);
                    locationDenied.setTitle(R.string.locationDenied_title);
                    locationDenied.setMessage(R.string.locationDenied_message);
                    locationDenied.setPositiveButton(android.R.string.ok, null);
                    locationDenied.setOnDismissListener(new DialogInterface.OnDismissListener()
                    {
                        @Override
                        public void onDismiss(DialogInterface dialog)
                        {
                            // Location permission denied, close app
                            Log.d(TAG, "-----------------------coarse location permission denied");
                            finish();
                        }
                    });
                    locationDenied.show();
                }
                break;
            }
        }
    }

    public void CheckBluetoothCompatibility() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null)  // Device not supported
        {
            final AlertDialog.Builder bluetoothNA = new AlertDialog.Builder(this);
            bluetoothNA.setTitle(R.string.bluetoothNA_title);
            bluetoothNA.setMessage(R.string.bluetoothNA_message);
            bluetoothNA.setPositiveButton(android.R.string.ok, null);
            bluetoothNA.setOnDismissListener(new DialogInterface.OnDismissListener()
            {
                @Override
                public void onDismiss(DialogInterface dialog)
                {
                    // BlueTooth not supported, close app
                    Log.d(TAG, "----------------------device doesn't support bluetooth");
                    finish();
                }
            });
            bluetoothNA.show();
        }
        else    // Device is supported, ensure BT is enabled
        {
            Log.d(TAG, "----------------------device supports bluetooth");
            if (!mBluetoothAdapter.isEnabled())
            {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
            }
        }
    }

    public void CheckBTPermissions() {
        if (checkPermissions(Manifest.permission.BLUETOOTH) && checkPermissions(Manifest.permission.BLUETOOTH_ADMIN)) {
            //All Permissions Granted
            Log.i(TAG, "-------------------------User Granted BlueTooth Permissions");
            CheckBluetoothCompatibility();
        } else {
            //Some Permissions were Denied, Request Permissions from User
            Log.e(TAG, "-------------------------Asking for BlueTooth Permissions");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN
                    }, PERMISSION_REQUEST_BLUETOOTH);
        }
    }

    public boolean checkPermissions(String per) {
        boolean result = true;
        result = (checkSelfPermission(per) == PackageManager.PERMISSION_GRANTED);
        Log.i(TAG, "----------------------Checking RunTime Permissions: " + result);
        return result;
    }

    public void CheckLocationPermissions()
    {
        // If location permissions not granted
        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            final AlertDialog.Builder locationPrompt = new AlertDialog.Builder(this);
            locationPrompt.setTitle(R.string.locationPrompt_title);
            locationPrompt.setMessage(R.string.locationPrompt_message);
            locationPrompt.setPositiveButton(android.R.string.ok, null);
            locationPrompt.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                }
            });
            locationPrompt.show(); //show the dialog box
        }
        else    // Location granted
        {
            Log.d(TAG, "coarse location permission granted");
        }
    }

    public void onItemClick(AdapterView<?> l, View v, int position, long id) {
        Log.i(TAG, "Beacon " + ((BeaconInterface)getApplicationContext()).deviceList.get((int) id) + " selected");
        if (!(((BeaconInterface)getApplicationContext()).selectedBeacon.equals(((BeaconInterface)getApplicationContext()).deviceMAC.get((int) id))))
            ((BeaconInterface)getApplicationContext()).resetFilter();   // Reset Kalman filter if new device selected
        ((BeaconInterface)getApplicationContext()).selectedBeacon = ((BeaconInterface)getApplicationContext()).deviceMAC.get((int) id);
        // Launch RangeFinderActivity
        Intent rangefinder = new Intent(DeviceDiscoveryActivity.this, RangeFinderActivity.class);
        startActivity(rangefinder);
    }

    Runnable mStatusChecker = new Runnable() {
        @Override
        public void run() {
            try {
                // Update UI with list
                deviceNames.notifyDataSetChanged();
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
}
