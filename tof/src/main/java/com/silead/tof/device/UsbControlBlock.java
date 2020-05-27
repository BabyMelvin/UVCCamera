package com.silead.tof.device;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.SparseArray;

import com.silead.tof.util.SDLog;

import java.lang.ref.WeakReference;
import java.util.Locale;

/**
 * control class
 * never reuse the instance when it closed
 */
public final class UsbControlBlock implements Cloneable {

    private static final String TAG = "UsbControlBlock";
    private final WeakReference<USBMonitor> mWeakMonitor;
    private final WeakReference<UsbDevice> mWeakDevice;
    protected UsbDeviceConnection mConnection;
    protected UsbDeviceInfo mInfo;
    private final int mBusNum;
    private final int mDevNum;
    private final SparseArray<SparseArray<UsbInterface>> mInterfaces = new SparseArray<SparseArray<UsbInterface>>();

    /**
     * this class needs permission to access USB device before constructing
     *
     * @param monitor
     * @param device
     */
    public UsbControlBlock(final USBMonitor monitor, final UsbDevice device) {
        SDLog.i(TAG, "UsbControlBlock:constructor");
        mWeakMonitor = new WeakReference<USBMonitor>(monitor);
        mWeakDevice = new WeakReference<UsbDevice>(device);

        mConnection = monitor.getUsbManager().openDevice(device);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mInfo = monitor.updateDeviceInfo(monitor.getUsbManager(), device, null);
        }
        final String name = device.getDeviceName();
        final String[] v = !TextUtils.isEmpty(name) ? name.split("/") : null;
        int busnum = 0;
        int devnum = 0;
        if (v != null) {
            busnum = Integer.parseInt(v[v.length - 2]);
            devnum = Integer.parseInt(v[v.length - 1]);
        }
        mBusNum = busnum;
        mDevNum = devnum;

        if (mConnection != null) {
            final int desc = mConnection.getFileDescriptor();
            final byte[] rawDesc = mConnection.getRawDescriptors();
            SDLog.i(TAG, String.format(Locale.US, "name=%s,desc=%d,busnum=%d,devnum=%d,rawDesc=", name, desc, busnum, devnum) + rawDesc);
        } else {
            SDLog.e(TAG, "could not connect to device " + name);
        }

    }

    /**
     * copy constructor
     *
     * @param src
     * @throws IllegalStateException
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private UsbControlBlock(final UsbControlBlock src) throws IllegalStateException {
        final USBMonitor monitor = src.getUSBMonitor();
        final UsbDevice device = src.getDevice();
        if (device == null) {
            throw new IllegalStateException("device may already be removed");
        }
        mConnection = monitor.getUsbManager().openDevice(device);
        if (mConnection == null) {
            throw new IllegalStateException("device may already be removed or have no permission");
        }
        mInfo = monitor.updateDeviceInfo(monitor.getUsbManager(), device, null);
        mWeakMonitor = new WeakReference<USBMonitor>(monitor);
        mWeakDevice = new WeakReference<UsbDevice>(device);
        mBusNum = src.mBusNum;
        mDevNum = src.mDevNum;
        // FIXME USBMonitor.mCtrlBlocks添加（现在 HashMap，因此添加时将替换它，将列表悬挂到列表或 HashMap？
    }

    /**
     * duplicate by clone
     * need permission
     * USBMonitor never handle cloned UsbControlBlock, you should release it after using it.
     *
     * @return
     * @throws CloneNotSupportedException
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public UsbControlBlock clone() throws CloneNotSupportedException {
        final UsbControlBlock ctrlBlock;
        try {
            ctrlBlock = new UsbControlBlock(this);
        } catch (final IllegalStateException e) {
            throw new CloneNotSupportedException(e.getMessage());
        }
        return ctrlBlock;
    }

    public USBMonitor getUSBMonitor() {
        return mWeakMonitor.get();
    }

    public final UsbDevice getDevice() {
        return mWeakDevice.get();
    }

    /**
     * get device name
     *
     * @return
     */
    public String getDeviceName() {
        final UsbDevice device = mWeakDevice.get();
        return device != null ? device.getDeviceName() : "";
    }

    /**
     * get device id
     *
     * @return
     */
    public int getDeviceId() {
        final UsbDevice device = mWeakDevice.get();
        return device != null ? device.getDeviceId() : 0;
    }

    /**
     * get device key string
     *
     * @return same value if the devices has same vendor id, product id, device class, device subclass and device protocol
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public String getDeviceKeyName() {
        return USBMonitor.getDeviceKeyName(mWeakDevice.get());
    }

    /**
     * get device key string
     *
     * @param useNewAPI if true, try to use serial number
     * @return
     * @throws IllegalStateException
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public String getDeviceKeyName(final boolean useNewAPI) throws IllegalStateException {
        if (useNewAPI) checkConnection();

        return USBMonitor.getDeviceKeyName(mWeakDevice.get(), mInfo.serial, useNewAPI);

    }

    /**
     * get device key
     *
     * @return
     * @throws IllegalStateException
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public int getDeviceKey() throws IllegalStateException {
        checkConnection();
        return USBMonitor.getDeviceKey(mWeakDevice.get());
    }

    /**
     * get device key
     *
     * @param useNewAPI if true, try to use serial number
     * @return
     * @throws IllegalStateException
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public int getDeviceKey(final boolean useNewAPI) throws IllegalStateException {
        if (useNewAPI) checkConnection();
        return USBMonitor.getDeviceKey(mWeakDevice.get(), mInfo.serial, useNewAPI);
    }

    /**
     * get device key string
     * if device has serial number, use it
     *
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public String getDeviceKeyNameWithSerial() {
        return USBMonitor.getDeviceKeyName(mWeakDevice.get(), mInfo.serial, false);
    }

    /**
     * get device key
     * if device has serial number, use it
     *
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public int getDeviceKeyWithSerial() {
        return getDeviceKeyNameWithSerial().hashCode();
    }

    /**
     * get UsbDeviceConnection
     *
     * @return
     */
    public synchronized UsbDeviceConnection getConnection() {
        return mConnection;
    }

    /**
     * get file descriptor to access USB device
     *
     * @return
     * @throws IllegalStateException
     */
    public synchronized int getFileDescriptor() throws IllegalStateException {
        checkConnection();
        return mConnection.getFileDescriptor();
    }

    /**
     * get raw descriptor for the USB device
     *
     * @return
     * @throws IllegalStateException
     */
    public synchronized byte[] getRawDescriptors() throws IllegalStateException {
        checkConnection();
        return mConnection.getRawDescriptors();
    }

    /**
     * get vendor id
     *
     * @return
     */
    public int getVenderId() {
        final UsbDevice device = mWeakDevice.get();
        return device != null ? device.getVendorId() : 0;
    }

    /**
     * get product id
     *
     * @return
     */
    public int getProductId() {
        final UsbDevice device = mWeakDevice.get();
        return device != null ? device.getProductId() : 0;
    }

    /**
     * get version string of USB
     *
     * @return
     */
    public String getUsbVersion() {
        return mInfo.usb_version;
    }

    /**
     * get manufacture
     *
     * @return
     */
    public String getManufacture() {
        return mInfo.manufacturer;
    }

    /**
     * get product name
     *
     * @return
     */
    public String getProductName() {
        return mInfo.product;
    }

    /**
     * get version
     *
     * @return
     */
    public String getVersion() {
        return mInfo.version;
    }

    /**
     * get serial number
     *
     * @return
     */
    public String getSerial() {
        return mInfo.serial;
    }

    public int getBusNum() {
        return mBusNum;
    }

    public int getDevNum() {
        return mDevNum;
    }

    /**
     * get interface
     *
     * @param interface_id
     * @throws IllegalStateException
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public synchronized UsbInterface getInterface(final int interface_id) throws IllegalStateException {
        return getInterface(interface_id, 0);
    }

    /**
     * get interface
     *
     * @param interface_id
     * @param altsetting
     * @return
     * @throws IllegalStateException
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public synchronized UsbInterface getInterface(final int interface_id, final int altsetting) throws IllegalStateException {
        checkConnection();
        SparseArray<UsbInterface> intfs = mInterfaces.get(interface_id);
        if (intfs == null) {
            intfs = new SparseArray<UsbInterface>();
            mInterfaces.put(interface_id, intfs);
        }
        UsbInterface intf = intfs.get(altsetting);
        if (intf == null) {
            final UsbDevice device = mWeakDevice.get();
            final int n = device.getInterfaceCount();
            for (int i = 0; i < n; i++) {
                final UsbInterface temp = device.getInterface(i);
                if ((temp.getId() == interface_id) && (temp.getAlternateSetting() == altsetting)) {
                    intf = temp;
                    break;
                }
            }
            if (intf != null) {
                intfs.append(altsetting, intf);
            }
        }
        return intf;
    }

    /**
     * open specific interface
     *
     * @param intf
     */
    public synchronized void claimInterface(final UsbInterface intf) {
        claimInterface(intf, true);
    }

    public synchronized void claimInterface(final UsbInterface intf, final boolean force) {
        checkConnection();
        mConnection.claimInterface(intf, force);
    }

    /**
     * close interface
     *
     * @param intf
     * @throws IllegalStateException
     */
    public synchronized void releaseInterface(final UsbInterface intf) throws IllegalStateException {
        checkConnection();
        final SparseArray<UsbInterface> intfs = mInterfaces.get(intf.getId());
        if (intfs != null) {
            final int index = intfs.indexOfValue(intf);
            intfs.removeAt(index);
            if (intfs.size() == 0) {
                mInterfaces.remove(intf.getId());
            }
        }
        mConnection.releaseInterface(intf);
    }

    /**
     * Close device
     * This also close interfaces if they are opened in Java side
     */
    public synchronized void close() {
        SDLog.i(TAG, "UsbControlBlock#close:");

        if (mConnection != null) {
            final int n = mInterfaces.size();
            for (int i = 0; i < n; i++) {
                final SparseArray<UsbInterface> intfs = mInterfaces.valueAt(i);
                if (intfs != null) {
                    final int m = intfs.size();
                    for (int j = 0; j < m; j++) {
                        final UsbInterface intf = intfs.valueAt(j);
                        mConnection.releaseInterface(intf);
                    }
                    intfs.clear();
                }
            }
            mInterfaces.clear();
            mConnection.close();
            mConnection = null;
            final USBMonitor monitor = mWeakMonitor.get();
            if (monitor != null) {
                if (monitor.getOnDeviceConnectListener() != null) {
                    monitor.getOnDeviceConnectListener().onDisconnect(mWeakDevice.get(), UsbControlBlock.this);
                }
                monitor.getCtrlBlocks().remove(getDevice());
            }
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null) return false;
        if (o instanceof UsbControlBlock) {
            final UsbDevice device = ((UsbControlBlock) o).getDevice();
            return device == null ? mWeakDevice.get() == null
                    : device.equals(mWeakDevice.get());
        } else if (o instanceof UsbDevice) {
            return o.equals(mWeakDevice.get());
        }
        return super.equals(o);
    }

//		@Override
//		protected void finalize() throws Throwable {
///			close();
//			super.finalize();
//		}

    private synchronized void checkConnection() throws IllegalStateException {
        if (mConnection == null) {
            throw new IllegalStateException("already closed");
        }
    }
}
