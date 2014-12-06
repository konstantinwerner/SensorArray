package in.konstant.sensors;

import java.util.ArrayList;

public abstract class SensorDevice {

    protected String mAddress;
    protected String mDeviceName;
    protected boolean mConnected = false;

    protected ArrayList<Sensor> mSensors;
    protected ArrayList<SensorDeviceEventListener> mStateListeners;

    public SensorDevice(final String address) {
        this.mAddress = address;
        mSensors = new ArrayList<Sensor>();
        mStateListeners = new ArrayList<SensorDeviceEventListener>();
    }

    public boolean registerStateListener(final SensorDeviceEventListener listener) {
        return mStateListeners.add(listener);
    }

    public boolean unregisterStateListener(final SensorDeviceEventListener listener) {
        return mStateListeners.remove(listener);
    }

    protected void notifySensorDeviceEvent(final int event) {
        for (SensorDeviceEventListener listener : mStateListeners) {
            listener.onSensorDeviceEvent(this, event);
        }
    }

    public abstract boolean initialize();
    public abstract void connect();
    public abstract void disconnect();
    public abstract boolean quit();

    public boolean isConnected() {
        return mConnected;
    }

    abstract String getBluetoothName();

    public String getBluetoothAddress() {
        return mAddress;
    }

    public String getDeviceName() {
        return mDeviceName;
    }

    public void setDeviceName(final String name) {
        this.mDeviceName = name;
    }

    public int getNumberOfSensors() {
        return mSensors.size();
    }

    public Sensor getSensor(final int id) {
        return mSensors.get(id);
    }

    public abstract float[] getMeasurementValue(final int sensorId, final int measurementId);
}
