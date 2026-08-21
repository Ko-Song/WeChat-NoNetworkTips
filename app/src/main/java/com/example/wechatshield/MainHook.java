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
        if (!lpparam.processName.equals("com.tencent.mm")) return;

        // 1. 保持 Toast 拦截
        initToastBlocker();

        // 2. 挂载我们刚刚通过静态逆向找到的核心错误分发器
        initErrorProcessorHook(lpparam.classLoader);
    }

    private void initErrorProcessorHook(ClassLoader cl) {
        String targetClass = "com.tencent.mm.ui.pc";
        String targetMethod = "a";

        try {
            XposedHelpers.findAndHookMethod(targetClass, cl, targetMethod, 
                android.content.Context.class, int.class, int.class, String.class, int.class, 
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        int errType = (Integer) param.args[1];
                        int errCode = (Integer) param.args[2];
                        String extra = (String) param.args[3];
                        
                        // 打印日志，当网络横幅出现时，看看这里捕获到了什么错误码
                        XposedBridge.log("WeChatShield: [错误分发捕获] errType=" + errType + ", errCode=" + errCode + ", info=" + extra);
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        // 后续我们可以在这里根据 errType/errCode 强行干预返回值
                        // param.setResult(true); 
                    }
                });
            XposedBridge.log("WeChatShield: [静态逆向] com.tencent.mm.ui.pc.a 挂载成功！");
        } catch (Throwable e) {
            XposedBridge.log("WeChatShield: [静态逆向] 挂载失败: " + e.getMessage());
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
