package io.github.cheng343.calculator.easteregg;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

final class LauncherIcon {
    private LauncherIcon() {}

    private static ComponentName alias(Context context) {
        return new ComponentName(context.getPackageName(),
                context.getPackageName() + ".LauncherAlias");
    }

    static boolean isHidden(Context context) {
        int state = context.getPackageManager().getComponentEnabledSetting(alias(context));
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    static void setHidden(Context context, boolean hidden) {
        // Disable only the launcher alias. MainActivity remains the LSPosed settings entry.
        context.getPackageManager().setComponentEnabledSetting(alias(context),
                hidden ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }
}
