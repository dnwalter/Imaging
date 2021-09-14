package me.minetsh.imaging.core.entity;

import me.minetsh.imaging.core.IMGPath;
import me.minetsh.imaging.core.sticker.IMGSticker;

public class IMGModel {
    private IMGPath mImgPath;
    private IMGSticker mSticker;

    public IMGModel(IMGPath imgPath) {
        mImgPath = imgPath;
    }

    public IMGModel(IMGSticker sticker) {
        mSticker = sticker;
    }

    public boolean equalsSticker(IMGSticker sticker) {
        boolean result = false;
        if (mSticker != null) {
            result = mSticker.equals(sticker);
        }

        return result;
    }

    public IMGPath getImgPath() {
        return mImgPath;
    }

    public void setImgPath(IMGPath imgPath) {
        mImgPath = imgPath;
    }

    public IMGSticker getSticker() {
        return mSticker;
    }

    public void setSticker(IMGSticker sticker) {
        mSticker = sticker;
    }
}
