package com.example.wechatshield;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String KEYWORD = "当前无法连接网络";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.processName.equals("com.tencent.mm")) return;

        initToastBlocker();
        initBannerBlocker(lpparam.classLoader);
    }

    private void initBannerBlocker(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(TextView.class, "setText", CharSequence.class, TextView.BufferType.class, boolean.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    CharSequence text = (CharSequence) param.args[0];
                    if (text != null && text.toString().contains(KEYWORD)) {
                        TextView tv = (TextView) param.thisObject;
                        tv.setVisibility(View.GONE);

                        ViewParent parent = tv.getParent();
                        for (int i = 0; i < 4; i++) {
                            if (parent instanceof View) {
                                View pView = (View) parent;
                                pView.setVisibility(View.GONE);
                                
                                ViewGroup.LayoutParams params = pView.getLayoutParams();
                                if (params != null) {
                                    params.height = 0;
                                    pView.setLayoutParams(params);
                                }
                                parent = pView.getParent();
                            } else {
                                break;
                            }
                        }
                        
                        param.setResult(null);
                        XposedBridge.log("WeChatShield: [横幅拦截] 成功清除无网络横幅及父容器底板，匹配文本: " + text);
                    }
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("WeChatShield:横幅 Hook 异常: " + e.getMessage());
        }
    }

    private void initToastBlocker() {
        try {
            XposedHelpers.findAndHookMethod(Toast.class, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(null);
                    XposedBridge.log("WeChatShield: [Toast拦截] 无差别拦截并阻止了一次微信 Toast 弹出");
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("WeChatShield: Toast Hook 异常: " + e.getMessage());
        }
    }
}
