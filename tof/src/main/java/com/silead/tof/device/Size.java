package com.silead.tof.device;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Locale;


public class Size implements Parcelable {
    //
    /**
     * 本机端uvc_raw_format_t的值，这主要用于 libuvc
     * 9999 is still image
     */
    public int type;
    /**
     * 本机端raw_frame_t值，用于 androusb，
     * libuvc 不兼容
     */
    public int frame_type;
    public int index;
    public int width;
    public int height;
    public int frameIntervalType;
    public int frameIntervalIndex;
    public int[] intervals;
    // 下面是从 frameIntervalType 和 intervals 计算在 _updateFrameRate 中计算的
    public float[] fps;
    private String frameRates;

    /**
     * 构造函数
     *
     * @param _type       native raw_format_t的值，但 9999 是静止图像
     * @param _frame_type  本机端raw_frame_t的值
     * @param _index
     * @param _width
     * @param _height
     */
    public Size(final int _type, final int _frame_type, final int _index, final int _width, final int _height) {
        type = _type;
        frame_type = _frame_type;
        index = _index;
        width = _width;
        height = _height;
        frameIntervalType = -1;
        frameIntervalIndex = 0;
        intervals = null;
        updateFrameRate();
    }

    /**
     * 构造函数
     *
     * @param _type          本机端raw_format_t的值，但 9999 是静止图像
     * @param _frame_type    本机端raw_frame_t的值
     * @param _index
     * @param _width
     * @param _height
     * @param _min_intervals
     * @param _max_intervals
     */
    public Size(final int _type, final int _frame_type, final int _index, final int _width, final int _height, final int _min_intervals, final int _max_intervals, final int _step) {
        type = _type;
        frame_type = _frame_type;
        index = _index;
        width = _width;
        height = _height;
        frameIntervalType = 0;
        frameIntervalIndex = 0;
        intervals = new int[3];
        intervals[0] = _min_intervals;
        intervals[1] = _max_intervals;
        intervals[2] = _step;
        updateFrameRate();
    }

    /**
     * 构造函数
     *
     * @param _type       本机端raw_format_t的值，但 9999 是静止图像
     * @param _frame_type 本机端raw_frame_t的值
     * @param _index
     * @param _width
     * @param _height
     * @param _intervals
     */
    public Size(final int _type, final int _frame_type, final int _index, final int _width, final int _height, final int[] _intervals) {
        type = _type;
        frame_type = _frame_type;
        index = _index;
        width = _width;
        height = _height;
        final int n = _intervals != null ? _intervals.length : -1;
        if (n > 0) {
            frameIntervalType = n;
            intervals = new int[n];
            System.arraycopy(_intervals, 0, intervals, 0, n);
        } else {
            frameIntervalType = -1;
            intervals = null;
        }
        frameIntervalIndex = 0;
        updateFrameRate();
    }

    /**
     * 复制构造函数
     *
     * @param other
     */
    public Size(final Size other) {
        type = other.type;
        frame_type = other.frame_type;
        index = other.index;
        width = other.width;
        height = other.height;
        frameIntervalType = other.frameIntervalType;
        frameIntervalIndex = other.frameIntervalIndex;
        final int n = other.intervals != null ? other.intervals.length : -1;
        if (n > 0) {
            intervals = new int[n];
            System.arraycopy(other.intervals, 0, intervals, 0, n);
        } else {
            intervals = null;
        }
        updateFrameRate();
    }

    private Size(final Parcel source) {
        // 读取顺序必须与 writeToParcel 中的写入顺序相同。
        type = source.readInt();
        frame_type = source.readInt();
        index = source.readInt();
        width = source.readInt();
        height = source.readInt();
        frameIntervalType = source.readInt();
        frameIntervalIndex = source.readInt();
        if (frameIntervalType >= 0) {
            if (frameIntervalType > 0) {
                intervals = new int[frameIntervalType];
            } else {
                intervals = new int[3];
            }
            source.readIntArray(intervals);
        } else {
            intervals = null;
        }
        updateFrameRate();
    }

    public Size set(final Size other) {
        if (other != null) {
            type = other.type;
            frame_type = other.frame_type;
            index = other.index;
            width = other.width;
            height = other.height;
            frameIntervalType = other.frameIntervalType;
            frameIntervalIndex = other.frameIntervalIndex;
            final int n = other.intervals != null ? other.intervals.length : -1;
            if (n > 0) {
                intervals = new int[n];
                System.arraycopy(other.intervals, 0, intervals, 0, n);
            } else {
                intervals = null;
            }
            updateFrameRate();
        }
        return this;
    }

    public float getCurrentFrameRate() throws IllegalStateException {
        final int n = fps != null ? fps.length : 0;
        if ((frameIntervalIndex >= 0) && (frameIntervalIndex < n)) {
            return fps[frameIntervalIndex];
        }
        throw new IllegalStateException("unknown frame rate or not ready");
    }

    public void setCurrentFrameRate(final float frameRate) {
        // 选择最接近的
        int index = -1;
        final int n = fps != null ? fps.length : 0;
        for (int i = 0; i < n; i++) {
            if (fps[i] <= frameRate) {
                index = i;
                break;
            }
        }
        frameIntervalIndex = index;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeInt(type);
        dest.writeInt(frame_type);
        dest.writeInt(index);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeInt(frameIntervalType);
        dest.writeInt(frameIntervalIndex);
        if (intervals != null) {
            dest.writeIntArray(intervals);
        }
    }

    public void updateFrameRate() {
        final int n = frameIntervalType;
        if (n > 0) {
            fps = new float[n];
            for (int i = 0; i < n; i++) {
                final float _fps = fps[i] = 10000000.0f / intervals[i];
            }
        } else if (n == 0) {
            try {
                final int min = Math.min(intervals[0], intervals[1]);
                final int max = Math.max(intervals[0], intervals[1]);
                final int step = intervals[2];
                if (step > 0) {
                    int m = 0;
                    for (int i = min; i <= max; i += step) {
                        m++;
                    }
                    fps = new float[m];
                    m = 0;
                    for (int i = min; i <= max; i += step) {
                        final float _fps = fps[m++] = 10000000.0f / i;
                    }
                } else {
                    final float max_fps = 10000000.0f / min;
                    int m = 0;
                    for (float fps = 10000000.0f / min; fps <= max_fps; fps += 1.0f) {
                        m++;
                    }
                    fps = new float[m];
                    m = 0;
                    for (float fps = 10000000.0f / min; fps <= max_fps; fps += 1.0f) {
                        this.fps[m++] = fps;
                    }
                }
            } catch (final Exception e) {
                // ignore, 为什么min和max是0？
                fps = null;
            }
        }
        final int m = fps != null ? fps.length : 0;
        final StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < m; i++) {
            sb.append(String.format(Locale.US, "%4.1f", fps[i]));
            if (i < m - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        frameRates = sb.toString();
        if (frameIntervalIndex > m) {
            frameIntervalIndex = 0;
        }
    }

    @Override
    public String toString() {
        float frame_rate = 0.0f;
        try {
            frame_rate = getCurrentFrameRate();
        } catch (final Exception e) {
        }
        return String.format(Locale.US, "Size(%dx%d@%4.1f,type:%d,frame:%d,index:%d,%s)", width, height, frame_rate, type, frame_type, index, frameRates);
    }

    public static final Creator<Size> CREATOR = new Parcelable.Creator<Size>() {
        @Override
        public Size createFromParcel(final Parcel source) {
            return new Size(source);
        }

        @Override
        public Size[] newArray(final int size) {
            return new Size[size];
        }
    };
}
