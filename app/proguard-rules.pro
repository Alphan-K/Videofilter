# ===================================================================
# 基础运行时与平台规则
# ===================================================================
# 注意：-skipnonpubliclibraryclasses 是 ProGuard 旧选项，R8 已不再支持，添加会导致构建失败
-dontusemixedcaseclassnames
-verbose

# 保留调试属性，便于查看堆栈崩溃行号（生产定位核心）
-keepattributes SourceFile,LineNumberTable,EnclosingMethod,InnerClasses,Signature,*Annotation*

# ===================================================================
# 项目核心数据模型与枚举保护（重点保障格式/编码筛选器）
# ===================================================================
-keep class com.videofilter.app.model.** { *; }

-keepclassmembers enum com.videofilter.app.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static ** getEntries();
    public *;
}

# 保持扫描事件与密封接口结构
-keep interface com.videofilter.app.scanner.ScanEvent { *; }
-keep class com.videofilter.app.scanner.ScanEvent$* { *; }

# ===================================================================
# Room 持久化层保护
# ===================================================================
-keep class com.videofilter.app.data.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-dontwarn androidx.room.paging.**

# ===================================================================
# 原生多媒体与底层 Demuxer 调用保护
# ===================================================================
-keepclassmembers class android.media.MediaMetadataRetriever {
    public *;
}
-keepclassmembers class android.media.MediaExtractor {
    public *;
}
-keepclassmembers class android.media.MediaFormat {
    public *;
}

# ===================================================================
# Kotlin 协程与流式通信规则
# ===================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ===================================================================
# Jetpack Compose 与 Material 3 规则
# ===================================================================
-keep class androidx.compose.ui.platform.** { *; }
-keep class androidx.compose.material3.** { *; }
-dontwarn androidx.compose.**

# 保留 AndroidX 生命周期监听器
-keep class * implements androidx.lifecycle.LifecycleObserver { *; }
-keepclassmembers class * implements androidx.lifecycle.LifecycleObserver {
    <init>(...);
}
