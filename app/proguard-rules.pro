# Framework loads this exact class name from META-INF/xposed/java_init.list.
-keep class io.github.cheng343.calculator.easteregg.ModuleMain { *; }

# Keep the two renderer class names used in the GitHub APK metadata check.
# Unused methods and dependencies can still be removed.
-keep,allowoptimization class com.airbnb.lottie.LottieCompositionFactory {
    public static com.airbnb.lottie.LottieTask fromJsonInputStream(java.io.InputStream, java.lang.String);
}
-keep,allowoptimization class com.airbnb.lottie.LottieDrawable {
    public <init>();
}

-keepattributes SourceFile,LineNumberTable
