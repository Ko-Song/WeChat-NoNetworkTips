package com.example.wechatshield;

import android.widget.Toast;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 严格过滤微信包名，适配所有子进程防掉线
        if (!lpparam.packageName.startsWith("com.tencent.mm")) {
            return;
        }

        // 初始化一刀切 Toast 拦截
        initToastBlocker(lpparam);
    }

    private void initToastBlocker(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Toast 的 show 方法，一刀切拦截所有弹窗，无需关键词匹配
            XposedHelpers.findAndHookMethod(Toast.class, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // 直接置空结果，阻止 Toast 弹窗显示
                    param.setResult(null);
                    XposedBridge.log("WeChatShield: Successfully blocked a Toast message in WeChat.");
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("WeChatShield: Failed to hook Toast -> " + e.getMessage());
        }
    }
}
