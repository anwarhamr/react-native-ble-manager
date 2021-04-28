package it.innove;

import java.util.ArrayList;
import java.util.List;
import java.nio.ByteBuffer;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
//import android.bluetooth.le.AdvertisingSet;
//import android.bluetooth.le.AdvertisingSetCallback;
//import android.bluetooth.le.AdvertisingSetParameters;
//import android.bluetooth.le.PeriodicAdvertisingParameters;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public class LollipopAdvertisingManager extends AdvertisingManager {
    private int sleepInterval = 10000;

    public LollipopAdvertisingManager(ReactApplicationContext reactContext, BleManager bleManager) {
        super(reactContext, bleManager);
    }

    @Override
    public void stopAdvertising(Callback callback) {
        advSessionId.incrementAndGet();
        getBluetoothAdapter().getBluetoothLeAdvertiser().stopAdvertising(mAdvertiseCallback);
        if (callback != null)
            callback.invoke();
        else {
            WritableMap map = Arguments.createMap();
            if (sleepInterval > 0)
                map.putInt("duration", sleepInterval);
            
            bleManager.sendEvent("BleManagerAdvertiseStopped", map);
        }
        sleepInterval = 0;
    }

    public static byte[] readableArrayToByteIntArray(ReadableArray readableArray) {
        List<Byte> bytes = new ArrayList<>(readableArray.size() * 5);
        for (int i = 0; i < readableArray.size(); i++) {
            bytes.add((byte)((readableArray.getInt(i) >> 0) & 0xff));
            // for (byte b :ByteBuffer.allocate(1).putInt(readableArray.getInt(i)).array()) {
            //     bytes.add(b);
            // }
        }

        byte[] bytesArr = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            bytesArr[i] = bytes.get(i);
        }

        return bytesArr;
    }
    //final int mode, final int tx, final int interval,
    @Override
    public void advertise(ReadableMap options, ReadableMap mfgData, ReadableArray serviceData, final Callback callback) {
        Log.d(BleManager.LOG_TAG, "Service: Starting Advertisement1");
        // Check if all features are supported
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        if (options.hasKey("includeDeviceName")) {
            dataBuilder.setIncludeDeviceName(options.getBoolean("includeDeviceName"));
        } 
        if (options.hasKey("duration")) {
             sleepInterval = options.getInt("duration");
        }
        if (mfgData != null) {
            if (mfgData.hasKey("manufacturerId") && mfgData.hasKey("manufacturerSpecificData")){
                ReadableArray arr = mfgData.getArray("manufacturerSpecificData");
                dataBuilder.addManufacturerData(mfgData.getInt("manufacturerId"), readableArrayToByteIntArray(arr));}
            else if (mfgData.hasKey("manufacturerId")){
                dataBuilder.addManufacturerData(mfgData.getInt("manufacturerId"),new byte[0]);
            }
            else
                Log.d(BleManager.LOG_TAG, "advertise: Not adding ManufacturerData; mfgData supplied but manufacturerId was not");
        } 
        if (serviceData != null && serviceData.size() > 0) {
            for (int i = 0; i < serviceData.size(); i++) {
                ReadableMap service = serviceData.getMap(i);
                if (service.hasKey("serviceUUID")) {
                    ParcelUuid id = new ParcelUuid(UUIDHelper.uuidFromString(service.getString("serviceUUID")));
                    dataBuilder.addServiceUuid(id);
                    if (service.hasKey("serviceData"))
                        dataBuilder.addServiceData(id, readableArrayToByteIntArray(service.getArray("serviceData")));

                    Log.d(bleManager.LOG_TAG, "advertising service: " + id.toString());
                }
            }
        }

        AdvertiseData data = dataBuilder.build();

        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();
        if (options.hasKey("advertiseMode"))
            settingsBuilder.setAdvertiseMode(options.getInt("advertiseMode"));
        if (options.hasKey("txPowerLevel"))
            settingsBuilder.setAdvertiseMode(options.getInt("txPowerLevel"));
        if (options.hasKey("isConnectable"))
            settingsBuilder.setConnectable(options.getBoolean("isConnectable"));
        AdvertiseSettings settings = settingsBuilder.build();

        getBluetoothAdapter().getBluetoothLeAdvertiser().startAdvertising(settings, data,
                mAdvertiseCallback);

        callback.invoke();
    }



    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartFailure(int errorCode) {
            Log.d(BleManager.LOG_TAG, "ServiceCB: onStartFailure" + errorCode);

            WritableMap map = Arguments.createMap();
            map.putInt("error", errorCode);
            bleManager.sendEvent("BleManagerAdvertiseStartFailed", map);
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.d(BleManager.LOG_TAG, "ServiceCB: onStartSuccess");
            WritableMap map = Arguments.createMap();
            if (sleepInterval > 0) {
                map.putInt("stopDuration", sleepInterval);
                Thread thread = new Thread() {

                    @Override
                    public void run() {

                        try {
                            Thread.sleep(sleepInterval);
                        } catch (InterruptedException ignored) {
                        }

                        stopAdvertising(null);
                    }

                };
                thread.start();
            }

            bleManager.sendEvent("BleManagerAdvertiseStarted", map);

        }
    };
//
//    private android.bluetooth.le.AdvertisingSetCallback mAdvertisingSetCallback = new AdvertisingSetCallback() {
//        @Override
//        public void onAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
//            //Callback triggered in response to
//            // AdvertisingSet#setAdvertisingData indicating result of the operation.
//            Log.d(BleManager.LOG_TAG, String.format("ServiceCB: onAdvertisingDataSet %d", status));
//
//        }
//
//        @Override
//        public void onAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable, int status) {
//            //Callback triggered in response to
//            // BluetoothLeAdvertiser#startAdvertisingSet indicating result of the operation.
//            Log.d(BleManager.LOG_TAG, String.format("ServiceCB: onAdvertisingEnabled enabled:%d status:%d", enable, status));
//        }
//
//        @Override
//        public void onAdvertisingParametersUpdated(AdvertisingSet advertisingSet, int txPower, int status) {
//            //Callback triggered in response to
//            // AdvertisingSet#setAdvertisingParameters indicating result of the operation.
//            Log.d(BleManager.LOG_TAG, "ServiceCB: onAdvertisingParametersUpdated");
//        }
//
//        @Override
//        public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
//            // Callback triggered in response to
//            // BluetoothLeAdvertiser#startAdvertisingSet indicating result of the operation.
//            Log.d(BleManager.LOG_TAG, String.format("ServiceCB: onAdvertisingSetStarted2 %d %d", txPower, status));
//            WritableMap map = Arguments.createMap();
//            bleManager.sendEvent("BleManagerAdvertiseStarted", map);
//            current = advertisingSet;
//            if (currentOption != 1) {
//                Thread thread = new Thread() {
//                    private int currentAdvSession = advSessionId.incrementAndGet();
//
//                    @Override
//                    public void run() {
//                        final AtomicInteger iterations = new AtomicInteger();
//
//                        BluetoothAdapter btAdapter = getBluetoothAdapter();
//                        // check current scan session was not stopped
//                        while (advSessionId.intValue() == currentAdvSession &&
//                                btAdapter.getState() == BluetoothAdapter.STATE_ON) {
//                            int c = iterations.incrementAndGet();
//                            Log.d(BleManager.LOG_TAG, String.format("ServiceCB: onAdvertisingSetStarted thread iteration:%d", c));
//
//                            try {
//                                Thread.sleep(1000);
//                            } catch (InterruptedException ignored) {
//                            }
//                            if (current != null)
//                                current.setAdvertisingData((new
//                                        AdvertiseData.Builder()).addServiceData(Service_UUID,
//                                        ("" + c).getBytes())
//                                        .setIncludeDeviceName(true)
//                                        .setIncludeTxPowerLevel(true).build());
//                        }
//                    }
//                };
//                thread.start();
//            } else if (currentOption == 1) {
//                Thread thread = new Thread() {
//                    private int currentAdvSession = advSessionId.incrementAndGet();
//
//                    @Override
//                    public void run() {
//                        final AtomicInteger iterations = new AtomicInteger();
//
//                        BluetoothAdapter btAdapter = getBluetoothAdapter();
//                        // check current scan session was not stopped
//                        try {
//                            Thread.sleep(4000);
//                        } catch (InterruptedException ignored) {
//                        }
////                        if (current != null)
////                            current.enableAdvertising(false, 0, 0);
//                        stopAdvertising(null);
//                    }
//
//                };
//                thread.start();
//            }
//        }
//
//        @Override
//        public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
//            //Callback triggered in response to
//            // BluetoothLeAdvertiser#stopAdvertisingSet indicating advertising set is stopped.
//            Log.d(BleManager.LOG_TAG, "ServiceCB: onAdvertisingSetStopped");
//            WritableMap map = Arguments.createMap();
//            bleManager.sendEvent("BleManagerAdvertiseStopped", map);
//
//        }
//
//        @Override
//        public void onPeriodicAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
//            //Callback triggered in response to
//            // AdvertisingSet#setPeriodicAdvertisingData indicating result of the operation.
//            Log.d(BleManager.LOG_TAG, "ServiceCB: onPeriodicAdvertisingDataSet");
//        }
//
//        @Override
//        public void onPeriodicAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable,
//                                                 int status) {
//            //Callback triggered in response to
//            // AdvertisingSet#setPeriodicAdvertisingEnabled indicating result of the operation.
//            Log.d(BleManager.LOG_TAG, "ServiceCB: onPeriodicAdvertisingEnabled");
//        }
//
//        @Override
//        public void onPeriodicAdvertisingParametersUpdated(AdvertisingSet advertisingSet,
//                                                           int status) {
//            //Callback triggered in response to
//            // AdvertisingSet#setPeriodicAdvertisingParameters indicating result of the operation.
//            Log.d(BleManager.LOG_TAG, "ServiceCB: onPeriodicAdvertisingParametersUpdated");
//        }
//
//        @Override
//        public void onScanResponseDataSet(AdvertisingSet advertisingSet, int status) {
//            //Callback triggered in response to
//            // AdvertisingSet#setAdvertisingData indicating result of the operation.
//            Log.d(BleManager.LOG_TAG, "ServiceCB: onScanResponseDataSet");
//        }
//    };
}
