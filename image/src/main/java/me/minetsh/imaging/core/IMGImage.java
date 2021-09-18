package me.minetsh.imaging.core;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import me.minetsh.imaging.core.clip.IMGClip;
import me.minetsh.imaging.core.clip.IMGClipWindow;
import me.minetsh.imaging.core.entity.IMGModel;
import me.minetsh.imaging.core.homing.IMGHoming;
import me.minetsh.imaging.core.interfaces.IIMGViewCallback;
import me.minetsh.imaging.core.sticker.IMGSticker;
import me.minetsh.imaging.core.util.IMGUtils;
import me.minetsh.imaging.view.IMGStickerView;

/**
 * Created by felix on 2017/11/21 下午10:03.
 */

public class IMGImage {

    private static final String TAG = "IMGImage";

    private Bitmap mImage, mMosaicImage;

    /**
     * 原图片边框
     */
    private RectF mOriginFrame = new RectF();
    // 包括自己填充白底的图片边框
    private RectF mFrame = new RectF();
    // 记录移动涂鸦前的边框，用于判断新移动的涂鸦是否移出边界
    private RectF mTempFrame = new RectF();

    /**
     * 裁剪图片边框（显示的图片区域）
     */
    private RectF mClipFrame = new RectF();

    private RectF mTempClipFrame = new RectF();

    /**
     * 裁剪模式前状态备份
     */
    private RectF mBackupClipFrame = new RectF();

    private float mBackupClipRotate = 0;

    private float mRotate = 0, mTargetRotate = 0;

    private boolean isRequestToBaseFitting = false;

    private boolean isAnimCanceled = false;

    /**
     * 裁剪模式时当前触摸锚点
     */
    private IMGClip.Anchor mAnchor;

    private boolean isSteady = true;

    private Path mShade = new Path();

    /**
     * 裁剪窗口
     */
    private IMGClipWindow mClipWin = new IMGClipWindow();

    private boolean isDrawClip = false;

    /**
     * 编辑模式
     */
    private IMGMode mMode = IMGMode.NONE;

    private boolean isFreezing = mMode == IMGMode.CLIP;

    /**
     * 可视区域，无Scroll 偏移区域
     */
    private RectF mWindow = new RectF();

    /**
     * 是否初始位置
     */
    private boolean isInitialHoming = false;

    /**
     * 当前选中贴片
     */
    private IMGSticker mForeSticker;

    /**
     * 所有的涂鸦包括画图和文本
     */
    private List<IMGModel> mGraffitis = new ArrayList<>();
    // 被点击选中的涂鸦的下标
    private int mCheckedDoodleIndex = -1;

    /**
     * 马赛克路径
     */
    private List<IMGPath> mMosaics = new ArrayList<>();

    private static final int MIN_SIZE = 500;

    private static final int MAX_SIZE = 10000;

    private Paint mPaint, mMosaicPaint, mShadePaint;

    private Matrix M = new Matrix();

    private static final Bitmap DEFAULT_IMAGE;

    private static final int COLOR_SHADE = 0xCC000000;

    // 初始的缩放比例
    private float mInitialScale = -1;

    static {
        DEFAULT_IMAGE = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
    }

    // 记录缩放前原图片的中点
    private boolean mIsSetCenterXY = true;
    private float mLastCenterX = 0;
    private float mLastCenterY = 0;

    // 是否开始裁剪
    private boolean mIsStartClip = false;

    // 是否要显示超出限制的Toast
    private boolean mIsNeedShowLimitToast = true;

    {
        mShade.setFillType(Path.FillType.WINDING);

        // Doodle&Mosaic 's paint
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(IMGPath.BASE_DOODLE_WIDTH);
        mPaint.setColor(Color.RED);
        mPaint.setPathEffect(new CornerPathEffect(IMGPath.BASE_DOODLE_WIDTH));
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    // 重置数据
    public void resetBitmap(Bitmap bitmap) {
        mIsStartClip = false;
        mFrame = new RectF();
        mTempFrame = new RectF();
        mOriginFrame = new RectF();
        mClipFrame = new RectF();
        mTempClipFrame = new RectF();
        mBackupClipFrame = new RectF();
        mBackupClipRotate = 0;
        mRotate = 0;
        mTargetRotate = 0;
        mClipWin = new IMGClipWindow();
        mForeSticker = null;
        mGraffitis = new ArrayList<>();
        mMosaics = new ArrayList<>();
        M = new Matrix();

        setBitmap(bitmap);
    }

    public void resetCenterXY() {
        mIsSetCenterXY = true;
    }

    public IMGImage() {
        mImage = DEFAULT_IMAGE;

        if (mMode == IMGMode.CLIP) {
            initShadePaint();
        }
    }

    private IIMGViewCallback mViewCallback;
    public void setViewCallback(IIMGViewCallback callback) {
        mViewCallback = callback;
    }

    public void setBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }

        this.mImage = bitmap;

        // 清空马赛克图层
        if (mMosaicImage != null) {
            mMosaicImage.recycle();
        }
        this.mMosaicImage = null;

        makeMosaicBitmap();

        onImageChanged();
    }

    public IMGMode getMode() {
        return mMode;
    }

    public void setMode(IMGMode mode) {

        if (this.mMode == mode) return;

        moveToBackground(mForeSticker);

        if (mode == IMGMode.CLIP) {
            setFreezing(true);
        }

        this.mMode = mode;

        if (mMode == IMGMode.CLIP) {

            // 初始化Shade 画刷
            initShadePaint();

            mClipFrame.set(mFrame);

            // 备份裁剪前Clip 区域
            mBackupClipRotate = getRotate();
            mBackupClipFrame.set(mClipFrame);

            float scale = 1 / getScale();
            M.setTranslate(-mFrame.left, -mFrame.top);
            M.postScale(scale, scale);
            M.mapRect(mBackupClipFrame);

            // 重置裁剪区域
            mClipWin.reset(mClipFrame, getTargetRotate());
        } else {

            if (mMode == IMGMode.MOSAIC) {
                makeMosaicBitmap();
            }

            mClipWin.setClipping(false);
        }
    }

    private void rotateStickers(float rotate) {
        M.setRotate(rotate, mFrame.centerX(), mFrame.centerY());
        for (IMGModel model : mGraffitis) {
            IMGSticker sticker = model.getSticker();
            if (sticker != null) {
                M.mapRect(sticker.getFrame());
                sticker.setRotation(sticker.getRotation() + rotate);
                sticker.setX(sticker.getFrame().centerX() - sticker.getPivotX());
                sticker.setY(sticker.getFrame().centerY() - sticker.getPivotY());
            }
        }
    }

    private void initShadePaint() {
        if (mShadePaint == null) {
            mShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mShadePaint.setColor(COLOR_SHADE);
            mShadePaint.setStyle(Paint.Style.FILL);
        }
    }

    public boolean isMosaicEmpty() {
        return mMosaics.isEmpty();
    }

    // 撤销涂鸦
    public void undoGraffiti() {
        if (!mGraffitis.isEmpty()) {
            mGraffitis.remove(mGraffitis.size() - 1);
        }
    }

    public void undoMosaic() {
        if (!mMosaics.isEmpty()) {
            mMosaics.remove(mMosaics.size() - 1);
        }
    }

    public RectF getClipFrame() {
        return mClipFrame;
    }

    /**
     * 裁剪区域旋转回原始角度后形成新的裁剪区域，旋转中心发生变化，
     * 因此需要将视图窗口平移到新的旋转中心位置。
     */
    public IMGHoming clip(float scrollX, float scrollY) {
        RectF frame = mClipWin.getOffsetFrame(scrollX, scrollY);

        M.setRotate(-getRotate(), mClipFrame.centerX(), mClipFrame.centerY());
        M.mapRect(mClipFrame, frame);

        return new IMGHoming(
                scrollX + (mClipFrame.centerX() - frame.centerX()),
                scrollY + (mClipFrame.centerY() - frame.centerY()),
                getScale(), getRotate()
        );
    }

    public void toBackupClip() {
        M.setScale(getScale(), getScale());
        M.postTranslate(mFrame.left, mFrame.top);
        M.mapRect(mClipFrame, mBackupClipFrame);
        setTargetRotate(mBackupClipRotate);
        isRequestToBaseFitting = true;
        mIsStartClip = false;
    }

    public void resetClip() {
        // TODO 就近旋转
        setTargetRotate(getRotate() - getRotate() % 360);
        mClipFrame.set(mFrame);
        mClipWin.reset(mClipFrame, getTargetRotate());
    }

    private void makeMosaicBitmap() {
        if (mMosaicImage != null || mImage == null) {
            return;
        }

        if (mMode == IMGMode.MOSAIC) {

            int w = Math.round(mImage.getWidth() / 64f);
            int h = Math.round(mImage.getHeight() / 64f);

            w = Math.max(w, 8);
            h = Math.max(h, 8);

            // 马赛克画刷
            if (mMosaicPaint == null) {
                mMosaicPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                mMosaicPaint.setFilterBitmap(false);
                mMosaicPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            }

            mMosaicImage = Bitmap.createScaledBitmap(mImage, w, h, false);
        }
    }

    private void onImageChanged() {
        isInitialHoming = false;
        onWindowChanged(mWindow.width(), mWindow.height());

        if (mMode == IMGMode.CLIP) {
            mClipWin.reset(mClipFrame, getTargetRotate());
        }
    }

    public RectF getFrame() {
        return mFrame;
    }

    public boolean onClipHoming() {
        return mClipWin.homing();
    }

    public IMGHoming getStartHoming(float scrollX, float scrollY) {
        return new IMGHoming(scrollX, scrollY, getScale(), getRotate());
    }

    public IMGHoming getEndHoming(float scrollX, float scrollY) {
        IMGHoming homing = new IMGHoming(scrollX, scrollY, getScale(), getTargetRotate());

        if (mMode == IMGMode.CLIP) {
            RectF frame = new RectF(mClipWin.getTargetFrame());
            frame.offset(scrollX, scrollY);
            if (mClipWin.isResetting()) {

                RectF clipFrame = new RectF();
                M.setRotate(getTargetRotate(), mClipFrame.centerX(), mClipFrame.centerY());
                M.mapRect(clipFrame, mClipFrame);

                homing.rConcat(IMGUtils.fill(frame, clipFrame));
            } else {
                RectF cFrame = new RectF();

                // cFrame要是一个暂时clipFrame
                if (mClipWin.isHoming()) {
//
//                    M.mapRect(cFrame, mClipFrame);

//                    mClipWin
                    // TODO 偏移中心

                    M.setRotate(getTargetRotate() - getRotate(), mClipFrame.centerX(), mClipFrame.centerY());
                    M.mapRect(cFrame, mClipWin.getOffsetFrame(scrollX, scrollY));

                    homing.rConcat(IMGUtils.fitHoming(frame, cFrame, mClipFrame.centerX(), mClipFrame.centerY()));


                } else {
                    M.setRotate(getTargetRotate(), mClipFrame.centerX(), mClipFrame.centerY());
                    M.mapRect(cFrame, mFrame);
                    homing.rConcat(IMGUtils.fillHoming(frame, cFrame, mClipFrame.centerX(), mClipFrame.centerY()));
                }

            }
        } else {
            RectF clipFrame = new RectF();
            M.setRotate(getTargetRotate(), mFrame.centerX(), mFrame.centerY());
            M.mapRect(clipFrame, mFrame);

            RectF win = new RectF(mWindow);
            win.offset(scrollX, scrollY);
            homing.rConcat(IMGUtils.fitHoming(win, clipFrame, isRequestToBaseFitting, mLastCenterX, mLastCenterY));
            isRequestToBaseFitting = false;
        }

        return homing;
    }

    public <S extends IMGSticker> void addSticker(S sticker) {
        if (sticker != null) {
            mGraffitis.add(new IMGModel(sticker));
            moveToForeground(sticker);
        }
    }

    public void resetCheckedDoodleIndex() {
        mCheckedDoodleIndex = -1;
    }

    // 是否超出了屏幕
    public boolean isOutSideWindow() {
        boolean result = false;
        if (mCheckedDoodleIndex >= 0 && mCheckedDoodleIndex < mGraffitis.size()) {
            IMGPath imgPath = mGraffitis.get(mCheckedDoodleIndex).getImgPath();
            if (imgPath != null) {
                float scale = getScale();
                Matrix matrix = new Matrix();
                matrix.preTranslate(mOriginFrame.left, mOriginFrame.top);
                matrix.preScale(scale, scale);

                Path path = new Path(imgPath.path);
                path.transform(matrix);
                RectF bounds = new RectF();
                path.computeBounds(bounds, true);

                boolean left = bounds.left <= Math.min(mWindow.left, mTempFrame.left);
                boolean top = bounds.top <= Math.min(mWindow.top, mTempFrame.top);
                boolean right = bounds.right >= Math.max(mWindow.right, mTempFrame.right);
                boolean bottom = bounds.bottom >= Math.max(mWindow.bottom, mTempFrame.bottom);
                result = left || top || right || bottom;
            }
        }

        return result;
    }

    // 判断文本是否超出了屏幕
    public boolean isOutSideWindowBySticker() {
        boolean result = false;
        if (mForeSticker != null) {
            RectF bounds = mForeSticker.getFrameNoIcon();

            boolean left = bounds.left <= Math.min(mWindow.left, mTempFrame.left);
            boolean top = bounds.top <= Math.min(mWindow.top, mTempFrame.top);
            boolean right = bounds.right >= Math.max(mWindow.right, mTempFrame.right);
            boolean bottom = bounds.bottom >= Math.max(mWindow.bottom, mTempFrame.bottom);
            result = left || top || right || bottom;
        }

        return result;
    }

    // 移动涂鸦
    public void moveDoodle(float dx, float dy) {
        if (mCheckedDoodleIndex >= 0 && mCheckedDoodleIndex < mGraffitis.size()) {
            IMGPath imgPath = mGraffitis.get(mCheckedDoodleIndex).getImgPath();
            if (imgPath != null) {
                float scale = getScale();
                Matrix matrix = new Matrix();
                matrix.preTranslate(mOriginFrame.left, mOriginFrame.top);
                matrix.preScale(scale, scale);

                matrix.postTranslate(dx, dy);

                Path path = new Path(imgPath.path);
                path.transform(matrix);
                if (isLimitExceeded(path)) return;

                matrix.postTranslate(-mOriginFrame.left, -mOriginFrame.top);
                matrix.postScale(1f / getScale(), 1f / getScale());

                imgPath.path.transform(matrix);
            }
        }
    }

    private Rect mRegion;
    public Rect getRegion() {
        return mRegion;
    }

    // 判断这个坐标是否在涂鸦上
    public boolean checkPoint(float x, float y) {
        boolean result = false;
        float scale = getScale();
        Matrix matrix = new Matrix();
        matrix.preTranslate(mOriginFrame.left, mOriginFrame.top);
        matrix.preScale(scale, scale);
        int i = mGraffitis.size() - 1;
        for(; i >= 0; i--) {
            IMGPath imgPath = mGraffitis.get(i).getImgPath();
            if (imgPath == null) continue;
            RectF bounds = new RectF();
            Path path = new Path(imgPath.getPath());
            path.transform(matrix);
            path.computeBounds(bounds, true);
            Region region = new Region();
            boolean isRegion = region.setPath(path, new Region((int)bounds.left, (int)bounds.top,(int)bounds.right, (int)bounds.bottom));
            float radius = imgPath.getPaintWidth() * scale * 0.5f;
            Region regionPoint = new Region((int)(x - radius), (int)(y - radius), (int)(x + radius), (int)(y + radius));
            mRegion = new Rect((int)(x - radius), (int)(y - radius), (int)(x + radius), (int)(y + radius));;
            // 是否生成了区域
            if (isRegion) {
                regionPoint.op(region, Region.Op.INTERSECT);
            }else {
                regionPoint.op(new Region((int)bounds.left, (int)bounds.top, (int)bounds.right, (int)bounds.bottom), Region.Op.INTERSECT);
            }

            if (!regionPoint.isEmpty()) {
                // 点击位置和涂鸦相交
                result = true;
                break;
            }

        }

        mCheckedDoodleIndex = i;
        refreshTempFrame();

        return result;
    }

    public void refreshTempFrame() {
        mTempFrame = new RectF(mFrame);
    }


    public void addPath(IMGPath path, float sx, float sy) {
        if (path == null) return;

        float scale = 1f / getScale();

        M.setTranslate(sx, sy);
//        if (mInitialScale == getScale() || path.getMode() != IMGMode.DOODLE) {
//        }
        M.postRotate(-getRotate(), mFrame.centerX(), mFrame.centerY());
        M.postTranslate(-mOriginFrame.left, -mOriginFrame.top);
        M.postScale(scale, scale);
        path.transform(M);
        path.setScaleRate(1);

        switch (path.getMode()) {
            case DOODLE:
                mGraffitis.add(new IMGModel(path));
                break;
            case MOSAIC:
                path.setWidth(path.getWidth() * scale);
                mMosaics.add(path);
                break;
        }
    }

    private void moveToForeground(IMGSticker sticker) {
        if (sticker == null) return;

        moveToBackground(mForeSticker);

        if (sticker.isShowing()) {
            mForeSticker = sticker;
        } else sticker.show();
    }

    private void moveToBackground(IMGSticker sticker) {
        if (sticker == null) return;

        if (!sticker.isShowing()) {
            if (mForeSticker == sticker) {
                mForeSticker = null;
            }
        } else {
            sticker.dismiss();
        }
    }

    public void stickAll() {
        moveToBackground(mForeSticker);
    }

    public void onDismiss(IMGSticker sticker) {
        moveToBackground(sticker);
    }

    public void onShowing(IMGSticker sticker) {
        if (mForeSticker != sticker) {
            moveToForeground(sticker);
        }
    }

    public void onRemoveSticker(IMGSticker sticker) {
        if (mForeSticker == sticker) {
            mForeSticker = null;
        }

        for (IMGModel model : mGraffitis) {
            if (model.equalsSticker(sticker)) {
                mGraffitis.remove(model);
                break;
            }
        }
    }

    public void onWindowChanged(float width, float height) {
        if (width == 0 || height == 0) {
            return;
        }

        mWindow.set(0, 0, width, height);

        if (!isInitialHoming) {
            onInitialHoming(width, height);
        } else {

            // Pivot to fit window.
            M.setTranslate(mWindow.centerX() - mFrame.centerX(), mWindow.centerY() - mFrame.centerY());
            M.mapRect(mFrame);
            M.mapRect(mOriginFrame);
            M.mapRect(mClipFrame);
        }

        mClipWin.setClipWinSize(width, height);
    }

    private void onInitialHoming(float width, float height) {
        mOriginFrame.set(0, 0, mImage.getWidth(), mImage.getHeight());
        mFrame.set(0, 0, mImage.getWidth(), mImage.getHeight());
        mClipFrame.set(mFrame);
        mClipWin.setClipWinSize(width, height);

        if (mClipFrame.isEmpty()) {
            return;
        }

        toBaseHoming();

        isInitialHoming = true;
        onInitialHomingDone();
    }

    private void toBaseHoming() {
        float scale = Math.min(
                mWindow.width() / mFrame.width(),
                mWindow.height() / mFrame.height()
        );

        // Scale to fit window.
        M.setScale(scale, scale, mFrame.centerX(), mFrame.centerY());
        M.postTranslate(mWindow.centerX() - mFrame.centerX(), mWindow.centerY() - mFrame.centerY());
        M.mapRect(mFrame);
        M.mapRect(mOriginFrame);
        M.mapRect(mClipFrame);
    }

    private void onInitialHomingDone() {
        if (mMode == IMGMode.CLIP) {
            mClipWin.reset(mClipFrame, getTargetRotate());
        }
    }

    public void onDrawImage(Canvas canvas) {

        if (!mIsStartClip) {
            mIsStartClip = mClipWin.isClipping();
        }

        if (mIsStartClip) {
            // 裁剪区域
            canvas.clipRect(mFrame);
        }

        // 绘制图片
        canvas.drawBitmap(mImage, null, mOriginFrame, null);
    }

    public int onDrawMosaicsPath(Canvas canvas) {
        int layerCount = canvas.saveLayer(mFrame, null, Canvas.ALL_SAVE_FLAG);

        if (!isMosaicEmpty()) {
            canvas.save();
            float scale = getScale();
            canvas.translate(mFrame.left, mFrame.top);
            canvas.scale(scale, scale);
            for (IMGPath path : mMosaics) {
                path.onDrawMosaic(canvas, mPaint);
            }
            canvas.restore();
        }

        return layerCount;
    }

    public void onDrawMosaic(Canvas canvas, int layerCount) {
        canvas.drawBitmap(mMosaicImage, null, mFrame, mMosaicPaint);
        canvas.restoreToCount(layerCount);
    }

    // 填充白底
    public void onDrawWhiteRect(Canvas canvas) {
        float [] whiteOffset = {0f, 0f, 0f, 0f};
        float scale = getScale();
        Matrix matrix = new Matrix();
        matrix.preTranslate(mOriginFrame.left, mOriginFrame.top);
        matrix.preScale(scale, scale);

        RectF rectF = new RectF(mOriginFrame);
        for (IMGModel model : mGraffitis) {
            IMGPath path = model.getImgPath();
            IMGSticker sticker = model.getSticker();

            if (path != null) {
                rectF = path.onDrawWhiteRect(rectF, matrix, whiteOffset);
            }

            if (sticker != null) {
                rectF = sticker.onDrawWhiteRect(rectF, whiteOffset);
            }
        }

        Paint paintWhite = new Paint();
        paintWhite.setColor(Color.WHITE);
        paintWhite.setStyle(Paint.Style.FILL);
        canvas.drawRect(rectF, paintWhite);

        for (int i = 0; i < 4; i++) {
            float offset = whiteOffset[i];
            switch (i) {
                case 0:
                    mFrame.left = mOriginFrame.left + offset;
                    break;
                case 1:
                    mFrame.top = mOriginFrame.top + offset;
                    break;
                case 2:
                    mFrame.right = mOriginFrame.right + offset;
                    break;
                case 3:
                    mFrame.bottom = mOriginFrame.bottom + offset;
                    break;
            }
        }
    }

    // 画涂鸦（包括文本）
    public void onDrawGraffiti(Canvas canvas) {
        for (int i = 0; i < mGraffitis.size(); i++) {
            IMGPath path = mGraffitis.get(i).getImgPath();
            IMGSticker sticker = mGraffitis.get(i).getSticker();
            if (path != null) onDrawDoodles(canvas, path, i);
            if (sticker != null) onDrawStickers(canvas, sticker);
        }
    }

    private void onDrawDoodles(Canvas canvas, IMGPath path, int index) {
        float scale = getScale();

        Matrix matrix = new Matrix();
        matrix.preTranslate(mOriginFrame.left, mOriginFrame.top);
        matrix.preScale(scale, scale);

        path.onDrawDoodle(canvas, mPaint, matrix, index == mCheckedDoodleIndex);
    }

    public void onDrawStickerClip(Canvas canvas) {
        M.setRotate(getRotate(), mClipFrame.centerX(), mClipFrame.centerY());
        M.mapRect(mTempClipFrame, mClipWin.isClipping() ? mFrame : mClipFrame);
        canvas.clipRect(mTempClipFrame);
    }

    private void onDrawStickers(Canvas canvas, IMGSticker sticker) {
        if (!sticker.isShowing()) {
            float tPivotX = sticker.getX() + sticker.getPivotX();
            float tPivotY = sticker.getY() + sticker.getPivotY();

            canvas.save();
            M.setTranslate(sticker.getX(), sticker.getY());
            M.postScale(sticker.getScale(), sticker.getScale(), tPivotX, tPivotY);
            M.postRotate(sticker.getRotation(), tPivotX, tPivotY);

            canvas.concat(M);
            sticker.onSticker(canvas);
            canvas.restore();
        }
    }

    public void onDrawShade(Canvas canvas) {
        if (mMode == IMGMode.CLIP && isSteady) {
            mShade.reset();
            mShade.addRect(mFrame.left - 2, mFrame.top - 2, mFrame.right + 2, mFrame.bottom + 2, Path.Direction.CW);
            mShade.addRect(mClipFrame, Path.Direction.CCW);
            canvas.drawPath(mShade, mShadePaint);
        }
    }

    public void onDrawClip(Canvas canvas, float scrollX, float scrollY) {
        if (mMode == IMGMode.CLIP) {
            mClipWin.onDraw(canvas);
        }
    }

    public void onTouchDown(float x, float y) {
        isSteady = false;
        moveToBackground(mForeSticker);
        if (mMode == IMGMode.CLIP) {
            mAnchor = mClipWin.getAnchor(x, y);
        }
    }

    public void onTouchUp(float scrollX, float scrollY) {
        if (mAnchor != null) {
            mAnchor = null;
        }
    }

    public void onSteady(float scrollX, float scrollY) {
        isSteady = true;
        onClipHoming();
        mClipWin.setShowShade(true);
    }

    public void onScaleBegin() {

    }

    public IMGHoming onScroll(float scrollX, float scrollY, float dx, float dy) {
        if (mMode == IMGMode.CLIP) {
            mClipWin.setShowShade(false);
            if (mAnchor != null) {
                mClipWin.onScroll(mAnchor, dx, dy);

                RectF clipFrame = new RectF();
                M.setRotate(getRotate(), mClipFrame.centerX(), mClipFrame.centerY());
                M.mapRect(clipFrame, mFrame);

                RectF frame = mClipWin.getOffsetFrame(scrollX, scrollY);
                IMGHoming homing = new IMGHoming(scrollX, scrollY, getScale(), getTargetRotate());
                homing.rConcat(IMGUtils.fillHoming(frame, clipFrame, mClipFrame.centerX(), mClipFrame.centerY()));
                return homing;
            }
        }
        return null;
    }

    public float getTargetRotate() {
        return mTargetRotate;
    }

    public void setTargetRotate(float targetRotate) {
        this.mTargetRotate = targetRotate;
    }

    /**
     * 在当前基础上旋转
     */
    public void rotate(int rotate) {
        mTargetRotate = Math.round((mRotate + rotate) / 90f) * 90;
        mClipWin.reset(mClipFrame, getTargetRotate());
    }

    public float getRotate() {
        return mRotate;
    }

    public void setRotate(float rotate) {
        mRotate = rotate;
    }

    public float getScale() {
        return 1f * mOriginFrame.width() / mImage.getWidth();
    }

    public void resetInitialScale() {
        mInitialScale = -1;
    }

    public float getInitialScale() {
        if (mInitialScale < 0) {
            mInitialScale = getScale();
        }

        return mInitialScale;
    }

    // 缩放图片，让整个屏幕能显示全
    public void resetScaleToShowAll() {
        Log.e("ousyyy", "isOutSide");
        float scale = Math.min(
                mWindow.width() / mFrame.width(),
                mWindow.height() / mFrame.height()
        );

        float factor  = scale;

        M.setScale(scale, scale, mFrame.centerX(), mFrame.centerY());
        M.postTranslate(mWindow.centerX() - mFrame.centerX(), mWindow.centerY() - mFrame.centerY());
        M.mapRect(mFrame);
        M.mapRect(mOriginFrame);
        M.mapRect(mClipFrame);

        for (IMGModel model : mGraffitis) {
            IMGSticker sticker = model.getSticker();
            if (sticker == null) continue;
            M.mapRect(sticker.getFrame());
            float tPivotX = sticker.getX() + sticker.getPivotX();
            float tPivotY = sticker.getY() + sticker.getPivotY();
            sticker.addScale(factor);
            sticker.setX(sticker.getX() + sticker.getFrame().centerX() - tPivotX);
            sticker.setY(sticker.getY() + sticker.getFrame().centerY() - tPivotY);
        }
    }

    public void setScale(float scale) {
        setScale(scale, mFrame.centerX(), mFrame.centerY());
    }

    public void setScale(float scale, float focusX, float focusY) {
        onScale(scale / getScale(), focusX, focusY);
    }

    public void onScale(float factor, float focusX, float focusY) {
        if (mIsSetCenterXY) {
            mIsSetCenterXY = false;
            mLastCenterX = mFrame.centerX();
            mLastCenterY = mFrame.centerY();
        }

        if (factor == 1f) return;

        if (Math.max(mFrame.width(), mFrame.height()) >= MAX_SIZE
                || Math.min(mFrame.width(), mFrame.height()) <= MIN_SIZE) {
            factor += (1 - factor) / 2;
        }

        M.setScale(factor, factor, focusX, focusY);
        M.mapRect(mFrame);
        M.mapRect(mOriginFrame); // todo ousy
        M.mapRect(mClipFrame);

        for (IMGModel model : mGraffitis) {
            IMGSticker sticker = model.getSticker();
            if (sticker == null) continue;
            M.mapRect(sticker.getFrame());
            float tPivotX = sticker.getX() + sticker.getPivotX();
            float tPivotY = sticker.getY() + sticker.getPivotY();
            sticker.addScale(factor);
            sticker.setX(sticker.getX() + sticker.getFrame().centerX() - tPivotX);
            sticker.setY(sticker.getY() + sticker.getFrame().centerY() - tPivotY);
        }
    }

    public void onScaleEnd() {
    }

    public void onHomingStart(boolean isRotate) {
        isAnimCanceled = false;
        isDrawClip = true;
    }

    public void onHoming(float fraction) {
        mClipWin.homing(fraction);
    }

    /**
     *
     * @param rotate
     * @return
     */
    public boolean onHomingEnd(boolean rotate) {
        isDrawClip = true;
        if (mMode == IMGMode.CLIP) {
            // 开启裁剪模式

            boolean clip = !isAnimCanceled;

            mClipWin.setHoming(false);
            mClipWin.setClipping(true);
            mClipWin.setResetting(false);

            return clip;
        } else {
            if (isFreezing && !isAnimCanceled) {
                if (mViewCallback != null) {
                    mViewCallback.onHoming();
                }

                setFreezing(false);
            }
        }
        return false;
    }

    public boolean isFreezing() {
        return isFreezing;
    }

    private void setFreezing(boolean freezing) {
        if (freezing != isFreezing) {
            rotateStickers(freezing ? -getRotate() : getTargetRotate());
            isFreezing = freezing;
        }
    }

    public void onHomingCancel(boolean isRotate) {
        isAnimCanceled = true;
        Log.d(TAG, "Homing cancel");
    }

    public void release() {
        if (mImage != null && !mImage.isRecycled()) {
            mImage.recycle();
        }
    }

    public void setNeedShowLimitToast(boolean needShowLimitToast) {
        mIsNeedShowLimitToast = needShowLimitToast;
    }

    /**
     * 是否超过最大限制
     * 需求是增加的白底加图片的总宽高，不能超过图片宽高的5倍
     * @param path
     */
    public boolean isLimitExceeded(Path path) {
        RectF bounds = new RectF();
        path.computeBounds(bounds, true);
        float left = bounds.left < mFrame.left ? bounds.left : mFrame.left;
        float top = bounds.top < mFrame.top ? bounds.top : mFrame.top;
        float right = bounds.right > mFrame.right ? bounds.right : mFrame.right;
        float bottom = bounds.bottom > mFrame.bottom ? bounds.bottom : mFrame.bottom;
        float width = right - left;
        float height = bottom - top;
        if (!(width >= mOriginFrame.width() && width <= mOriginFrame.width() * 5)
                || !(height >= mOriginFrame.height() && height <= mOriginFrame.height() * 5)) {
            if (mIsNeedShowLimitToast) {
                mIsNeedShowLimitToast = false;
                Log.e("ousyyy", "图片尺寸超出限制");
            }
            return true;
        }

        return false;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (DEFAULT_IMAGE != null) {
            DEFAULT_IMAGE.recycle();
        }
    }
}
