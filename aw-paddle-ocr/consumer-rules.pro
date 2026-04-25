# aw-paddle-ocr consumer ProGuard rules

-keepclassmembers class com.answufeng.paddleocr.AwPaddleOcr {
    public *** init(...);
    public *** detect(...);
    public *** detectSync(...);
    public boolean isInitialized();
}

-keepclassmembers class com.answufeng.paddleocr.AwPaddleOcr$OcrConfig {
    public *** padding(...);
    public *** maxSideLen(...);
    public *** boxScoreThresh(...);
    public *** boxThresh(...);
    public *** unClipRatio(...);
    public *** doAngle(...);
    public *** mostAngle(...);
}

-keepclassmembers class com.answufeng.paddleocr.OcrResult {
    public *** getStrRes(...);
    public *** getTextBlocks(...);
    public *** getDetectTime(...);
    public *** getBoxImg(...);
}

-keepclassmembers class com.answufeng.paddleocr.TextBlock {
    public *** getText(...);
    public *** getBoxPoint(...);
    public *** getScore(...);
    public *** getClsLabel(...);
    public *** getClsConfidence(...);
}

-keep class com.benjaminwan.ocrlibrary.OcrEngine { *; }
-keep class com.benjaminwan.ocrlibrary.OcrResult { *; }
-keep class com.benjaminwan.ocrlibrary.TextBlock { *; }

-dontwarn com.benjaminwan.ocrlibrary.**
