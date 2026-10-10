package io.github.cheng343.calculator.easteregg;

import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.util.Log;
import android.view.View;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public final class ModuleMain extends XposedModule {
    private static final String TARGET = "com.coloros.calculator";
    private static final String FRAGMENT = "com.android.calculator2.ui.fragment.CalculatorFragment";
    private static final String TAG = "OnePlusEasterEgg";
    private final List<XposedInterface.HookHandle> handles = new ArrayList<>();
    private boolean mainProcess;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        mainProcess = TARGET.equals(param.getProcessName());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!mainProcess || !TARGET.equals(param.getPackageName()) || !handles.isEmpty()) return;
        try {
            ClassLoader loader = param.getClassLoader();
            Class<?> fragmentClass = Class.forName(FRAGMENT, false, loader);
            EasterEggController controller = new EasterEggController(loader, moduleApks(),
                    (message, error) -> {
                        if (error == null) log(Log.INFO, TAG, message);
                        else log(Log.ERROR, TAG, message, error);
                    });
            Method click = Reflect.method(fragmentClass, "onClick", View.class);
            handles.add(configuredHook(click, "calculator-easter-egg-click")
                    .intercept(chain -> {
                        Object argument = chain.getArg(0);
                        if (argument instanceof View && controller.onClick(
                                chain.getThisObject(), (View) argument)) return null;
                        return chain.proceed();
                    }));
            installCleanup(fragmentClass, "onPause", controller);
            installCleanup(fragmentClass, "onDestroyView", controller);
            installCleanup(fragmentClass, "onConfigurationChanged", controller, Configuration.class);
            log(Log.INFO, TAG, "Modern API " + getApiVersion() + " hooks installed for " + TARGET);
        } catch (Throwable error) {
            for (XposedInterface.HookHandle handle : handles) handle.unhook();
            handles.clear();
            log(Log.ERROR, TAG, "Could not install calculator hooks", error);
        }
    }

    /**
     * Module APK paths, used to read the bundled never_settle animations. The
     * calculator 17.2.16 no longer ships them, so the module carries its own copy
     * and falls back to the host assets only while they still exist.
     */
    private List<String> moduleApks() {
        List<String> paths = new ArrayList<>();
        try {
            ApplicationInfo info = getModuleApplicationInfo();
            if (info != null) {
                if (info.sourceDir != null && !info.sourceDir.isEmpty()) paths.add(info.sourceDir);
                if (info.splitSourceDirs != null) {
                    for (String split : info.splitSourceDirs) {
                        if (split != null && !split.isEmpty()) paths.add(split);
                    }
                }
            }
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Could not resolve the module APK path", error);
        }
        if (paths.isEmpty()) log(Log.WARN, TAG, "No module APK path; using host assets only");
        return paths;
    }

    private void installCleanup(Class<?> owner, String name, EasterEggController controller,
                                Class<?>... parameters) throws NoSuchMethodException {
        handles.add(configuredHook(Reflect.method(owner, name, parameters), "calculator-easter-egg-" + name)
                .intercept(chain -> {
                    controller.dismiss(chain.getThisObject());
                    return chain.proceed();
                }));
    }

    private XposedInterface.HookBuilder configuredHook(Method method, String id) {
        XposedInterface.HookBuilder builder = hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE);
        if (getApiVersion() >= 102) {
            // API 101 has the same interceptor/lifecycle contract but has no setId method.
            // Reflecting this optional method avoids linking a 102-only method on 101.
            try {
                Object configured = Reflect.method(XposedInterface.HookBuilder.class,
                        "setId", String.class).invoke(builder, id);
                if (configured instanceof XposedInterface.HookBuilder)
                    builder = (XposedInterface.HookBuilder) configured;
            } catch (ReflectiveOperationException error) {
                log(Log.WARN, TAG, "Optional Hook ID unavailable", error);
            }
        }
        return builder;
    }
}
