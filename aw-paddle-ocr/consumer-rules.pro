# 合併到依賴應用之 R8/ProGuard
-keepclasseswithmembernames class * {
    native <methods>;
}

# 公開 API 與 JNI 入口（避免整包 `com.answufeng.paddleocr.**` 過寬）
-keep class com.answufeng.paddleocr.AwPaddleOcr { *; }
-keep class com.answufeng.paddleocr.AwPaddleOcr$OcrConfig { *; }
-keep class com.answufeng.paddleocr.PPOCRv5Engine { *; }
-keep class com.answufeng.paddleocr.OcrTextBlock { *; }
-keep class com.answufeng.paddleocr.OcrResult { *; }
-keep class com.answufeng.paddleocr.OcrResult$* { *; }
-keep class com.answufeng.paddleocr.TextBlock { *; }
