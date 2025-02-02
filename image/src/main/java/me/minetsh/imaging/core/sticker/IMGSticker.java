package me.minetsh.imaging.core.sticker;

import android.graphics.RectF;

import me.minetsh.imaging.core.IMGViewPortrait;

/**
 * Created by felix on 2017/11/14 下午7:31.
 */

public interface IMGSticker extends IMGStickerPortrait, IMGViewPortrait {
    RectF onDrawWhiteRect(RectF frame, float[] whiteOffset);

    // 返回不包括小图标的frame
    RectF getFrameNoIcon();
}
