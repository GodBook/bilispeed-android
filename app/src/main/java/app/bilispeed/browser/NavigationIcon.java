package app.bilispeed.browser;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Small consistent stroke icons, drawn locally without a font or image dependency. */
final class NavigationIcon extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int item;
    NavigationIcon(int item, int color) {
        this.item = item;
        paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.7f);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
    }
    @Override public void draw(Canvas canvas) {
        canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
        path.reset();
        switch (item) {
            case 0:
                path.moveTo(3, 10); path.lineTo(12, 3); path.lineTo(21, 10);
                path.moveTo(5, 9); path.lineTo(5, 21); path.lineTo(10, 21); path.lineTo(10, 15);
                path.lineTo(14, 15); path.lineTo(14, 21); path.lineTo(19, 21); path.lineTo(19, 9); break;
            case 1:
                path.moveTo(13, 3); path.cubicTo(15, 9, 20, 9, 20, 15); path.cubicTo(20, 23, 4, 23, 4, 15);
                path.cubicTo(4, 11, 8, 9, 8, 7); path.lineTo(11, 11); path.cubicTo(14, 8, 13, 5, 13, 3);
                path.moveTo(12, 14); path.cubicTo(7, 18, 11, 21, 14, 19); break;
            case 2:
                canvas.drawCircle(10.5f, 10.5f, 7, paint); path.moveTo(16, 16); path.lineTo(21, 21); break;
            case 3:
                path.moveTo(4, 6); path.lineTo(4, 20); path.lineTo(20, 20); path.lineTo(20, 6); path.close();
                path.moveTo(8, 3); path.lineTo(8, 8); path.moveTo(16, 3); path.lineTo(16, 8);
                path.moveTo(8, 12); path.lineTo(16, 12); path.moveTo(8, 16); path.lineTo(13, 16); break;
            case 4:
                canvas.drawCircle(12, 7, 4, paint); path.moveTo(4, 21); path.cubicTo(4, 11, 20, 11, 20, 21); break;
            default:
                for (int point = 0; point < 32; point++) {
                    double angle = Math.PI * 2 * point / 32 - Math.PI / 8;
                    float radius = point % 4 < 2 ? 10 : 8;
                    float x = 12 + (float) Math.cos(angle) * radius, y = 12 + (float) Math.sin(angle) * radius;
                    if (point == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                path.close(); canvas.drawCircle(12, 12, 3.4f, paint);
        }
        canvas.drawPath(path, paint); canvas.restore();
    }
    @Override public void setTint(int color) { paint.setColor(color); invalidateSelf(); }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
