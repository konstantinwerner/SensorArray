package in.konstant.sensorarray;

import android.app.Activity;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v4.widget.DrawerLayout;
import android.widget.Toast;

import in.konstant.BT.BTControl;
import in.konstant.BT.BTDeviceList;
import in.konstant.R;
import in.konstant.sensors.SensorArray;
import in.konstant.sensors.SensorArrayEventListener;
import in.konstant.sensors.SensorDevice;
import in.konstant.sensors.SensorEvent;

public class MainActivity extends Activity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks,
                   SensorDeviceListDialog.SensorDeviceListDialogListener,
                   SensorArrayEventListener
                   {

    MenuItem miBluetooth;
    SensorArray sensorArray;

    private NavigationDrawerFragment mNavigationDrawerFragment;
    private Fragment lastFragment;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("MainActivity", "onCreate");

        sensorArray = SensorArray.getInstance();
        sensorArray.load(getApplicationContext());

        sensorArray.registerStateListener(this);

        setContentView(R.layout.activity_main);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));

        if (!BTControl.available()) {
            Toast.makeText(this, R.string.toast_no_bt, Toast.LENGTH_LONG).show();
        } else {
            BTControl.registerStateChangeReceiver(this, BTStateChangeReceiver);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorArray.unregisterStateListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorArray.registerStateListener(this);
    }

    @Override
    protected void onDestroy() {
        sensorArray.save();
        sensorArray.unregisterStateListener(this);
        BTControl.unregisterStateChangeReceiver(this, BTStateChangeReceiver);
        super.onDestroy();
    }

    @Override
    public void onNavigationDrawerItemSelected(final int group, final int child) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag("sensor_" + group + "_" + child);

        // Falls das Fragment noch nicht hinzugefuegt wurde -> hinzufuegen
        if (fragment == null) {
            fragment = SensorFragment.getInstance(group, child);
            fragmentManager.beginTransaction()
                    .add(R.id.container, fragment, "sensor_" + group + "_" + child)
                    .commit();
        }

        // Falls schon ein Fragment angezeigt wird -> dieses ausblenden
        if (lastFragment != null) {
            fragmentManager.beginTransaction().hide(lastFragment).commit();
            Log.d("SensorFragment", "hiding lastFragment " + lastFragment.getId());
        }

        // Das neue Fragment anzeigen
        fragmentManager.beginTransaction().show(fragment).commit();
        lastFragment = fragment;
        Log.d("SensorFragment", "setting lastFragment to " + lastFragment.getId());
    }

    public void onFragmentCreated(final String title) {

    }

    public void restoreActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            getMenuInflater().inflate(R.menu.main, menu);

            miBluetooth = menu.findItem(R.id.action_bluetooth);
            miBluetooth.setEnabled(BTControl.available());
            setBTIcon(BTControl.enabled());

            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_bluetooth:
                if (BTControl.enabled()) {
                    BTControl.disable();
                } else {
                    BTControl.enable(this);
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private final BroadcastReceiver BTStateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {

            if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        setBTIcon(true);
                        break;

                    case BluetoothAdapter.STATE_OFF:
                        setBTIcon(false);
                        break;
                }
            }
        }
    };

    private void setBTIcon(final boolean state) {
        if (state) {
            miBluetooth.setIcon(R.drawable.ai_bluetooth_connected);
        } else {
            miBluetooth.setIcon(R.drawable.ai_bluetooth);
        }
    }

    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == BTDeviceList.REQ_DEVICE_LIST && resultCode == Activity.RESULT_OK) {
            String address = data.getExtras().getString(BTDeviceList.EXTRA_DEVICE_ADDRESS);
            String name = data.getExtras().getString(BTDeviceList.EXTRA_DEVICE_NAME);

            if (address != null) {
                if (sensorArray.containsDevice(address)) {
                    Toast.makeText(this, R.string.toast_device_already_on_list, Toast.LENGTH_SHORT).show();
                } else {
                    sensorArray.addDevice(address, name);
                }
            }
        }
    }

    public void onSensorArrayEvent(final SensorDevice device, final int event) {
        switch (event) {
            case SensorEvent.ADDED:
                Toast.makeText(MainActivity.this,
                        getResources().getString(R.string.device_added, device.getDeviceName()),
                        Toast.LENGTH_SHORT).show();
                break;
            case SensorEvent.REMOVED:
                Toast.makeText(MainActivity.this,
                        getResources().getString(R.string.device_removed, device.getDeviceName()),
                        Toast.LENGTH_SHORT).show();
                break;
            case SensorEvent.CONNECTED:
                Toast.makeText(MainActivity.this,
                        getResources().getString(R.string.device_connected, device.getDeviceName()),
                        Toast.LENGTH_SHORT).show();
                break;
            case SensorEvent.INITIALIZED:
                Toast.makeText(MainActivity.this,
                        getResources().getString(R.string.device_initialized, device.getDeviceName()),
                        Toast.LENGTH_SHORT).show();
                break;
            case SensorEvent.CONNECTING:
                Toast.makeText(MainActivity.this,
                        getResources().getString(R.string.device_connecting, device.getDeviceName()),
                        Toast.LENGTH_SHORT).show();
                break;
            case SensorEvent.DISCONNECTED:
                Toast.makeText(MainActivity.this,
                        getResources().getString(R.string.device_disconnected, device.getDeviceName()),
                        Toast.LENGTH_SHORT).show();
                break;
            case SensorEvent.CONNECTION_FAILED:
                Toast.makeText(MainActivity.this,
                        getResources().getString(R.string.device_connection_failed, device.getDeviceName()),
                        Toast.LENGTH_SHORT).show();
                break;
            case SensorEvent.CONNECTION_LOST:
                Toast.makeText(MainActivity.this,
                        getResources().getString(R.string.device_connection_lost, device.getDeviceName()),
                        Toast.LENGTH_SHORT).show();
                break;
        }
    }

    @Override
    public void onSensorDeviceListDialogConnect(final int id, final boolean connected) {

        if (connected) {
            sensorArray.getDevice(id).disconnect();
        } else {
            if (BTControl.enabled()) {
                sensorArray.getDevice(id).connect();
            }
        }
    }

    @Override
    public void onSensorDeviceListDialogSettings(final int id) {

    }

    @Override
    public void onSensorDeviceListDialogDelete(final int id) {

        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getResources().getString(
                        R.string.dialog_delete_message,
                        sensorArray.getDevice(id).getDeviceName()))
                .setPositiveButton("Yes", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sensorArray.getDevice(id).quit();
                        sensorArray.removeDevice(id);
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

}
