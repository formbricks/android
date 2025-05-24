-keep class com.formbricks.android.DataBinderMapperImpl { *; }
-keep class com.formbricks.android.Formbricks { *; }
-keep class com.formbricks.android.helper.FormbricksConfig { *; }
-keep class com.formbricks.android.model.error.SDKError { *; }
-keep interface com.formbricks.android.FormbricksCallback { *; }
-keep class com.android.org.conscrypt.** { *; }
-keep class javax.annotation.** { *; }
-keep class org.apache.harmony.xnet.provider.jsse.** { *; }
# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn java.lang.invoke.StringConcatFactory