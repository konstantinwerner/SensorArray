package in.konstant.sensors;

import android.util.Log;

public class ExternalSensor extends Sensor {

    private final int id;
    private final String name;
    private final String part;

    public ExternalSensor(final int id, final Type type, final String name, final String part) {
        super();

        this.id = id;
        this.type = type;
        this.name = name;
        this.part = part;
    }

    public String getName() {
        return this.name;
    }

    public String getPart() {
        return this.part;
    }

    public void activate() {

    }

    public void deactivate() {

    }

    public void addMeasurement(final Measurement measurement) {
        mMeasurements.add(measurement);
    }
}
