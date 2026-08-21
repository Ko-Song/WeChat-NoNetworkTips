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
            XposedHelpers.findAndHookMethod(Toast.class, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    int id = counter.incrementAndGet();
                    String content = "未知内容";

                    try {
                        Toast toast = (Toast) param.thisObject;
                        View view = (View) XposedHelpers.callMethod(toast, "getView");
                        if (view != null) {
                            content = extractText(view);
                            if (content.isEmpty()) content = "自定义布局Toast";
                        } else {
                            content = "系统原生Toast(无View)";
                        }
                    } catch (Throwable ignored) {
                        content = "解析异常";
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
