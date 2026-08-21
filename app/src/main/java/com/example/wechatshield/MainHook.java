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
    private static boolean isPcHooked = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.processName.equals("com.tencent.mm")) return;

        // 1. 保持已经成功生效的 Toast 拦截
        initToastBlocker();

        // 2. 通过动态监听 ClassLoader 来安全挂载目标类（解决多DEX找不到类的问题）
        initDynamicClassHook(lpparam.classLoader);
    }

    private void initDynamicClassHook(ClassLoader baseCl) {
        try {
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass", String.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String className = (String) param.args[0];
                    if ("com.tencent.mm.ui.pc".equals(className)) {
                        Class<?> clazz = (Class<?>) param.getResult();
                        if (clazz != null && !isPcHooked) {
                            isPcHooked = true;
                            XposedBridge.log("WeChatShield: [动态捕捉] 成功捕获到延迟加载的目标类: com.tencent.mm.ui.pc");
                            hookErrorProcessor(clazz);
                        }
                    }
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("WeChatShield: 动态 ClassLoader 监听失败: " + e.getMessage());
        }
    }

    private void hookErrorProcessor(Class<?> clazz) {
        try {
            // 精准 Hook 静态方法 a
            XposedHelpers.findAndHookMethod(clazz, "a", 
                android.content.Context.class, int.class, int.class, String.class, int.class, 
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        int errType = (Integer) param.args[1];
                        int errCode = (Integer) param.args[2];
                        String extra = (String) param.args[3];
                        
                        XposedBridge.log("WeChatShield: [错误分发捕获] 拦截到错误下发 -> errType=" + errType + ", errCode=" + errCode + ", info=" + extra);
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        // 如果想直接阻断该方法底层的弹窗逻辑，可以取消下面这行的注释
                        // param.setResult(true);
                    }
                });
            XposedBridge.log("WeChatShield: [静态逆向] com.tencent.mm.ui.pc.a 方法挂载成功！");
        } catch (Throwable e) {
            XposedBridge.log("WeChatShield: com.tencent.mm.ui.pc.a 方法挂载异常: " + e.getMessage());
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

                    // 针对网络连接提示进行专属屏蔽，其他正常 Toast 放行（如果你想全屏屏蔽可以保留 param.setResult(null)）
                    if (content != null && content.contains("当前无法连接网络")) {
                        param.setResult(null);
                        XposedBridge.log("WeChatShield: [精准Toast屏蔽] #" + id + " 已拦截网络提示: " + content);
                    }
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
