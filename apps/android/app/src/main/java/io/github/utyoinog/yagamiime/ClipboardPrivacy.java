// 系统敏感标记与常见验证码内容仅允许即时粘贴，不进入历史。
package io.github.utyoinog.yagamiime;

import android.content.ClipDescription;

final class ClipboardPrivacy {
    static boolean sensitive(ClipDescription description, String text) {
        if (description != null && description.getExtras() != null
                && description.getExtras().getBoolean("android.content.extra.IS_SENSITIVE", false)) { return true; }
        String compact = text.trim().replace(" ", "").replace("-", "");
        return compact.matches("[0-9]{4,8}") || compact.matches("[0-9]{13,19}");
    }
}
