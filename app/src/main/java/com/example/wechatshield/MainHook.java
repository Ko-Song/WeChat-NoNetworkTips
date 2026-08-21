package com.example.wechatshield;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final AtomicInteger toastCounter = new AtomicInteger(0);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.tencent.mm")) return;

        XposedBridge.log("WeChatShield: 微信已加载，开始 Hook...");
        initToastBlocker();
        initDebugBannerBlocker(lpparam.classLoader);
    }

    private void initDebugBannerBlocker(ClassLoader cl) {
        String targetClass = "com.tencent.mm.ui.conversation.banner.k0";
        
        try {
            Class<?> clazz = XposedHelpers.findClass(targetClass, cl);
            for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
                String methodName = method.getName();
                if (methodName.equals("toString") || methodName.equals("hashCode")) continue;

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("WeChatShield: [追踪] 调用了 " + param.method.getName());
                    }
                });
            }
            XposedBridge.log("WeChatShield: [追踪] 已成功 Hook 类 " + targetClass + " 下的所有方法！");
        } catch (Throwable e) {
            XposedBridge.log("WeChatShield: [错误] 追踪初始化失败: " + e.toString());
        }
    }

    private void initToastBlocker() {
        try {
            XposedHelpers.findAndHookMethod(Toast.class, "makeText", android.content.Context.class, CharSequence.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Toast toast = (Toast) param.getResult();
                    if (toast != null && param.args[1] != null) {
                        XposedHelpers.setAdditionalInstanceField(toast, "toast_text", param.args[1].toString());
                    }
                }
            });

            XposedHelpers.findAndHookMethod(Toast.class, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    int id = toastCounter.incrementAndGet();
                    String content = "";
                    try {
                        Toast toast = (Toast) param.thisObject;
                        content = (String) XposedHelpers.getAdditionalInstanceField(toast, "toast_text");
                        if (content == null) {
                            View view = (View) XposedHelpers.callMethod(toast, "getView");
                            content = extractText(view);
                        }
                    } catch (Throwable ignored) {}

                    param.setResult(null);
                    XposedBridge.log("WeChatShield: [Toast屏蔽] #" + id + " 内容: " + (content == null ? "未知" : content));
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("WeChatShield: Toast Hook 失败: " + e.getMessage());
        }
    }

    private String extractText(View view) {
        if (view instanceof TextView) return ((TextView) view).getText().toString();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                String sub = extractText(group.getChildAt(i));
                if (!sub.isEmpty()) return sub;
            }
        }
        return "";
    }
}
