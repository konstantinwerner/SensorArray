package in.konstant.sensors;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;

import in.konstant.BT.BTDevice;

public class SensorCommandHandler
            extends Thread {

    private final static String TAG = "SensorCommandHandler";
    private final static boolean DBG = true;

    private static final char CMD_START_CHAR = '{';
    private static final char CMD_DELIMITER  = '|';
    private static final char CMD_END_CHAR   = '}';

    private static final class CMD {
        public static final char GET_NO_SENSORS         = 'a';
        public static final char GET_SENSOR             = 'b';
        public static final char GET_NO_MEAS            = 'c';
        public static final char GET_SENSOR_MEAS        = 'd';
        public static final char GET_SENSOR_VALUE       = 'e';
        public static final char SET_SENSOR_RANGE       = 'f';
        public static final char SET_SENSOR_OFF         = 'g';
        public static final char SET_SENSOR_ON          = 'h';
    }

    private Handler inHandler;

    private String command;
    private StringBuilder reply;
    private ArrayList<String> arguments;

    private Boolean commandToSend;
    private Boolean replyReceived;

    private final Object commandMonitor;
    private final Object replyMonitor;

    private BTDevice btDevice;

    public SensorCommandHandler(final BTDevice btDevice) {
        setPriority(Thread.NORM_PRIORITY);

        this.btDevice = btDevice;

        inHandler = new Handler(Looper.getMainLooper(), handleInMessage);
        btDevice.setDataHandler(inHandler);

        commandToSend = false;
        replyReceived = false;

        commandMonitor = new Object();
        replyMonitor = new Object();

        reply = new StringBuilder();
        arguments = new ArrayList<String>();
    }

    public String[] sendCommand(final String command) {
        this.command = command;

        synchronized (commandMonitor) {
            commandToSend = true;
            commandMonitor.notify();
        }

        synchronized (replyMonitor) {
            try {
                while (!replyReceived) {
                    replyMonitor.wait();        //TODO: Timeout (or use Conditions)
                }
            } catch (InterruptedException e) {
                return null;
            }

            replyReceived = false;
        }

        return arguments.toArray(new String[arguments.size()]);
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            synchronized (commandMonitor) {
                try {
                    while (!commandToSend)
                        commandMonitor.wait();

                    btDevice.send(command.getBytes());

                    commandToSend = false;

                } catch (InterruptedException e) {

                }
            }
        }
    }

    private final Handler.Callback handleInMessage = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == BTDevice.BTDataEvent.DATA_RECEIVED) {

                byte[] data = (byte[]) msg.obj;

                // When new data has been received, pass it to the parser
                if (parse(data)) {
                    // When the Parser has found the Stop-Char
                    synchronized (replyMonitor) {
                        // Notify about completed reply reception
                        replyReceived = true;
                        replyMonitor.notify();
                    }
                }
            }
            return true;
        }
    };

    private boolean parse(byte[] data) {
        for (int b = 0; b < data.length && data[b] != 0; ++b) {
            switch (data[b]) {
                case CMD_START_CHAR:
                    arguments.clear();
                    reply.setLength(0);
                    break;

                case CMD_DELIMITER:
                    arguments.add(reply.toString());

                    Log.d(TAG, reply.toString());

                    reply.setLength(0);
                    break;

                default:
                    reply.append((char)data[b]);
                    break;

                case CMD_END_CHAR:
                    arguments.add(reply.toString());

                    Log.d(TAG, reply.toString());

                    return true;
            }
            data[b] = 0;
        }
        return false;
    }

//--------------------------------------------

    public int getNrOfSensors() {
        String cmd = buildCommand(CMD.GET_NO_SENSORS, '\0', '\0', '\0');
        String[] result = sendCommand(cmd);

        if (result[0].equals("" + CMD.GET_NO_SENSORS)) {
            return (int)(result[1].charAt(0)) - '0';
        } else
            return 0;
        //TODO: Throw Exception
    }

    public ExternalSensor getSensor(final int sensorId) {
        String cmd = buildCommand(CMD.GET_SENSOR, (char)(sensorId + '0'), '\0', '\0');
        String[] result = sendCommand(cmd);

        if (result[0].equals("" + CMD.GET_SENSOR)) {
            return new ExternalSensor(
                        (int)(result[1].charAt(0)) - '0',
                        Type.fromInteger((int)(result[2].charAt(0)) - '0'),
                        result[3],
                        result[4]);
        } else
            return null;
        //TODO: Throw Exception
    }

    public int getNrOfMeasurements(final int sensorId) {
        String cmd = buildCommand(CMD.GET_NO_MEAS, (char)(sensorId + '0'), '\0', '\0');
        String[] result = sendCommand(cmd);

        if (result[0].equals("" + CMD.GET_NO_MEAS)) {
            return (int)(result[2].charAt(0)) - '0';
        } else
            return 0;
        //TODO: Throw Exception
    }

    public Measurement getMeasurement(final int sensorId, final int measurementId) {
        String cmd = buildCommand(CMD.GET_SENSOR_MEAS,
                                  (char)(sensorId + '0'),
                                  (char)(measurementId + '0'),
                                  '\0');

        String[] result = sendCommand(cmd);

        if (result[0].equals("" + CMD.GET_SENSOR_MEAS)) {

            Unit unit = new Unit(
                    result[6],
                    result[7],
                    Prefix.fromInteger((int)(result[8].charAt(0)) - '0'),
                    Subunit.fromString(result[9]));

            ArrayList<Range> ranges = new ArrayList<Range>();

            for (int r = 0; r < Integer.parseInt(result[10]); ++r) {
                Range range = new Range(
                                  ASCII85.decodeToFloat(result[11 + 3*r]),
                                  ASCII85.decodeToFloat(result[12 + 3*r]),
                                  (int)(result[13 + 3*r].charAt(0)) - '0'
                );

                ranges.add(range);
            }

            return new Measurement(
                    result[3],
                    (int)(result[4].charAt(0)) - '0',
                    ASCII85.decodeToInt(result[5]),
                    ranges.toArray(new Range[ranges.size()]),
                    unit
                    );
        } else
            return null;
        //TODO: Throw Exception
    }

    public float[] getSensorValue(final int sensorId, final int measurementId) {
        String cmd = buildCommand(CMD.GET_SENSOR_VALUE,
                (char)(sensorId + '0'),
                (char)(measurementId + '0'),
                '\0');

        String[] result = sendCommand(cmd);

        if (result[0].equals("" + CMD.GET_SENSOR_VALUE)) {

            int range = Integer.parseInt(result[3]);
            int size = Integer.parseInt(result[4]);

            float[] value = new float[size];

            for (int v = 0; v < size; v++) {
                value[v] = ASCII85.decodeToFloat(result[5+v]);
            }

            return value;
        } else
            return null;
        //TODO: Throw Exception
    }

    public int setSensorRange(final int sensorId, final int measurementId, final int rangeId) {
        String cmd = buildCommand(CMD.SET_SENSOR_RANGE,
                (char)(sensorId + '0'),
                (char)(measurementId + '0'),
                (char)(rangeId + '0'));

        String[] result = sendCommand(cmd);

        if (result[0].equals("" + CMD.SET_SENSOR_RANGE)) {
            return Integer.parseInt(result[3]);
        } else
            return -1;
        //TODO: Throw Exception
    }

//--------------------------------------------

    private static String buildCommand(final char id, final char... args) {
        StringBuilder cmd = new StringBuilder();

        cmd.append(CMD_START_CHAR);
        cmd.append(id);

        for (char a:args) {
            cmd.append(CMD_DELIMITER);
            cmd.append(a);
        }

        cmd.append(CMD_END_CHAR);
        cmd.append('\r');

        return cmd.toString();
    }

}
