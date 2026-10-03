// 键盘布局偏好，输入语言和方案继续由服务处理。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.content.res.Configuration;

final class KeyboardPreferences {
    private final Context context;
    final int height;
    final int theme;
    final boolean dark;

    KeyboardPreferences(Context context) {
        this.context = context;
        height = Math.max(-1, Math.min(1, context.getSharedPreferences("keyboard", Context.MODE_PRIVATE).getInt("height", 0)));
        theme = Math.max(0, Math.min(2, context.getSharedPreferences("keyboard", Context.MODE_PRIVATE).getInt("theme", 0)));
        dark = theme == 2 || theme == 0 && (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    int signature() { return height * 3 + theme + (dark ? 100 : 0); }

    int rowHeight() {
        boolean landscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        return (landscape ? 40 : 57) + height * (landscape ? 4 : 8);
    }
}
