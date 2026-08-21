package com.example.wechatshield;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Method;
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
        if (!lpparam.processName.equals("com.tencent.mm")) return;

        // 1. 保持 Toast 拦截
        initToastBlocker();

        // 2. 智能容错的 Banner 拦截
        initBannerBlocker(lpparam.classLoader);
    }

    private void initBannerBlocker(ClassLoader cl) {
        String targetClass = "com.tencent.mm.ui.conversation.banner.k0";

        try {
            // 尝试在这个 ClassLoader 中寻找目标类
            Class<?> clazz = XposedHelpers.findClass(targetClass, cl);
            
            XposedBridge.log("WeChatShield: 成功在当前 ClassLoader 中定位到类: " + targetClass);

            // 遍历并拦截该类的所有方法，重点处理布尔返回值或直接让其失效
            for (Method method : clazz.getDeclaredMethods()) {
                String methodName = method.getName();
                if (methodName.equals("toString") || methodName.equals("hashCode")) continue;

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        // 打印被调用的方法名，帮助我们确认到底是哪个方法在作祟
                        // XposedBridge.log("WeChatShield: 触发方法 -> " + param.method.getName());
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            // 如果方法返回布尔值，且原本要求显示（true），直接强行篡改并压制为 false
                            if (param.getResult() instanceof Boolean && (Boolean) param.getResult()) {
                                param.setResult(false);
                                XposedBridge.log("WeChatShield: [成功拦截] 已将 " + param.method.getName() + " 的返回值篡改为 false");
                            }
                        } catch (Throwable ignored) {}
                    }
                });
            }
            XposedBridge.log("WeChatShield: [成功] " + targetClass + " 的方法群已全部纳入拦截网！");

        } catch (XposedHelpers.ClassNotFoundError e) {
            // 正常的 ClassLoader 隔离现象，静默忽略，等待正确的 ClassLoader 出现
        } catch (Throwable e) {
            XposedBridge.log("WeChatShield: Banner 拦截异常: " + e.getMessage());
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
