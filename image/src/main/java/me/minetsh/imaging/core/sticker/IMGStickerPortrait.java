package me.minetsh.imaging.core.sticker;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;

/**
 * Created by felix on 2017/11/16 下午5:54.
 */

public interface IMGStickerPortrait {

    boolean show();

    boolean remove();

    boolean dismiss();

    boolean isShowing();

    RectF getFrame();

//    RectF getAdjustFrame();
//
//    RectF getDeleteFrame();

    void onSticker(Canvas canvas);

    void registerCallback(IMGSticker.Callback callback);

    void unregisterCallback(IMGSticker.Callback callback);

    interface Callback {

        <V extends View & IMGSticker> void onDismiss(V stickerView);

        <V extends View & IMGSticker> void onShowing(V stickerView);

        <V extends View & IMGSticker> boolean onRemove(V stickerView);

        // 从触摸文本的回调
        void onTouchDown();

        // 从触摸文本到抬起手指的回调
        <V extends View & IMGSticker> void onTouchMove(V stickerView);

        // 从触摸文本到抬起手指的回调
        <V extends View & IMGSticker> void onTouchUp(V stickerView);
    }
}
