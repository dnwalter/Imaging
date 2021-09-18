package me.minetsh.imaging.core;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by felix on 2017/11/22 下午6:13.
 */

public class IMGPath {

    protected Path path;

    private int color = Color.RED;

    private float width = BASE_MOSAIC_WIDTH;

    private IMGMode mode = IMGMode.DOODLE;

    public static final float BASE_DOODLE_WIDTH = 30f;

    public static final float BASE_MOSAIC_WIDTH = 72f;
    
    // 缩放率
    private float mScaleRate = 1f; // 添加path的时候图片当前的缩放率

    public IMGPath() {
        this(new Path());
    }

    public IMGPath(Path path) {
        this(path, IMGMode.DOODLE);
    }

    public IMGPath(Path path, IMGMode mode) {
        this(path, mode, Color.RED);
    }

    public IMGPath(Path path, IMGMode mode, int color) {
        this(path, mode, color, BASE_MOSAIC_WIDTH);
    }

    public IMGPath(Path path, IMGMode mode, int color, float width) {
        this.path = path;
        this.mode = mode;
        this.color = color;
        this.width = width;
        if (mode == IMGMode.MOSAIC) {
            path.setFillType(Path.FillType.EVEN_ODD);
        }
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public IMGMode getMode() {
        return mode;
    }

    public void setMode(IMGMode mode) {
        this.mode = mode;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getWidth() {
        return width;
    }
    
    public void setScaleRate(float scaleRate) {
        mScaleRate = scaleRate;
    }

    public float getPaintWidth() {
        return mScaleRate * BASE_DOODLE_WIDTH;
    }

    // 用白矩阵扩充
    public RectF onDrawWhiteRect(RectF frame, Matrix matrix, float[] whiteOffset) {
        // 如果画的线超出图片，要用白色矩阵填充
        RectF bounds = new RectF();
        Path pathx = new Path(path);
        pathx.transform(matrix);
        pathx.computeBounds(bounds, true);

        float width = BASE_DOODLE_WIDTH * mScaleRate; // todo ousy 这个width不是同比例缩放，导致不同图片看到涂鸦和白底的小间距不一样
        RectF rect = new RectF(frame);
        if (bounds.left < frame.left) {
            whiteOffset[0] += (bounds.left - frame.left);
            rect.left = bounds.left;
        }
        if (bounds.right > frame.right) {
            whiteOffset[2] += (bounds.right - frame.right);
            rect.right = bounds.right;
        }
        if (bounds.top < frame.top) {
            whiteOffset[1] += (bounds.top - frame.top);
            rect.top = bounds.top;
        }
        if (bounds.bottom > frame.bottom) {
            whiteOffset[3] += (bounds.bottom - frame.bottom);
            rect.bottom = bounds.bottom;
        }

        return rect;
    }

    /**
     *
     * @param canvas
     * @param paint
     * @param isChecked
     */
    public void onDrawDoodle(Canvas canvas, Paint paint, Matrix matrix, float scale, boolean isChecked) {
        Path path = new Path(this.path);
        path.transform(matrix);

        if (mode == IMGMode.DOODLE) {
            if (isChecked) {
                // todo ousy UI的设计师2px，要根据dp转换
                float width = (BASE_DOODLE_WIDTH + 4f) * mScaleRate * scale;
                Paint paintShadow = new Paint(paint);
                paintShadow.setStrokeWidth(width);
                paintShadow.setColor(Color.WHITE);
                paintShadow.setAlpha(102);
                paintShadow.setPathEffect(new CornerPathEffect(width));
                canvas.drawPath(new Path(path), paintShadow);
            }
            // 画线
            paint.setColor(color);
            paint.setStrokeWidth(BASE_DOODLE_WIDTH * mScaleRate * scale);
            paint.setPathEffect(new CornerPathEffect(BASE_DOODLE_WIDTH * mScaleRate * scale));
            // rewind
            canvas.drawPath(path, paint);

        }
    }

    public void onDrawMosaic(Canvas canvas, Paint paint) {
        if (mode == IMGMode.MOSAIC) {
            paint.setStrokeWidth(width);
            canvas.drawPath(path, paint);
        }
    }

    public void transform(Matrix matrix) {
        path.transform(matrix);
    }
}
