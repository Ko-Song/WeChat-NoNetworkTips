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

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.tencent.mm")) return;

        XposedBridge.log("WeChatShield: 微信已加载，开始 Hook...");
        
        // 保持 Toast 拦截
        initToastBlocker();
        
        // 使用你在 MT 中找到的类名进行调试
        initDebugBannerBlocker(lpparam.classLoader);
    }

    private void initDebugBannerBlocker(ClassLoader cl) {
        String targetClass = "com.tencent.mm.ui.conversation.banner.k0";
        String targetMethod = "i";

        try {
            XposedHelpers.findAndHookMethod(targetClass, cl, targetMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log("WeChatShield: [调试] 命中了 " + targetClass + "." + targetMethod);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.getResult() instanceof Boolean && (Boolean) param.getResult()) {
                        param.setResult(false);
                        XposedBridge.log("WeChatShield: [Banner屏蔽] 成功拦截网络横幅");
                    }
                }
            });
            XposedBridge.log("WeChatShield: " + targetClass + " Hook 成功!");
        } catch (XposedHelpers.ClassNotFoundError e) {
            XposedBridge.log("WeChatShield: [错误] 找不到类 " + targetClass + "，请在 MT 中重新确认类名");
        } catch (NoSuchMethodError e) {
            XposedBridge.log("WeChatShield: [错误] 类存在但找不到方法 " + targetMethod + "，可能方法名已变");
        } catch (Throwable e) {
            XposedBridge.log("WeChatShield: [错误] Hook banner 失败: " + e.toString());
        }
    }

    private void initToastBlocker() {
        // ... (保持你之前的 Toast 代码不变)
    }
}
