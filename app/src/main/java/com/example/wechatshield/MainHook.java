package com.example.wechatshield;

import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.processName.equals("com.tencent.mm")) return;

        // 1. 保留已经生效的 Toast 拦截 (解决 pc.a 弹出的提示)
        initToastBlocker();

        // 2. 终极横幅击杀 (双管齐下)
        initBannerBlocker(lpparam.classLoader);
    }

    private void initBannerBlocker(ClassLoader classLoader) {
        // 【第一重绝杀】: 针对我们刚找出的真凶类 com.tencent.mm.ui.conversation.a
        try {
            Class<?> targetClass = XposedHelpers.findClass("com.tencent.mm.ui.conversation.a", classLoader);
            XposedBridge.hookAllMethods(targetClass, "a", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    // 如果这个方法返回的是横幅的 View，直接将其隐藏
                    if (param.getResult() instanceof View) {
                        ((View) param.getResult()).setVisibility(View.GONE);
                        XposedBridge.log("WeChatShield: [精准定位] 成功将 conversation.a 返回的横幅设为 GONE!");
                    }
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("WeChatShield: 目标类 hook 失败: " + e.getMessage());
        }

        // 【第二重绝杀】: 底层 UI 兜底 (以防它被包在其他布局里)
        try {
            // 直接在 Android 渲染文字的底层拦截，只要是这句话，连同它的父容器一起干掉
            XposedHelpers.findAndHookMethod(TextView.class, "setText", CharSequence.class, TextView.BufferType.class, boolean.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    CharSequence text = (CharSequence) param.args[0];
                    if (text != null && text.toString().contains("当前无法连接网络，可检查网络设置是否正常")) {
                        TextView tv = (TextView) param.thisObject;
                        tv.setVisibility(View.GONE);
                        
                        // 顺藤摸瓜，把包着文字的那个红色横幅外框也隐藏掉，防止留下一条空隙
                        if (tv.getParent() instanceof View) {
                            ((View) tv.getParent()).setVisibility(View.GONE);
                        }
                        
                        // 阻止文字渲染
                        param.setResult(null);
                        XposedBridge.log("WeChatShield: [UI底层击杀] 彻底粉碎无网络横幅 UI");
                    }
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("WeChatShield: TextView hook 失败: " + e.getMessage());
        }
    }

    private void initToastBlocker() {
        // 这里放入你原本已经测试成功的 Toast 拦截代码 (为了节省篇幅我精简了，请把之前的完整 Toast hook 贴在这里)
        try {
            XposedHelpers.findAndHookMethod(Toast.class, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    // ... 你的 Toast 拦截逻辑 ...
                    // 如果识别到断网 Toast -> param.setResult(null);
                }
            });
        } catch (Throwable ignored) {}
    }
}
