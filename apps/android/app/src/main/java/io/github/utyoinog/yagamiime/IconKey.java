// 将功能图标与可选标签作为一组居中，避免普通复合图标贴在按键边缘。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.widget.TextView;

final class IconKey extends TextView {
    private Drawable icon;
    private boolean above;

    IconKey(Context context) { super(context); }

    void icon(Drawable icon, boolean above) {
        this.icon = icon; this.above = above;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (icon == null) { super.onDraw(canvas); return; }
        int viewport = canvas.save();
        // TextView 的单行布局可能滚动文本；自绘图标按可见按键范围定位。
        canvas.translate(getScrollX(), getScrollY());
        Paint paint = getPaint();
        paint.setColor(getCurrentTextColor());
        float size = icon.getBounds().width();
        float gap = getText().length() == 0 ? 0 : getCompoundDrawablePadding();
        float available = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight() - (above ? 0 : size + gap));
        String label = TextUtils.ellipsize(getText(), getPaint(), available, TextUtils.TruncateAt.END).toString();
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float textHeight = label.isEmpty() ? 0 : metrics.descent - metrics.ascent;
        float textWidth = paint.measureText(label);
        float x, y, labelX, baseline;
        if (above) {
            x = (getWidth() - size) / 2;
            y = (getHeight() - size - gap - textHeight) / 2;
            labelX = (getWidth() - textWidth) / 2;
            baseline = y + size + gap - metrics.ascent;
        } else {
            x = (getWidth() - size - gap - textWidth) / 2;
            y = (getHeight() - size) / 2;
            labelX = x + size + gap;
            baseline = getHeight() / 2f - (metrics.ascent + metrics.descent) / 2;
        }
        int saved = canvas.save();
        canvas.translate(x, y); icon.draw(canvas); canvas.restoreToCount(saved);
        if (!label.isEmpty()) { canvas.drawText(label, labelX, baseline, paint); }
        canvas.restoreToCount(viewport);
    }
}
