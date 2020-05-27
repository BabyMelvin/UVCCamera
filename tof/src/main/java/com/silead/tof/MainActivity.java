package com.silead.tof;

import android.app.ActionBar;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import com.silead.tof.base.ActivityBase;
import com.silead.tof.device.USBMonitor;
import com.silead.tof.device.UVCCamera;
import com.silead.tof.util.SDLog;
import com.silead.tof.widget.ToFDialogParent;
import com.silead.tof.widget.ToFTextureView;

public class MainActivity extends ActivityBase implements View.OnClickListener, ToFDialogParent {
    private Button mPreviewButton;
    private Button mShotButton;
    private final Object mSync = new Object();
    private UVCCamera mUVCCamera;
    private ToFTextureView mTofTextureView;
    private static final String TAG = "MainActivity";
    private USBMonitor mUsbMonitor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_tof);
        ActionBar actionBar = getActionBar();
        if (actionBar != null)
            actionBar.hide();
        initData();
    }

    private void initData() {
        mTofTextureView = findViewById(R.id.tof_texture_view);
        mShotButton = findViewById(R.id.shot);
        mPreviewButton = findViewById(R.id.start_preview);
        mTofTextureView.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / UVCCamera.DEFAULT_PREVIEW_HEIGHT);
        mUsbMonitor = new USBMonitor(this, new ToFListener(mTofTextureView, this));
    }

    public void setUVCamera(UVCCamera camera) {
        mUVCCamera = camera;
    }

    @Override
    protected void onStart() {
        super.onStart();
        mUsbMonitor.register();
        synchronized (mSync) {
            if (mUVCCamera != null) {
                mUVCCamera.startPreview();
            }
        }
    }

    @Override
    protected void onStop() {
        synchronized (mSync) {
            if (mUVCCamera != null) {
                mUVCCamera.stopPreview();
            }

            if (mUsbMonitor != null) {
                mUsbMonitor.unregister();
            }
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        synchronized (mSync) {
            releaseCamera();
            if (mUsbMonitor != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    mUsbMonitor.destroy();
                }
                mUsbMonitor = null;
            }
        }
        super.onDestroy();
    }

    public void releaseCamera() {
        synchronized (mSync) {
            if(mUVCCamera !=null) {
                mUVCCamera.setStatusCallback(null);
                mUVCCamera.setButtonCallback(null);
                mUVCCamera.close();
                mUVCCamera.destroy();
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.shot:
                SDLog.d(TAG, "onClick: shot");
                break;
            case R.id.start_preview:
                SDLog.d(TAG, "onClick: start preview");
                break;
            default:
                SDLog.e(TAG, "onClick: no implement");
                break;
        }
    }

    @Override
    public USBMonitor getUSBMonitor() {
        return mUsbMonitor;
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                }
            });
        }
    }
}
