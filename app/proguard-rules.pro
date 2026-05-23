# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# AIDL服务相关规则 - Shizuku需要通过ComponentName绑定
-keep class com.shutiao.flow.UserService { *; }
-keep class com.shutiao.flow.IUserService { *; }
-keep class com.shutiao.flow.IUserService$* { *; }
-keep interface com.shutiao.flow.IUserService { *; }
