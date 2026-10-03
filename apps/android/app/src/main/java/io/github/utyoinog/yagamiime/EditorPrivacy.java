// 由宿主声明与验证码字段提示决定隐私边界；不读取用户正文来判断。
package io.github.utyoinog.yagamiime;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

import java.util.Locale;

final class EditorPrivacy {
    static boolean privateInput(EditorInfo info) {
        int type = info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        String hint = info.hintText == null ? "" : info.hintText.toString().toLowerCase(Locale.ROOT);
        return (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
                || type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                || type == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                || hint.contains("验证码") || hint.contains("校验码") || hint.contains("one-time")
                || hint.contains("verification code") || hint.matches(".*\\botp\\b.*");
    }

    static boolean personalized(EditorInfo info) {
        if (info == null || privateInput(info)) { return false; }
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        return (info.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
                && variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                && variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                && variation != InputType.TYPE_TEXT_VARIATION_URI;
    }
}
