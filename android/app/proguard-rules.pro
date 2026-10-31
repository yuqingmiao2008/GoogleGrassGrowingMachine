-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

-keepattributes Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-keepclassmembers class * implements java.io.Serializable { *; }
# OkHttp / Okio
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# 我们的数据类只做手工 JSON 解析，无需反射保留
