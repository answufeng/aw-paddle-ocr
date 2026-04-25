# aw-paddle-ocr ProGuard Rules

-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes Exceptions
-keep class kotlin.Metadata { *; }
-keep class kotlin.coroutines.jvm.internal.BaseContinuationImpl { *; }

-keepclassmembers class ** {
    @kotlin.coroutines.jvm.internal.DebugMetadata *;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class com.benjaminwan.ocrlibrary.** { *; }

-dontwarn okio.**
-dontwarn org.codehaus.mojo.**
