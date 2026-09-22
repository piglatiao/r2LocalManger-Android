# 一期不混淆（降低风险），保留规则仅作占位与后续可扩展。
# 保留 OkHttp / 自实现 S3 相关类型（如启用混淆时）：
-keep class com.r2manager.android.data.remote.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# 保留 Kotlin 元数据
-keep class kotlin.Metadata { *; }

# 移除日志（release 由 AppLog 内部控制，不依赖 ProGuard）
# -assumenosideeffects class android.util.Log { *; }
