/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.rooms.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ProgressButton;
import org.telegram.ui.Components.RecyclerListView;

import java.util.List;

public class FeaturedStickerSetCell2 extends FrameLayout {

    private final int currentAccount = UserConfig.selectedAccount;

    private final TextView textView;
    private final TextView valueTextView;
    private final BackupImageView imageView;
    private final ProgressButton addButton;
    private final TextView delButton;

    private AnimatorSet currentAnimation;
    private TLRPC.StickerSetCovered stickersSet;
    private boolean isInstalled;
    private boolean needDivider;

    public FeaturedStickerSetCell2(Context context) {
        super(context);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, LocaleController.isRTL ? 22 : 71, 10, LocaleController.isRTL ? 71 : 22, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, LocaleController.isRTL ? 100 : 71, 35, LocaleController.isRTL ? 71 : 100, 0));

        imageView = new BackupImageView(context);
        imageView.setAspectFit(true);
        imageView.setLayerNum(1);
        addView(imageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 12, 8, LocaleController.isRTL ? 12 : 0, 0));

        addButton = new ProgressButton(context);
        addButton.setText(LocaleController.getString("Add", R.string.Add));
        addButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        addView(addButton, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.END, 0, 18, 14, 0));

        delButton = new TextView(context);
        delButton.setGravity(Gravity.CENTER);
        delButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_removeButtonText));
        delButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        delButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        delButton.setText(LocaleController.getString("StickersRemove", R.string.StickersRemove));
        addView(delButton, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.END, 0, 16, 14, 0));

        updateColors();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));

        int width = addButton.getMeasuredWidth();
        int width2 = delButton.getMeasuredWidth();
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) delButton.getLayoutParams();
        if (width2 < width) {
            layoutParams.rightMargin = AndroidUtilities.dp(14) + (width - width2) / 2;
        } else {
            layoutParams.rightMargin = AndroidUtilities.dp(14);
        }

        measureChildWithMargins(textView, widthMeasureSpec, width, heightMeasureSpec, 0);
    }

    public void setStickersSet(TLRPC.StickerSetCovered set, boolean divider, boolean unread, boolean forceInstalled, boolean animated) {
        if (currentAnimation != null) {
            currentAnimation.cancel();
            currentAnimation = null;
        }

        needDivider = divider;
        stickersSet = set;
        setWillNotDraw(!needDivider);

        textView.setText(stickersSet.set.title);
        if (unread) {
            Drawable drawable = new Drawable() {

                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

                @Override
                public void draw(Canvas canvas) {
                    paint.setColor(0xff44a8ea);
                    canvas.drawCircle(AndroidUtilities.dp(4), AndroidUtilities.dp(5), AndroidUtilities.dp(3), paint);
                }

                @Override
                public void setAlpha(int alpha) {

                }

                @Override
                public void setColorFilter(ColorFilter colorFilter) {

                }

                @Override
                public int getOpacity() {
                    return PixelFormat.TRANSPARENT;
                }

                @Override
                public int getIntrinsicWidth() {
                    return AndroidUtilities.dp(12);
                }

                @Override
                public int getIntrinsicHeight() {
                    return AndroidUtilities.dp(8);
                }
            };
            textView.setCompoundDrawablesWithIntrinsicBounds(LocaleController.isRTL ? null : drawable, null, LocaleController.isRTL ? drawable : null, null);
        } else {
            textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }

        valueTextView.setText(LocaleController.formatPluralString("Stickers", set.set.count));

        TLRPC.Document sticker;
        if (set.cover != null) {
            sticker = set.cover;
        } else if (!set.covers.isEmpty()) {
            sticker = set.covers.get(0);
        } else {
            sticker = null;
        }
        if (sticker != null) {
            if (MessageObject.canAutoplayAnimatedSticker(sticker)) {
                TLObject object;
                if (set.set.thumb instanceof TLRPC.TL_photoSize || set.set.thumb instanceof TLRPC.TL_photoSizeProgressive) {
                    object = set.set.thumb;
                } else {
                    object = sticker;
                }
                ImageLocation imageLocation;

                if (object instanceof TLRPC.Document) { // first sticker in set as a thumb
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
                    imageLocation = ImageLocation.getForDocument(thumb, sticker);
                } else { // unique thumb
                    TLRPC.PhotoSize thumb = (TLRPC.PhotoSize) object;
                    imageLocation = ImageLocation.getForSticker(thumb, sticker);
                }

                if (object instanceof TLRPC.Document && MessageObject.isAnimatedStickerDocument(sticker, true)) {
                    imageView.setImage(ImageLocation.getForDocument(sticker), "50_50", imageLocation, null, 0, set);
                } else if (imageLocation != null && imageLocation.imageType == FileLoader.IMAGE_TYPE_LOTTIE) {
                    imageView.setImage(imageLocation, "50_50", "tgs", null, set);
                } else {
                    imageView.setImage(imageLocation, "50_50", "webp", null, set);
                }
            } else {
                final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
                if (thumb != null) {
                    imageView.setImage(ImageLocation.getForDocument(thumb, sticker), "50_50", "webp", null, set);
                } else {
                    imageView.setImage(ImageLocation.getForDocument(sticker), "50_50", "webp", null, set);
                }
            }
        } else {
            imageView.setImage(null, null, "webp", null, set);
        }

        addButton.setVisibility(VISIBLE);
        isInstalled = forceInstalled || MediaDataController.getInstance(currentAccount).isStickerPackInstalled(set.set.id);
        if (animated) {
            if (isInstalled) {
                delButton.setVisibility(VISIBLE);
            } else {
                addButton.setVisibility(VISIBLE);
            }
            currentAnimation = new AnimatorSet();
            currentAnimation.setDuration(250);
            currentAnimation.playTogether(
                    ObjectAnimator.ofFloat(delButton, View.ALPHA, isInstalled ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(delButton, View.SCALE_X, isInstalled ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(delButton, View.SCALE_Y, isInstalled ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(addButton, View.ALPHA, isInstalled ? 0.0f : 1.0f),
                    ObjectAnimator.ofFloat(addButton, View.SCALE_X, isInstalled ? 0.0f : 1.0f),
                    ObjectAnimator.ofFloat(addButton, View.SCALE_Y, isInstalled ? 0.0f : 1.0f));
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (isInstalled) {
                        addButton.setVisibility(INVISIBLE);
                    } else {
                        delButton.setVisibility(INVISIBLE);
                    }
                }
            });
            currentAnimation.setInterpolator(new OvershootInterpolator(1.02f));
            currentAnimation.start();
        } else {
            if (isInstalled) {
                delButton.setVisibility(VISIBLE);
                delButton.setAlpha(1.0f);
                delButton.setScaleX(1.0f);
                delButton.setScaleY(1.0f);
                addButton.setVisibility(INVISIBLE);
                addButton.setAlpha(0.0f);
                addButton.setScaleX(0.0f);
                addButton.setScaleY(0.0f);
            } else {
                addButton.setVisibility(VISIBLE);
                addButton.setAlpha(1.0f);
                addButton.setScaleX(1.0f);
                addButton.setScaleY(1.0f);
                delButton.setVisibility(INVISIBLE);
                delButton.setAlpha(0.0f);
                delButton.setScaleX(0.0f);
                delButton.setScaleY(0.0f);
            }
        }
    }

    public TLRPC.StickerSetCovered getStickerSet() {
        return stickersSet;
    }

    public void setAddOnClickListener(OnClickListener onClickListener) {
        addButton.setOnClickListener(onClickListener);
        delButton.setOnClickListener(onClickListener);
    }

    public void setDrawProgress(boolean value, boolean animated) {
        addButton.setDrawProgress(value, animated);
    }

    public boolean isInstalled() {
        return isInstalled;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(71), getHeight() - 1, getWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(71) : 0), getHeight() - 1, Theme.dividerPaint);
        }
    }

    public BackupImageView getImageView() {
        return imageView;
    }

    public void updateColors() {
        addButton.setProgressColor(Theme.getColor(Theme.key_featuredStickers_buttonProgress));
        addButton.setBackgroundRoundRect(Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed));
    }

    public static void createThemeDescriptions(List<ThemeDescription> descriptions, RecyclerListView listView, ThemeDescription.ThemeDescriptionDelegate delegate) {
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FeaturedStickerSetCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FeaturedStickerSetCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FeaturedStickerSetCell.class}, new String[]{"addButton"}, null, null, null, Theme.key_featuredStickers_buttonText));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FeaturedStickerSetCell.class}, new String[]{"delButton"}, null, null, null, Theme.key_featuredStickers_removeButtonText));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{FeaturedStickerSetCell.class}, Theme.dividerPaint, null, null, Theme.key_divider));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_featuredStickers_buttonProgress));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_featuredStickers_addButtonPressed));
    }
}
