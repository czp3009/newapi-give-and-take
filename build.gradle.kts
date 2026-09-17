plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktorfit)
}

group = "com.hiczp"
version = "0.0.1"

kotlin {
    jvmToolchain(JavaVersion.VERSION_25.majorVersion.toInt())
    applyDefaultHierarchyTemplate()

    mingwX64()
    linuxArm64()
    linuxX64()
    macosArm64()
}
