// 根构建脚本：仅声明插件（apply false），具体应用到 :app 模块。
// 版本唯一来源见 gradle/libs.versions.toml。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
