// 键盘布局偏好，输入语言和方案继续由服务处理。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.content.res.Configuration;

final class KeyboardPreferences {
    private final Context context;
    final int height;
    final int theme;
    final boolean dark;
    final int nasal;

    KeyboardPreferences(Context context) {
        this.context = context;
        height = Math.max(-1, Math.min(1, LocalStorage.open(context, "keyboard").getInt("height", 0)));
        theme = Math.max(0, Math.min(2, LocalStorage.open(context, "keyboard").getInt("theme", 0)));
        nasal = (LocalStorage.open(context, "keyboard").getInt("an_ang", 0) == 1 ? 1 : 0)
                | (LocalStorage.open(context, "keyboard").getInt("en_eng", 0) == 1 ? 2 : 0)
                | (LocalStorage.open(context, "keyboard").getInt("in_ing", 0) == 1 ? 4 : 0);
        dark = theme == 2 || theme == 0 && (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    int signature() { return height * 3 + theme + (dark ? 100 : 0) + nasal * 256; }

    static int nasalBit(String name) {
        switch (name) {
            case "an_ang": return 1;
            case "en_eng": return 2;
            case "in_ing": return 4;
            default: return 0;
        }
    }

    int rowHeight() {
        boolean landscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        return (landscape ? 40 : 62) + height * (landscape ? 4 : 8);
    }
}
