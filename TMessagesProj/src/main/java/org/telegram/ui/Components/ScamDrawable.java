package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.rooms.messenger.R;

public class ScamDrawable extends Drawable {

    private RectF rect = new RectF();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private int textWidth;
    private String text;

    public ScamDrawable(int textSize) {
        super();
        textPaint.setTextSize(AndroidUtilities.dp(textSize));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1));

        text = LocaleController.getString("ScamMessage", R.string.ScamMessage);
        textWidth = (int) Math.ceil(textPaint.measureText(text));
    }

    public void checkText() {
        String newText = LocaleController.getString("ScamMessage", R.string.ScamMessage);
        if (!newText.equals(text)) {
            text = newText;
            textWidth = (int) Math.ceil(textPaint.measureText(text));
        }
    }

    public void setColor(int color) {
        textPaint.setColor(color);
        paint.setColor(color);
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public int getIntrinsicWidth() {
        return textWidth + AndroidUtilities.dp(5 * 2);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(16);
    }

    @Override
    public void draw(Canvas canvas) {
        rect.set(getBounds());
        canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), paint);
        canvas.drawText(text, rect.left + AndroidUtilities.dp(5), rect.top + AndroidUtilities.dp(12), textPaint);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
