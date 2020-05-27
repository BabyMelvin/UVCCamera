package com.silead.tof.widget;

import com.silead.tof.device.USBMonitor;

public interface ToFDialogParent {
        public USBMonitor getUSBMonitor();
        public void onDialogResult(boolean canceled);
    }