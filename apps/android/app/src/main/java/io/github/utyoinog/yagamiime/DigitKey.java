// 数字始终位于按键几何中心，字母提示单独绘制在下方。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.widget.TextView;

final class DigitKey extends TextView {
    private final String digit;
    private final String caption;
    private final KeyboardStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    DigitKey(Context context, String digit, String caption, Runnable action) {
        super(context);
        this.digit = digit; this.caption = caption;
        style = new KeyboardStyle(context);
        setText(digit + (caption.isEmpty() ? "" : "\n" + caption));
        setContentDescription(digit);
        setClickable(true); setFocusable(false);
        setBackground(style.background(false));
        setOnClickListener(ignored -> action.run());
        paint.setTextAlign(Paint.Align.CENTER);
    }

    @Override protected void onDraw(Canvas canvas) {
        // 限制字号以适应横屏、矮键盘和系统大字体，不改变数字的中心位置。
        paint.setColor(style.text);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(Math.min(style.sp(24), getHeight() * 0.48f));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(digit, getWidth() / 2f, getHeight() / 2f - (metrics.ascent + metrics.descent) / 2, paint);
        if (!caption.isEmpty()) {
            paint.setColor(style.muted);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setTextSize(Math.min(style.sp(10), getHeight() * 0.2f));
            canvas.drawText(caption, getWidth() / 2f, getHeight() - style.dp(5), paint);
        }
    }
}
