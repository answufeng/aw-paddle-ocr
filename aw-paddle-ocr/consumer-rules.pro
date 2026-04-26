# 合併到依賴應用之 R8/ProGuard
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.answufeng.paddleocr.** { *; }
