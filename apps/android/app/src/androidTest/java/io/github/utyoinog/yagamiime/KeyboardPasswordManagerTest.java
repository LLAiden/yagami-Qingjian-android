// 密码管理器入口在未安装应用时保持当前拼音，避免意外提交或切走。
package io.github.utyoinog.yagamiime;

public final class KeyboardPasswordManagerTest extends KeyboardTestCase {
    public void testMissingPasswordManagerKeepsPinyinAndKeyboard() throws Exception {
        assertNull(getInstrumentation().getTargetContext().getPackageManager().getLaunchIntentForPackage("com.x8bit.bitwarden"));
        typeDigits("64"); click("密码管理器");
        click("返回键盘");
        node("选择拼音 ni"); node("收起键盘"); click("候选 你");
        await(() -> activity.message.getText().toString().equals("你"));
    }

    public void testMissingAuthenticatorKeepsPinyinAndKeyboard() throws Exception {
        assertNull(getInstrumentation().getTargetContext().getPackageManager().getLaunchIntentForPackage("com.google.android.apps.authenticator2"));
        typeDigits("64"); click("双因素验证");
        click("返回键盘");
        node("选择拼音 ni"); node("收起键盘"); click("候选 你");
        await(() -> activity.message.getText().toString().equals("你"));
    }
}
