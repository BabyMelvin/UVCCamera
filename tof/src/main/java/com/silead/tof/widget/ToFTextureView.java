package com.silead.tof.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

public class ToFTextureView extends TextureView {
    private static final String TAG = "ToFTextureView";
    private double mAspectRatio = -1.0;

    public ToFTextureView(Context context) {
        super(context);
    }

    public ToFTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ToFTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int paddingH = getPaddingLeft() + getPaddingRight();
        int paddingV = getPaddingBottom() + getPaddingTop();

        width -= paddingH;
        height -= paddingV;

        if (mAspectRatio > 0) {
            double viewAspect = width / height;
            double aspectDiff = mAspectRatio / viewAspect - 1;
            if (Math.abs(aspectDiff) > 0.01) {
                if (aspectDiff > 0) {
                    // width priority
                    height = (int) (width / mAspectRatio);
                } else {
                    // height priority
                    width = (int) (height / mAspectRatio);
                }
            }

            width += paddingH;
            height += paddingV;
            Log.d(TAG, "current width:" + width + ",current height = " + height);
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setAspectRatio(final double ratio) {
        if (ratio < 0)
            throw new IllegalArgumentException();

        if (mAspectRatio != ratio) {
            mAspectRatio = ratio;
            requestLayout();
        }
    }
}
