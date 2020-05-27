package com.silead.tof.device;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.silead.tof.util.HandlerThreadHandler;
import com.silead.tof.util.SDLog;
import com.silead.tof.device.UsbControlBlock;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class USBMonitor {

    private static final String TAG = "USBMonitor";

    private static final String ACTION_USB_PERMISSION_BASE = "com.silead.tof.USB_PERMISSION.";

    private final String ACTION_USB_PERMISSION = ACTION_USB_PERMISSION_BASE + hashCode();

    public static final String ACTION_USB_DEVICE_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";

    /**
     * 打开 UsbControlBlock
     */
    private final ConcurrentHashMap<UsbDevice, UsbControlBlock> mCtrlBlocks = new ConcurrentHashMap<UsbDevice, UsbControlBlock>();
    private final SparseArray<WeakReference<UsbDevice>> mHasPermissions = new SparseArray<WeakReference<UsbDevice>>();

    private final WeakReference<Context> mWeakContext;
    private final UsbManager mUsbManager;
    private final OnDeviceConnectListener mOnDeviceConnectListener;
    private PendingIntent mPermissionIntent = null;
    private List<DeviceFilter> mDeviceFilters = new ArrayList<DeviceFilter>();

    /**
     * 在工作线程上调用回调的处理程序
     */
    private final Handler mAsyncHandler;
    private volatile boolean destroyed;

    public USBMonitor(final Context context, final OnDeviceConnectListener listener) {
        SDLog.v(TAG, "USBMonitor:Constructor");

        if (listener == null)
            throw new IllegalArgumentException("OnDeviceConnectListener should not null.");
        mWeakContext = new WeakReference<Context>(context);
        mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        mOnDeviceConnectListener = listener;
        mAsyncHandler = HandlerThreadHandler.createHandler(TAG);
        destroyed = false;
        SDLog.v(TAG, "USBMonitor:mUsbManager=" + mUsbManager);
    }

    public UsbManager getUsbManager() {
        return mUsbManager;
    }

    public OnDeviceConnectListener getOnDeviceConnectListener() {
        return mOnDeviceConnectListener;
    }

    public final ConcurrentHashMap<UsbDevice, UsbControlBlock> getCtrlBlocks() {
        return mCtrlBlocks;
    }

    /**
     * Release all related resources,
     * never reuse again
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void destroy() {
        SDLog.i(TAG, "destroy:");
        unregister();
        if (!destroyed) {
            destroyed = true;
            // 关闭所有正在监视的 USB 设备
            final Set<UsbDevice> keys = mCtrlBlocks.keySet();
            if (keys != null) {
                UsbControlBlock ctrlBlock;
                try {
                    for (final UsbDevice key : keys) {
                        ctrlBlock = mCtrlBlocks.remove(key);
                        if (ctrlBlock != null) {
                            ctrlBlock.close();
                        }
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "destroy:", e);
                }
            }
            mCtrlBlocks.clear();
            try {
                mAsyncHandler.getLooper().quit();
            } catch (final Exception e) {
                Log.e(TAG, "destroy:", e);
            }
        }
    }

    /**
     * register BroadcastReceiver to monitor USB events
     *
     * @throws IllegalStateException
     */
    public synchronized void register() throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        if (mPermissionIntent == null) {
            SDLog.i(TAG, "register:");
            final Context context = mWeakContext.get();
            if (context != null) {
                mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
                final IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                // ACTION_USB_DEVICE_ATTACHED never comes on some devices so it should not be added here
                filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
                context.registerReceiver(mUsbReceiver, filter);
            }
            // start connection check
            mDeviceCounts = 0;
            mAsyncHandler.postDelayed(mDeviceCheckRunnable, 1000);
        }
    }

    /**
     * unregister BroadcastReceiver
     *
     * @throws IllegalStateException
     */
    public synchronized void unregister() throws IllegalStateException {
        // 删除用于连接检查的 Runnable
        mDeviceCounts = 0;
        if (!destroyed) {
            mAsyncHandler.removeCallbacks(mDeviceCheckRunnable);
        }
        if (mPermissionIntent != null) {
            SDLog.i(TAG, "unregister:");
            final Context context = mWeakContext.get();
            try {
                if (context != null) {
                    context.unregisterReceiver(mUsbReceiver);
                }
            } catch (final Exception e) {
                Log.w(TAG, e);
            }
            mPermissionIntent = null;
        }
    }

    public synchronized boolean isRegistered() {
        return !destroyed && (mPermissionIntent != null);
    }

    /**
     * set device filter
     *
     * @param filter
     * @throws IllegalStateException
     */
    public void setDeviceFilter(final DeviceFilter filter) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        mDeviceFilters.clear();
        mDeviceFilters.add(filter);
    }

    /**
     * 添加设备筛选器
     *
     * @param filter
     * @throws IllegalStateException
     */
    public void addDeviceFilter(final DeviceFilter filter) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        mDeviceFilters.add(filter);
    }

    /**
     * 删除设备筛选器
     *
     * @param filter
     * @throws IllegalStateException
     */
    public void removeDeviceFilter(final DeviceFilter filter) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        mDeviceFilters.remove(filter);
    }

    /**
     * set device filters
     *
     * @param filters
     * @throws IllegalStateException
     */
    public void setDeviceFilter(final List<DeviceFilter> filters) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        mDeviceFilters.clear();
        mDeviceFilters.addAll(filters);
    }

    /**
     * add device filters
     *
     * @param filters
     * @throws IllegalStateException
     */
    public void addDeviceFilter(final List<DeviceFilter> filters) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        mDeviceFilters.addAll(filters);
    }

    /**
     * remove device filters
     *
     * @param filters
     */
    public void removeDeviceFilter(final List<DeviceFilter> filters) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        mDeviceFilters.removeAll(filters);
    }

    /**
     * return the number of connected USB devices that matched device filter
     *
     * @return
     * @throws IllegalStateException
     */
    public int getDeviceCount() throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        return getDeviceList().size();
    }

    /**
     * return device list, return empty list if no device matched
     *
     * @return
     * @throws IllegalStateException
     */
    public List<UsbDevice> getDeviceList() throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        return getDeviceList(mDeviceFilters);
    }

    /**
     * return device list, return empty list if no device matched
     *
     * @param filters
     * @return
     * @throws IllegalStateException
     */
    public List<UsbDevice> getDeviceList(final List<DeviceFilter> filters) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        final HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        final List<UsbDevice> result = new ArrayList<UsbDevice>();
        if (deviceList != null) {
            if ((filters == null) || filters.isEmpty()) {
                result.addAll(deviceList.values());
            } else {
                for (final UsbDevice device : deviceList.values()) {
                    for (final DeviceFilter filter : filters) {
                        if ((filter != null) && filter.matches(device)) {
                            // when filter matches
                            if (!filter.isExclude) {
                                result.add(device);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * return device list, return empty list if no device matched
     *
     * @param filter
     * @return
     * @throws IllegalStateException
     */
    public List<UsbDevice> getDeviceList(final DeviceFilter filter) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        final HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        final List<UsbDevice> result = new ArrayList<UsbDevice>();
        if (deviceList != null) {
            for (final UsbDevice device : deviceList.values()) {
                if ((filter == null) || (filter.matches(device) && !filter.isExclude)) {
                    result.add(device);
                }
            }
        }
        return result;
    }

    /**
     * get USB device list, without filter
     *
     * @return
     * @throws IllegalStateException
     */
    public Iterator<UsbDevice> getDevices() throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        Iterator<UsbDevice> iterator = null;
        final HashMap<String, UsbDevice> list = mUsbManager.getDeviceList();
        if (list != null)
            iterator = list.values().iterator();
        return iterator;
    }

    /**
     * output device list to LogCat
     */
    public final void dumpDevices() {
        final HashMap<String, UsbDevice> list = mUsbManager.getDeviceList();
        if (list != null) {
            final Set<String> keys = list.keySet();
            if (keys != null && keys.size() > 0) {
                final StringBuilder sb = new StringBuilder();
                for (final String key : keys) {
                    final UsbDevice device = list.get(key);
                    final int num_interface = device != null ? device.getInterfaceCount() : 0;
                    sb.setLength(0);
                    for (int i = 0; i < num_interface; i++) {
                        sb.append(String.format(Locale.US, "interface%d:%s", i, device.getInterface(i).toString()));
                    }
                    SDLog.i(TAG, "key=" + key + ":" + device + ":" + sb.toString());
                }
            } else {
                SDLog.i(TAG, "no device");
            }
        } else {
            SDLog.i(TAG, "no device");
        }
    }

    /**
     * return whether the specific Usb device has permission
     *
     * @param device
     * @return true: 指定的 UsbDevice 具有权限
     * @throws IllegalStateException
     */
    public final boolean hasPermission(final UsbDevice device) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        return updatePermission(device, device != null && mUsbManager.hasPermission(device));
    }

    /**
     * 更新更新内部保留的权限状态
     *
     * @param device
     * @param hasPermission
     * @return hasPermission
     */
    private boolean updatePermission(final UsbDevice device, final boolean hasPermission) {
        int deviceKey = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            deviceKey = getDeviceKey(device, true);
        }
        synchronized (mHasPermissions) {
            if (hasPermission) {
                if (mHasPermissions.get(deviceKey) == null) {
                    mHasPermissions.put(deviceKey, new WeakReference<UsbDevice>(device));
                }
            } else {
                mHasPermissions.remove(deviceKey);
            }
        }
        return hasPermission;
    }

    /**
     * request permission to access to USB device
     *
     * @param device
     * @return true if fail to request permission
     */
    public synchronized boolean requestPermission(final UsbDevice device) {
        SDLog.v(TAG, "requestPermission:device=" + device);
        boolean result = false;
        if (isRegistered()) {
            if (device != null) {
                if (mUsbManager.hasPermission(device)) {
                    // call onConnect if app already has permission
                    processConnect(device);
                } else {
                    try {
                        // 如果没有权限，请请求它
                        mUsbManager.requestPermission(device, mPermissionIntent);
                    } catch (final Exception e) {
                        // Android5.1.x在 GALAXY 系统中生成一个未知的异常，意思是 android.permision.sec.MDM_APP_MGMT
                        Log.w(TAG, e);
                        processCancel(device);
                        result = true;
                    }
                }
            } else {
                processCancel(device);
                result = true;
            }
        } else {
            processCancel(device);
            result = true;
        }
        return result;
    }

    /**
     * 打开指定的 UsbDevice
     *
     * @param device
     * @return
     * @throws SecurityException 如果没有权限，请抛出安全异常
     */
    public UsbControlBlock openDevice(final UsbDevice device) throws SecurityException {
        if (hasPermission(device)) {
            UsbControlBlock result = mCtrlBlocks.get(device);
            if (result == null) {
                result = new UsbControlBlock(USBMonitor.this, device);    // この中でopenDeviceする
                mCtrlBlocks.put(device, result);
            }
            return result;
        } else {
            throw new SecurityException("has no permission");
        }
    }

    /**
     * BroadcastReceiver for USB permission
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (destroyed) return;
            final String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                // when received the result of requesting USB permission
                synchronized (USBMonitor.this) {
                    final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // get permission, call onConnect
                            processConnect(device);
                        }
                    } else {
                        // failed to get permission
                        processCancel(device);
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                updatePermission(device, hasPermission(device));
                processAttach(device);
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                // when device removed
                final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    UsbControlBlock ctrlBlock = mCtrlBlocks.remove(device);
                    if (ctrlBlock != null) {
                        // cleanup
                        ctrlBlock.close();
                    }
                    mDeviceCounts = 0;
                    processDetach(device);
                }
            }
        }
    };

    /**
     * number of connected & detected devices
     */
    private volatile int mDeviceCounts = 0;
    /**
     * periodically check connected devices and if it changed, call onAttach
     */
    private final Runnable mDeviceCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;
            final List<UsbDevice> devices = getDeviceList();
            final int n = devices.size();
            final int hasPermissionCounts;
            final int m;
            synchronized (mHasPermissions) {
                hasPermissionCounts = mHasPermissions.size();
                mHasPermissions.clear();
                for (final UsbDevice device : devices) {
                    hasPermission(device);
                }
                m = mHasPermissions.size();
            }
            if ((n > mDeviceCounts) || (m > hasPermissionCounts)) {
                mDeviceCounts = n;
                if (mOnDeviceConnectListener != null) {
                    for (int i = 0; i < n; i++) {
                        final UsbDevice device = devices.get(i);
                        mAsyncHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mOnDeviceConnectListener.onAttach(device);
                            }
                        });
                    }
                }
            }

            // confirm every 2 seconds
            mAsyncHandler.postDelayed(this, 2000);
        }
    };

    /**
     * open specific USB device
     *
     * @param device
     */
    private final void processConnect(final UsbDevice device) {
        if (destroyed) return;
        updatePermission(device, true);
        mAsyncHandler.post(new Runnable() {
            @Override
            public void run() {
                SDLog.v(TAG, "processConnect:device=" + device);
                UsbControlBlock ctrlBlock;
                final boolean createNew;
                ctrlBlock = mCtrlBlocks.get(device);
                if (ctrlBlock == null) {
                    ctrlBlock = new UsbControlBlock(USBMonitor.this, device);
                    mCtrlBlocks.put(device, ctrlBlock);
                    createNew = true;
                } else {
                    createNew = false;
                }
                if (mOnDeviceConnectListener != null) {
                    mOnDeviceConnectListener.onConnect(device, ctrlBlock, createNew);
                }
            }
        });
    }

    private final void processCancel(final UsbDevice device) {
        if (destroyed) return;
        SDLog.v(TAG, "processCancel:");
        updatePermission(device, false);
        if (mOnDeviceConnectListener != null) {
            mAsyncHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOnDeviceConnectListener.onCancel(device);
                }
            });
        }
    }

    private final void processAttach(final UsbDevice device) {
        if (destroyed) return;
        SDLog.v(TAG, "processAttach:");
        if (mOnDeviceConnectListener != null) {
            mAsyncHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOnDeviceConnectListener.onAttach(device);
                }
            });
        }
    }

    private final void processDetach(final UsbDevice device) {
        if (destroyed) return;
        SDLog.v(TAG, "processDetach:");
        if (mOnDeviceConnectListener != null) {
            mAsyncHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOnDeviceConnectListener.onDetach(device);
                }
            });
        }
    }

    /**
     * 生成设备密钥名称以保存每个 USB 设备的设置。
     * 供应商 ID、产品 ID、设备类、设备子类、设备协议生成
     * 请注意，同一产品具有相同的密钥名称
     *
     * @param device null返回空字符串
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static final String getDeviceKeyName(final UsbDevice device) {
        return getDeviceKeyName(device, null, false);
    }

    /**
     * 生成设备密钥名称以保存每个 USB 设备的设置。
     * 请注意，useNewAPI:false 是同一产品的设备密钥
     *
     * @param device
     * @param useNewAPI
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static final String getDeviceKeyName(final UsbDevice device, final boolean useNewAPI) {
        return getDeviceKeyName(device, null, useNewAPI);
    }

    /**
     * 生成设备密钥名称以保存每个 USB 设备的设置。 使此设备名称成为 HashMap 的键
     * 仅在 UsbDevice 打开时启用
     * 供应商 ID、产品 ID、设备类、设备子类、设备协议生成
     * 生成设备密钥名称，包括串行，除非是 null 或空字符
     * 如果 useNewAPI_true 满足 API 级别，则使用制造名称、版本和配置计数
     *
     * @param device    如果为空，则返回空字符串
     * @param serial    UsbDeviceConnection#getSerial 传递在 中获取的序列号，在内部获取，如果 API= 21 为 null，则 useNewAPI_true
     * @param useNewAPI API>=21或者，使用仅在 API_23 中可用的方法（取决于设备是否有效，因为某些设备返回 null）
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static final String getDeviceKeyName(final UsbDevice device, final String serial, final boolean useNewAPI) {
        if (device == null) return "";
        final StringBuilder sb = new StringBuilder();
        sb.append(device.getVendorId());
        sb.append("#");    // API >= 12
        sb.append(device.getProductId());
        sb.append("#");    // API >= 12
        sb.append(device.getDeviceClass());
        sb.append("#");    // API >= 12
        sb.append(device.getDeviceSubclass());
        sb.append("#");    // API >= 12
        sb.append(device.getDeviceProtocol());                        // API >= 12
        if (!TextUtils.isEmpty(serial)) {
            sb.append("#");
            sb.append(serial);
        }
        sb.append("#");
        if (TextUtils.isEmpty(serial)) {
            sb.append(device.getSerialNumber());
            sb.append("#");    // API >= 21
        }
        sb.append(device.getManufacturerName());
        sb.append("#");    // API >= 21
        sb.append(device.getConfigurationCount());
        sb.append("#");    // API >= 21

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            sb.append(device.getVersion());
        }
        sb.append("#");    // API >= 23
        SDLog.v(TAG, "getDeviceKeyName:" + sb.toString());
        return sb.toString();
    }

    /**
     * 获取设备密钥作为整数
     * 获取使用 getDeviceKeyName 获取的字符串的 hasCode
     * 供应商 ID、产品 ID、设备类、设备子类、设备协议生成
     * 请注意，相同的设备密钥与同类产品相同
     *
     * @param device null然后返回 0
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static final int getDeviceKey(final UsbDevice device) {
        return device != null ? getDeviceKeyName(device, null, false).hashCode() : 0;
    }

    /**
     * 获取设备密钥作为整数
     * getDeviceKeyName获取在 中获取的字符串的 hasCode
     * useNewAPI=false与同类产品相同的设备密钥，请注意
     *
     * @param device
     * @param useNewAPI
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static final int getDeviceKey(final UsbDevice device, final boolean useNewAPI) {
        return device != null ? getDeviceKeyName(device, null, useNewAPI).hashCode() : 0;
    }

    /**
     * 获取设备密钥作为整数
     * 获取使用 getDeviceKeyName 获取的字符串的 hasCode
     * 请注意，串行是 null，useNewAPI_false 是同一类型的产品，因此设备密钥相同
     *
     * @param device    null然后返回 0
     * @param serial    UsbDeviceConnection#getSerial传递在 中获取的序列号，在内部获取，如果 API= 21 为 null，则 useNewAPI_true
     * @param useNewAPI API_21 或 API_23 也仅使用一种方法（但根据设备是否有效，因为某些设备返回 null）
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static final int getDeviceKey(final UsbDevice device, final String serial, final boolean useNewAPI) {
        return device != null ? getDeviceKeyName(device, serial, useNewAPI).hashCode() : 0;
    }

    private static final int USB_DIR_OUT = 0;
    private static final int USB_DIR_IN = 0x80;
    private static final int USB_TYPE_MASK = (0x03 << 5);
    private static final int USB_TYPE_STANDARD = (0x00 << 5);
    private static final int USB_TYPE_CLASS = (0x01 << 5);
    private static final int USB_TYPE_VENDOR = (0x02 << 5);
    private static final int USB_TYPE_RESERVED = (0x03 << 5);
    private static final int USB_RECIP_MASK = 0x1f;
    private static final int USB_RECIP_DEVICE = 0x00;
    private static final int USB_RECIP_INTERFACE = 0x01;
    private static final int USB_RECIP_ENDPOINT = 0x02;
    private static final int USB_RECIP_OTHER = 0x03;
    private static final int USB_RECIP_PORT = 0x04;
    private static final int USB_RECIP_RPIPE = 0x05;
    private static final int USB_REQ_GET_STATUS = 0x00;
    private static final int USB_REQ_CLEAR_FEATURE = 0x01;
    private static final int USB_REQ_SET_FEATURE = 0x03;
    private static final int USB_REQ_SET_ADDRESS = 0x05;
    private static final int USB_REQ_GET_DESCRIPTOR = 0x06;
    private static final int USB_REQ_SET_DESCRIPTOR = 0x07;
    private static final int USB_REQ_GET_CONFIGURATION = 0x08;
    private static final int USB_REQ_SET_CONFIGURATION = 0x09;
    private static final int USB_REQ_GET_INTERFACE = 0x0A;
    private static final int USB_REQ_SET_INTERFACE = 0x0B;
    private static final int USB_REQ_SYNCH_FRAME = 0x0C;
    private static final int USB_REQ_SET_SEL = 0x30;
    private static final int USB_REQ_SET_ISOCH_DELAY = 0x31;
    private static final int USB_REQ_SET_ENCRYPTION = 0x0D;
    private static final int USB_REQ_GET_ENCRYPTION = 0x0E;
    private static final int USB_REQ_RPIPE_ABORT = 0x0E;
    private static final int USB_REQ_SET_HANDSHAKE = 0x0F;
    private static final int USB_REQ_RPIPE_RESET = 0x0F;
    private static final int USB_REQ_GET_HANDSHAKE = 0x10;
    private static final int USB_REQ_SET_CONNECTION = 0x11;
    private static final int USB_REQ_SET_SECURITY_DATA = 0x12;
    private static final int USB_REQ_GET_SECURITY_DATA = 0x13;
    private static final int USB_REQ_SET_WUSB_DATA = 0x14;
    private static final int USB_REQ_LOOPBACK_DATA_WRITE = 0x15;
    private static final int USB_REQ_LOOPBACK_DATA_READ = 0x16;
    private static final int USB_REQ_SET_INTERFACE_DS = 0x17;

    private static final int USB_REQ_STANDARD_DEVICE_SET = (USB_DIR_OUT | USB_TYPE_STANDARD | USB_RECIP_DEVICE);        // 0x10
    private static final int USB_REQ_STANDARD_DEVICE_GET = (USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_DEVICE);            // 0x90
    private static final int USB_REQ_STANDARD_INTERFACE_SET = (USB_DIR_OUT | USB_TYPE_STANDARD | USB_RECIP_INTERFACE);    // 0x11
    private static final int USB_REQ_STANDARD_INTERFACE_GET = (USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_INTERFACE);    // 0x91
    private static final int USB_REQ_STANDARD_ENDPOINT_SET = (USB_DIR_OUT | USB_TYPE_STANDARD | USB_RECIP_ENDPOINT);    // 0x12
    private static final int USB_REQ_STANDARD_ENDPOINT_GET = (USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_ENDPOINT);        // 0x92

    private static final int USB_REQ_CS_DEVICE_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_DEVICE);                // 0x20
    private static final int USB_REQ_CS_DEVICE_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_DEVICE);                    // 0xa0
    private static final int USB_REQ_CS_INTERFACE_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_INTERFACE);            // 0x21
    private static final int USB_REQ_CS_INTERFACE_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_INTERFACE);            // 0xa1
    private static final int USB_REQ_CS_ENDPOINT_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_ENDPOINT);                // 0x22
    private static final int USB_REQ_CS_ENDPOINT_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_ENDPOINT);                // 0xa2

    private static final int USB_REQ_VENDER_DEVICE_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_DEVICE);                // 0x40
    private static final int USB_REQ_VENDER_DEVICE_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_DEVICE);                // 0xc0
    private static final int USB_REQ_VENDER_INTERFACE_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_INTERFACE);        // 0x41
    private static final int USB_REQ_VENDER_INTERFACE_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_INTERFACE);        // 0xc1
    private static final int USB_REQ_VENDER_ENDPOINT_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_ENDPOINT);            // 0x42
    private static final int USB_REQ_VENDER_ENDPOINT_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_ENDPOINT);            // 0xc2

    private static final int USB_DT_DEVICE = 0x01;
    private static final int USB_DT_CONFIG = 0x02;
    private static final int USB_DT_STRING = 0x03;
    private static final int USB_DT_INTERFACE = 0x04;
    private static final int USB_DT_ENDPOINT = 0x05;
    private static final int USB_DT_DEVICE_QUALIFIER = 0x06;
    private static final int USB_DT_OTHER_SPEED_CONFIG = 0x07;
    private static final int USB_DT_INTERFACE_POWER = 0x08;
    private static final int USB_DT_OTG = 0x09;
    private static final int USB_DT_DEBUG = 0x0a;
    private static final int USB_DT_INTERFACE_ASSOCIATION = 0x0b;
    private static final int USB_DT_SECURITY = 0x0c;
    private static final int USB_DT_KEY = 0x0d;
    private static final int USB_DT_ENCRYPTION_TYPE = 0x0e;
    private static final int USB_DT_BOS = 0x0f;
    private static final int USB_DT_DEVICE_CAPABILITY = 0x10;
    private static final int USB_DT_WIRELESS_ENDPOINT_COMP = 0x11;
    private static final int USB_DT_WIRE_ADAPTER = 0x21;
    private static final int USB_DT_RPIPE = 0x22;
    private static final int USB_DT_CS_RADIO_CONTROL = 0x23;
    private static final int USB_DT_PIPE_USAGE = 0x24;
    private static final int USB_DT_SS_ENDPOINT_COMP = 0x30;
    private static final int USB_DT_CS_DEVICE = (USB_TYPE_CLASS | USB_DT_DEVICE);
    private static final int USB_DT_CS_CONFIG = (USB_TYPE_CLASS | USB_DT_CONFIG);
    private static final int USB_DT_CS_STRING = (USB_TYPE_CLASS | USB_DT_STRING);
    private static final int USB_DT_CS_INTERFACE = (USB_TYPE_CLASS | USB_DT_INTERFACE);
    private static final int USB_DT_CS_ENDPOINT = (USB_TYPE_CLASS | USB_DT_ENDPOINT);
    private static final int USB_DT_DEVICE_SIZE = 18;

    /**
     * 从指定 ID 的字符串描述符中获取字符串。 如果不能获取 null
     *
     * @param connection
     * @param id
     * @param languageCount
     * @param languages
     * @return
     */
    private static String getString(final UsbDeviceConnection connection, final int id, final int languageCount, final byte[] languages) {
        final byte[] work = new byte[256];
        String result = null;
        for (int i = 1; i <= languageCount; i++) {
            int ret = connection.controlTransfer(
                    USB_REQ_STANDARD_DEVICE_GET, // USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_DEVICE
                    USB_REQ_GET_DESCRIPTOR,
                    (USB_DT_STRING << 8) | id, languages[i], work, 256, 0);
            if ((ret > 2) && (work[0] == ret) && (work[1] == USB_DT_STRING)) {
                // skip first two bytes(bLength & bDescriptorType), and copy the rest to the string
                try {
                    result = new String(work, 2, ret - 2, "UTF-16LE");
                    if (!"Љ".equals(result)) {    // 有时奇怪的垃圾会回来。
                        break;
                    } else {
                        result = null;
                    }
                } catch (final UnsupportedEncodingException e) {
                    // ignore
                }
            }
        }
        return result;
    }

    /**
     * 获取供应商名称、产品名称、版本和串行
     *
     * @param device
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public UsbDeviceInfo getDeviceInfo(final UsbDevice device) {
        return updateDeviceInfo(mUsbManager, device, null);
    }

    /**
     * 获取供应商名称、产品名称、版本和串行
     * #updateDeviceInfo(final UsbManager, final UsbDevice, final UsbDeviceInfo)帮助器方法
     *
     * @param context
     * @param device
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static UsbDeviceInfo getDeviceInfo(final Context context, final UsbDevice device) {
        return updateDeviceInfo((UsbManager) context.getSystemService(Context.USB_SERVICE), device, new UsbDeviceInfo());
    }

    /**
     * 获取供应商名称、产品名称、版本和串行
     *
     * @param manager
     * @param device
     * @param _info
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static UsbDeviceInfo updateDeviceInfo(final UsbManager manager, final UsbDevice device, final UsbDeviceInfo _info) {
        final UsbDeviceInfo info = _info != null ? _info : new UsbDeviceInfo();
        info.clear();

        if (device != null) {

            info.manufacturer = device.getManufacturerName();
            info.product = device.getProductName();
            info.serial = device.getSerialNumber();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                info.usb_version = device.getVersion();
            }

            if ((manager != null) && manager.hasPermission(device)) {
                final UsbDeviceConnection connection = manager.openDevice(device);
                final byte[] desc = connection.getRawDescriptors();

                if (TextUtils.isEmpty(info.usb_version)) {
                    info.usb_version = String.format("%x.%02x", ((int) desc[3] & 0xff), ((int) desc[2] & 0xff));
                }
                if (TextUtils.isEmpty(info.version)) {
                    info.version = String.format("%x.%02x", ((int) desc[13] & 0xff), ((int) desc[12] & 0xff));
                }
                if (TextUtils.isEmpty(info.serial)) {
                    info.serial = connection.getSerial();
                }

                final byte[] languages = new byte[256];
                int languageCount = 0;
                // controlTransfer(int requestType, int request, int value, int index, byte[] buffer, int length, int timeout)
                try {
                    int result = connection.controlTransfer(
                            USB_REQ_STANDARD_DEVICE_GET, // USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_DEVICE
                            USB_REQ_GET_DESCRIPTOR,
                            (USB_DT_STRING << 8) | 0, 0, languages, 256, 0);
                    if (result > 0) {
                        languageCount = (result - 2) / 2;
                    }
                    if (languageCount > 0) {
                        if (TextUtils.isEmpty(info.manufacturer)) {
                            info.manufacturer = getString(connection, desc[14], languageCount, languages);
                        }
                        if (TextUtils.isEmpty(info.product)) {
                            info.product = getString(connection, desc[15], languageCount, languages);
                        }
                        if (TextUtils.isEmpty(info.serial)) {
                            info.serial = getString(connection, desc[16], languageCount, languages);
                        }
                    }
                } finally {
                    connection.close();
                }
            }
            if (TextUtils.isEmpty(info.manufacturer)) {
                info.manufacturer = USBVendorId.vendorName(device.getVendorId());
            }
            if (TextUtils.isEmpty(info.manufacturer)) {
                info.manufacturer = String.format("%04x", device.getVendorId());
            }
            if (TextUtils.isEmpty(info.product)) {
                info.product = String.format("%04x", device.getProductId());
            }
        }
        return info;
    }
}
