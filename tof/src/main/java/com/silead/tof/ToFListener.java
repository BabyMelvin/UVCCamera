package com.silead.tof;

import android.app.Activity;
import android.content.Context;
import android.graphics.Camera;
import android.hardware.camera2.CameraDevice;
import android.hardware.usb.UsbDevice;
import android.widget.Toast;

import com.silead.tof.device.IButtonCallback;
import com.silead.tof.device.IStatusCallback;
import com.silead.tof.device.OnDeviceConnectListener;
import com.silead.tof.device.UVCCamera;
import com.silead.tof.device.UsbControlBlock;
import com.silead.tof.util.SDLog;
import com.silead.tof.widget.ToFDialog;
import com.silead.tof.widget.ToFDialogParent;
import com.silead.tof.widget.ToFTextureView;

import java.nio.ByteBuffer;

public class ToFListener implements OnDeviceConnectListener {
    private static final String TAG = "ToFListener";
    private ToFTextureView mTofTextureView;
    private Context mContext;

    public ToFListener(ToFTextureView textureView, Context context) {
        mContext = context;
        mTofTextureView = textureView;
    }

    @Override
    public void onAttach(UsbDevice device) {
        Toast.makeText(mContext, "USB DEVICE ATTACHED", Toast.LENGTH_SHORT).show();
        SDLog.d(TAG,"USB DEVICE ATTACHED");
        ToFDialog.showDialog((Activity) mContext);
    }

    @Override
    public void onDetach(UsbDevice device) {
        Toast.makeText(mContext, "USB DEVICE DETACHED", Toast.LENGTH_SHORT).show();
        SDLog.d(TAG,"USB DEVICE DETACHED");
    }

    @Override
    public void onConnect(UsbDevice device, UsbControlBlock ctrlBlock, boolean createNew) {
        ((MainActivity) mContext).releaseCamera();
        UVCCamera uvcCamera = new UVCCamera();
        uvcCamera.open(ctrlBlock);
        uvcCamera.setStatusCallback(mStatusCallback);
        uvcCamera.setButtonCallback(mButtonCallback);
    }

    @Override
    public void onDisconnect(UsbDevice device, UsbControlBlock ctrlBlock) {

    }

    @Override
    public void onCancel(UsbDevice device) {

    }

    private IStatusCallback mStatusCallback = new IStatusCallback() {
        @Override
        public void onStatus(final int statusClass, final int event, final int selector, final int statusAttribute, ByteBuffer data) {
            ((MainActivity) mContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final Toast toast = Toast.makeText(mContext, "onStatus(statusClass=" + statusClass
                            + "; " +
                            "event=" + event + "; " +
                            "selector=" + selector + "; " +
                            "statusAttribute=" + statusAttribute + "; " +
                            "data=...)", Toast.LENGTH_SHORT);
                    toast.show();

                    SDLog.d(TAG,"onStatus(statusClass=" + statusClass
                            + "; " +
                            "event=" + event + "; " +
                            "selector=" + selector + "; " +
                            "statusAttribute=" + statusAttribute + "; " +
                            "data=...)");
                }
            });
        }
    };

    private IButtonCallback mButtonCallback = new IButtonCallback() {
        @Override
        public void onButton(int button, int state) {

        }
    };
}
