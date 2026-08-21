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

    private static final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.startsWith("com.tencent.mm")) {
            return;
        }
        initToastBlocker();
    }

    private void initToastBlocker() {
        try {
            // 1. 捕获 makeText 创建的文本内容并绑定到 Toast 实例上
            XposedHelpers.findAndHookMethod(Toast.class, "makeText",
                    android.content.Context.class, CharSequence.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Toast toast = (Toast) param.getResult();
                            CharSequence text = (CharSequence) param.args[1];
                            if (toast != null && text != null) {
                                XposedHelpers.setAdditionalInstanceField(toast, "toast_text", text.toString());
                            }
                        }
                    });

            // 2. 统一在 show 时拦截并提取内容
            XposedHelpers.findAndHookMethod(Toast.class, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    int id = counter.incrementAndGet();
                    String content = "";

                    try {
                        Toast toast = (Toast) param.thisObject;
                        
                        // 先尝试从绑定的属性中获取文本（高版本原生 Toast 必备）
                        content = (String) XposedHelpers.getAdditionalInstanceField(toast, "toast_text");
                        
                        // 如果为空，再尝试从自定义 View 中递归提取
                        if (content == null || content.isEmpty()) {
                            View view = (View) XposedHelpers.callMethod(toast, "getView");
                            if (view != null) {
                                content = extractText(view);
                            }
                        }
                    } catch (Throwable ignored) {}

                    if (content == null || content.isEmpty()) {
                        content = "未知内容";
                    }

                    // 拦截并吞掉弹窗
                    param.setResult(null);
                    XposedBridge.log("WeChatShield: [Toast拦截] # " + id + " | 内容: " + content);
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("WeChatShield: Hook Toast 失败 -> " + e.getMessage());
        }
    }

    private String extractText(View view) {
        if (view == null) return "";
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            return text != null ? text.toString() : "";
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                String sub = extractText(group.getChildAt(i));
                if (sub != null && !sub.isEmpty()) {
                    return sub;
                }
            }
        }
        return "";
    }
}
