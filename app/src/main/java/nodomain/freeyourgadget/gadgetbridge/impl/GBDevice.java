package nodomain.freeyourgadget.gadgetbridge.impl;

import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;

public class GBDevice implements Parcelable {
    public static final String ACTION_DEVICE_CHANGED
            = "nodomain.freeyourgadget.gadgetbridge.gbdevice.action.device_changed";
    public static final Creator<GBDevice> CREATOR = new Creator<GBDevice>() {
        @Override
        public GBDevice createFromParcel(Parcel source) {
            return new GBDevice(source);
        }

        @Override
        public GBDevice[] newArray(int size) {
            return new GBDevice[size];
        }
    };
    private static final Logger LOG = LoggerFactory.getLogger(GBDevice.class);
    public static final short RSSI_UNKNOWN = 0;
    public static final short BATTERY_UNKNOWN = -1;
    private static final short BATTERY_THRESHOLD_PERCENT = 10;
    public static final String EXTRA_DEVICE = "device";
    private final String mName;
    private final String mAddress;
    private final DeviceType mDeviceType;
    private String mFirmwareVersion = null;
    private String mHardwareVersion = null;
    private State mState = State.NOT_CONNECTED;
    private short mBatteryLevel = BATTERY_UNKNOWN;
    private short mBatteryThresholdPercent = BATTERY_THRESHOLD_PERCENT;
    private BatteryState mBatteryState;
    private short mRssi = RSSI_UNKNOWN;
    private String mBusyTask;

    public GBDevice(String address, String name, DeviceType deviceType) {
        mAddress = address;
        mName = name;
        mDeviceType = deviceType;
        validate();
    }

    private GBDevice(Parcel in) {
        mName = in.readString();
        mAddress = in.readString();
        mDeviceType = DeviceType.values()[in.readInt()];
        mFirmwareVersion = in.readString();
        mHardwareVersion = in.readString();
        mState = State.values()[in.readInt()];
        mBatteryLevel = (short) in.readInt();
        mBatteryThresholdPercent = (short) in.readInt();
        mBatteryState = (BatteryState) in.readSerializable();
        mRssi = (short) in.readInt();
        mBusyTask = in.readString();

        validate();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeString(mAddress);
        dest.writeInt(mDeviceType.ordinal());
        dest.writeString(mFirmwareVersion);
        dest.writeString(mHardwareVersion);
        dest.writeInt(mState.ordinal());
        dest.writeInt(mBatteryLevel);
        dest.writeInt(mBatteryThresholdPercent);
        dest.writeSerializable(mBatteryState);
        dest.writeInt(mRssi);
        dest.writeString(mBusyTask);
    }

    private void validate() {
        if (getAddress() == null) {
            throw new IllegalArgumentException("address must not be null");
        }
    }

    public String getName() {
        return mName;
    }

    public String getAddress() {
        return mAddress;
    }

    public String getFirmwareVersion() {
        return mFirmwareVersion;
    }

    public void setFirmwareVersion(String firmwareVersion) {
        mFirmwareVersion = firmwareVersion;
    }

    public String getHardwareVersion() {
        return mHardwareVersion;
    }

    public void setHardwareVersion(String hardwareVersion) {
        mHardwareVersion = hardwareVersion;
    }

    public boolean isConnected() {
        return mState.ordinal() >= State.CONNECTED.ordinal();
    }

    public boolean isInitializing() {
        return mState == State.INITIALIZING;
    }

    public boolean isInitialized() {
        return mState.ordinal() >= State.INITIALIZED.ordinal();
    }

    public boolean isConnecting() {
        return mState == State.CONNECTING;
    }

    public boolean isBusy() {
        return mBusyTask != null;
    }

    public String getBusyTask() {
        return mBusyTask;
    }

    /**
     * Marks the device as busy, performing a certain task. While busy, no other operations will
     * be performed on the device.
     * <p/>
     * Note that nested busy tasks are not supported, every single call to #setBusyTask()
     * or unsetBusy() has an effect.
     *
     * @param task a textual name of the task to be performed, possibly displayed to the user
     */
    public void setBusyTask(String task) {
        if (task == null) {
            throw new IllegalArgumentException("busy task must not be null");
        }
        if (mBusyTask != null) {
            LOG.warn("Attempt to mark device as busy with: " + task + ", but is already busy with: " + mBusyTask);
        }
        LOG.info("Mark device as busy: " + task);
        mBusyTask = task;
    }

    /**
     * Marks the device as not busy anymore.
     */
    public void unsetBusyTask() {
        if (mBusyTask == null) {
            LOG.error("Attempt to mark device as not busy anymore, but was not busy before.");
            return;
        }
        LOG.info("Mark device as NOT busy anymore: " + mBusyTask);
        mBusyTask = null;
    }

    public State getState() {
        return mState;
    }

    public void setState(State state) {
        mState = state;
        if (state.ordinal() <= State.CONNECTED.ordinal()) {
            unsetDynamicState();
        }
    }

    private void unsetDynamicState() {
        setBatteryLevel(BATTERY_UNKNOWN);
        setBatteryState(BatteryState.UNKNOWN);
        setFirmwareVersion(null);
        setRssi(RSSI_UNKNOWN);
        if (mBusyTask != null) {
            unsetBusyTask();
        }
    }

    public String getStateString() {
        switch (mState) {
            case NOT_CONNECTED:
                return GBApplication.getContext().getString(R.string.not_connected);
            case CONNECTING:
                return GBApplication.getContext().getString(R.string.connecting);
            case CONNECTED:
                return GBApplication.getContext().getString(R.string.connected);
            case INITIALIZING:
                return GBApplication.getContext().getString(R.string.initializing);
            case INITIALIZED:
                return GBApplication.getContext().getString(R.string.initialized);
        }
        return GBApplication.getContext().getString(R.string.unknown_state);
    }


    public String getInfoString() {
        if (mFirmwareVersion != null) {
            if (mHardwareVersion != null) {
                return GBApplication.getContext().getString(R.string.connectionstate_hw_fw, mHardwareVersion, mFirmwareVersion);
            }
            return GBApplication.getContext().getString(R.string.connectionstate_fw, mFirmwareVersion);
        } else {
            return "";
        }
    }

    public DeviceType getType() {
        return mDeviceType;
    }

    public void setRssi(short rssi) {
        if (rssi < 0) {
            LOG.warn("illegal rssi value " + rssi + ", setting to RSSI_UNKNOWN");
            mRssi = RSSI_UNKNOWN;
        } else {
            mRssi = rssi;
        }
    }

    /**
     * Returns the device specific signal strength value, or #RSSI_UNKNOWN
     */
    public short getRssi() {
        return mRssi;
    }

    // TODO: this doesn't really belong here
    public void sendDeviceUpdateIntent(Context context) {
        Intent deviceUpdateIntent = new Intent(ACTION_DEVICE_CHANGED);
        deviceUpdateIntent.putExtra(EXTRA_DEVICE, this);
        LocalBroadcastManager.getInstance(context).sendBroadcast(deviceUpdateIntent);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof GBDevice)) {
            return false;
        }
        if (((GBDevice) obj).getAddress().equals(this.mAddress)) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mAddress.hashCode() ^ 37;
    }

    /**
     * Ranges from 0-100 (percent), or -1 if unknown
     *
     * @return the battery level in range 0-100, or -1 if unknown
     */
    public short getBatteryLevel() {
        return mBatteryLevel;
    }

    public void setBatteryLevel(short batteryLevel) {
        if ((batteryLevel >= 0 && batteryLevel <= 100) || batteryLevel == BATTERY_UNKNOWN) {
            mBatteryLevel = batteryLevel;
        } else {
            LOG.error("Battery level musts be within range 0-100: " + batteryLevel);
        }
    }

    public BatteryState getBatteryState() {
        return mBatteryState;
    }

    public void setBatteryState(BatteryState mBatteryState) {
        this.mBatteryState = mBatteryState;
    }

    public short getBatteryThresholdPercent() {
        return mBatteryThresholdPercent;
    }

    public void setBatteryThresholdPercent(short batteryThresholdPercent) {
        this.mBatteryThresholdPercent = batteryThresholdPercent;
    }

    @Override
    public String toString() {
        return "Device " + getName() + ", " + getAddress() + ", " + getStateString();
    }

    public enum State {
        // Note: the order is important!
        NOT_CONNECTED,
        CONNECTING,
        CONNECTED,
        INITIALIZING,
        /**
         * Means that the device is connected AND all the necessary initialization steps
         * have been performed. At the very least, this means that basic information like
         * device name, firmware version, hardware revision (as applicable) is available
         * in the GBDevice.
         */
        INITIALIZED
    }
}
