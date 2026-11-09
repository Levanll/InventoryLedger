# ===========================================================
# R8 / ProGuard 规则
#
# 原则：只保留「反射访问」和「序列化」必需的类，其余全部混淆。
# 不加过于宽泛的 -keep（如 -keep class * ），那等于没开混淆。
# ===========================================================

# ---------- Room ----------
# Room 生成的 _Impl 类通过反射加载，必须保留
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
# Room 用到的 Kotlin 协程返回类型
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>(...);
}
# 实体类字段被 Room 通过列名映射，字段名不能混淆
-keepclassmembers @androidx.room.Entity class * {
    <fields>;
}

# ---------- Compose ----------
# Compose 编译器生成大量合成方法，混淆后运行时按名字查找会失败
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ---------- Kotlin 协程 ----------
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ---------- ZXing ----------
# ZXing 内部有反射与枚举查找
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ---------- CameraX ----------
-dontwarn androidx.camera.**
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }

# ---------- 应用自身模型 ----------
# AppException 的 payload 通过字符串 key 取值，且错误码要跨层传递
-keep class com.example.inventory.domain.ModelsKt { *; }
-keep class com.example.inventory.domain.AppException { *; }
-keep class com.example.inventory.domain.ErrCode { *; }

# 数据类被 Room 投影映射，按构造参数名匹配
-keepclassmembers class com.example.inventory.data.dao.** {
    <fields>;
    <init>(...);
}

# ---------- 通用 ----------
# 保留行号，便于崩溃定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 移除发布版的日志
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
