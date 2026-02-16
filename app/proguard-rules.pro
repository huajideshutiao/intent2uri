# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Shizuku相关规则
#-keep class rikka.shizuku.** { *; }
#-dontwarn rikka.shizuku.**
#-keep interface rikka.shizuku.** { *; }

# AIDL服务相关规则 - Shizuku需要通过ComponentName绑定
-keep class com.shutiao.flow.UserService { *; }
-keep class com.shutiao.flow.IUserService { *; }
-keep class com.shutiao.flow.IUserService$* { *; }
-keep interface com.shutiao.flow.IUserService { *; }

# 数据库相关数据类 - 字段名不能混淆
#-keep class com.shutiao.flow.OpenLink { *; }
#-keepclassmembers class com.shutiao.flow.OpenLink { *; }

# DbHelper单例 - 需要保持不被混淆
#-keep class com.shutiao.flow.DbHelper { *; }
#-keepclassmembers class com.shutiao.flow.DbHelper { *; }