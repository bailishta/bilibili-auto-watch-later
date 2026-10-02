# Room entities — kept by Room annotation processor automatically
# Data classes in data.remote are parsed manually (org.json), no reflection needed

# Tink 引用的 Error Prone 静态检查注解不参与 Android 运行时。
# 仅忽略这些可选注解，不关闭其他缺失类检查。
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
