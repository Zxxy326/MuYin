# QuickJS wrapper 通过 JNI 回调 Kotlin，类名/方法名不能被混淆或移除
-keep class com.whl.quickjs.** { *; }
-keep class wang.harlon.quickjs.** { *; }
-keepclassmembers class com.whl.quickjs.** { *; }

# SMB（jcifs-ng）与压缩包解析内部有反射/服务加载
-keep class jcifs.** { *; }
-keep class org.apache.commons.compress.** { *; }
-dontwarn jcifs.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.slf4j.**

# ZXing 二维码
-keep class com.google.zxing.** { *; }

# OkHttp / TLS 可选依赖
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class org.bouncycastle.jcajce.provider.** { *; }

# Media3 通过反射装配渲染器/提取器，保留其公共 API
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**

# 保留注解与泛型签名
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers enum * { *; }