package com.silead.tof.device;

import android.hardware.usb.UsbDevice;


public interface OnDeviceConnectListener {
    /**
     * called when device attached
     *
     * @param device
     */
    public void onAttach(UsbDevice device);

    /**
     * called when device detach(after onDisconnect)
     *
     * @param device
     */
    public void onDetach(UsbDevice device);

    /**
     * called after device opened
     *
     * @param device
     * @param ctrlBlock
     * @param createNew
     */
    public void onConnect(UsbDevice device, UsbControlBlock ctrlBlock, boolean createNew);

    /**
     * called when USB device removed or its power off (this callback is called after device closing)
     *
     * @param device
     * @param ctrlBlock
     */
    public void onDisconnect(UsbDevice device, UsbControlBlock ctrlBlock);

    /**
     * called when canceled or could not get permission from user
     *
     * @param device
     */
    public void onCancel(UsbDevice device);
}

