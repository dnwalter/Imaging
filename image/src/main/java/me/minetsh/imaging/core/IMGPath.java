package me.minetsh.imaging.core;

import android.graphics.Canvas;
import android.graphics.Color;
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

    public static final float BASE_DOODLE_WIDTH = 20f;

    public static final float BASE_MOSAIC_WIDTH = 72f;
    
    // 缩放率
    private float mScaleRate = 1f;

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

    // 用白矩阵扩充
    public void onDrawWhiteRect(Canvas canvas, RectF frame, Matrix matrix) {
        // 如果画的线超出图片，要用白色矩阵填充
        RectF bounds = new RectF();
        Path pathx = new Path(path);
        pathx.transform(matrix);
        pathx.computeBounds(bounds, false);

        float width = BASE_DOODLE_WIDTH * mScaleRate;
        List<Rect> rects = new ArrayList<>();
        if (bounds.left < frame.left) {
            Rect rectx = new Rect((int)Math.floor(bounds.left - width), (int)frame.top, (int)Math.ceil(frame.left), (int)frame.bottom);
            rects.add(rectx);
        }
        if (bounds.right > frame.right) {
            Rect rectx = new Rect((int)Math.floor(frame.right), (int)frame.top, (int)Math.ceil(bounds.right + width), (int)frame.bottom);
            rects.add(rectx);
        }
        if (bounds.top < frame.top) {
            Rect rectx = new Rect((int)frame.left, (int)Math.floor(bounds.top - width), (int)frame.right, (int) Math.ceil(frame.top));
            rects.add(rectx);
        }
        if (bounds.bottom > frame.bottom) {
            Rect rectx = new Rect((int)frame.left, (int)Math.floor(frame.bottom), (int)frame.right, (int)Math.ceil(bounds.bottom + width));
            rects.add(rectx);
        }

        Paint paintWhite = new Paint();
        paintWhite.setColor(Color.WHITE);
        paintWhite.setStyle(Paint.Style.FILL);
        for (Rect r : rects) {
            canvas.drawRect(r, paintWhite);
        }
    }

    public void onDrawDoodle(Canvas canvas, Paint paint) {
        if (mode == IMGMode.DOODLE) {
            // 画线
            paint.setColor(color);
            paint.setStrokeWidth(BASE_DOODLE_WIDTH * mScaleRate);
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
