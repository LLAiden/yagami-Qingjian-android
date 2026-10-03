// 统一 24 单位的线性矢量图标，不依赖系统字体中的符号字形。
package io.github.utyoinog.yagamiime;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

final class KeyIconDrawable extends Drawable {
    private final KeyIcon icon;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    KeyIconDrawable(KeyIcon icon, int color) {
        this.icon = icon;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override public void draw(Canvas canvas) {
        int saved = canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
        switch (icon) {
            case KEYBOARD:
                canvas.drawRoundRect(2, 5, 22, 19, 2, 2, paint);
                for (int y = 9; y <= 12; y += 3) {
                    for (int x = 6; x <= 18; x += 4) { line(canvas, x, y, x + 0.5f, y); }
                }
                line(canvas, 8, 16, 16, 16); break;
            case CLIPBOARD: case PASTE:
                canvas.drawRoundRect(6, 5, 19, 22, 2, 2, paint);
                canvas.drawRoundRect(9, 2, 16, 7, 1, 1, paint);
                line(canvas, 10, 12, 15, 12); line(canvas, 10, 16, 15, 16); break;
            case EDIT:
                path(canvas, 4, 16, 16, 4, 20, 8, 8, 20, 3, 21, 4, 16);
                line(canvas, 13, 7, 17, 11); break;
            case SELECT:
                canvas.drawRoundRect(3, 3, 21, 21, 2, 2, paint);
                path(canvas, 7, 12, 10, 15, 17, 8); break;
            case HIDE: case EXPAND:
                path(canvas, 5, 9, 12, 16, 19, 9); break;
            case COLLAPSE:
                path(canvas, 5, 15, 12, 8, 19, 15); break;
            case DELETE:
                path(canvas, 9, 5, 21, 5, 21, 19, 9, 19, 2, 12, 9, 5);
                line(canvas, 12, 9, 17, 15); line(canvas, 17, 9, 12, 15); break;
            case ENTER:
                path(canvas, 20, 5, 20, 13, 5, 13); path(canvas, 10, 8, 5, 13, 10, 18); break;
            case SHIFT:
                path(canvas, 4, 11, 12, 3, 20, 11, 16, 11, 16, 20, 8, 20, 8, 11, 4, 11); break;
            case CAPS_LOCK:
                path(canvas, 4, 10, 12, 2, 20, 10, 16, 10, 16, 17, 8, 17, 8, 10, 4, 10);
                line(canvas, 8, 22, 16, 22); break;
            case PASSWORD:
                canvas.drawCircle(7, 9, 5, paint);
                path(canvas, 11, 12, 20, 21, 23, 18, 20, 15, 18, 17, 15, 14); break;
            case AUTHENTICATOR:
                path(canvas, 12, 2, 21, 6, 20, 14, 17, 19, 12, 22, 7, 19, 4, 14, 3, 6, 12, 2);
                canvas.drawCircle(12, 11, 4, paint); path(canvas, 12, 8, 12, 11, 14, 12); break;
            case GLOBE:
                canvas.drawCircle(12, 12, 9, paint);
                canvas.drawOval(8, 3, 16, 21, paint); line(canvas, 3, 12, 21, 12); break;
            case SETTINGS:
                Path gear = new Path();
                for (int i = 0; i <= 32; i++) {
                    double angle = i * Math.PI / 16;
                    float radius = i % 4 < 2 ? 10 : 8;
                    float x = 12 + (float) Math.cos(angle) * radius;
                    float y = 12 + (float) Math.sin(angle) * radius;
                    if (i == 0) { gear.moveTo(x, y); } else { gear.lineTo(x, y); }
                }
                gear.close(); canvas.drawPath(gear, paint); canvas.drawCircle(12, 12, 3.5f, paint); break;
            case COPY:
                canvas.drawRoundRect(8, 8, 21, 21, 2, 2, paint);
                path(canvas, 16, 5, 16, 3, 3, 3, 3, 16, 5, 16); break;
            case CUT:
                canvas.drawCircle(6, 18, 3, paint); canvas.drawCircle(18, 18, 3, paint);
                line(canvas, 8, 16, 19, 3); line(canvas, 16, 16, 5, 3); break;
            case LEFT: case HOME: case BACK:
                path(canvas, 14, 5, 7, 12, 14, 19);
                if (icon == KeyIcon.HOME) { line(canvas, 3, 4, 3, 20); }
                break;
            case RIGHT: case END:
                path(canvas, 10, 5, 17, 12, 10, 19);
                if (icon == KeyIcon.END) { line(canvas, 21, 4, 21, 20); }
                break;
            case SYMBOLS:
                line(canvas, 8, 3, 6, 21); line(canvas, 17, 3, 15, 21);
                line(canvas, 3, 8, 21, 8); line(canvas, 2, 16, 20, 16); break;
            case TRASH:
                canvas.drawRoundRect(6, 7, 18, 22, 2, 2, paint);
                line(canvas, 3, 7, 21, 7); path(canvas, 9, 6, 9, 3, 15, 3, 15, 6);
                line(canvas, 10, 11, 10, 18); line(canvas, 14, 11, 14, 18); break;
            case PIN:
                path(canvas, 8, 3, 16, 3, 15, 11, 18, 15, 6, 15, 9, 11, 8, 3);
                line(canvas, 12, 15, 12, 22); break;
        }
        canvas.restoreToCount(saved);
    }

    private void line(Canvas canvas, float x1, float y1, float x2, float y2) {
        canvas.drawLine(x1, y1, x2, y2, paint);
    }

    private void path(Canvas canvas, float... points) {
        Path path = new Path(); path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) { path.lineTo(points[i], points[i + 1]); }
        canvas.drawPath(path, paint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
