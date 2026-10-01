import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
}

group = "com.hiczp"
version = "0.0.1"

val nativeHost = HostManager.host

kotlin {
    jvmToolchain(JavaVersion.VERSION_25.majorVersion.toInt())
    applyDefaultHierarchyTemplate()

    val mainEntryPoint = "com.hiczp.newapi.giveandtake.main"

    // The JVM target mainly exists to run the common tests quickly.
    jvm()
    mingwX64 { binaries.executable { entryPoint = mainEntryPoint } }
    linuxArm64 { binaries.executable { entryPoint = mainEntryPoint } }
    linuxX64 { binaries.executable { entryPoint = mainEntryPoint } }
    macosArm64 { binaries.executable { entryPoint = mainEntryPoint } }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktorfit.lib)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        nativeMain.dependencies {
            implementation(libs.ktor.client.curl)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Cross-host KLIB compilation is portable, but final linking can consume target-native libraries (for example the
// Ktor Curl engine) whose toolchains are incompatible with the current host. Final binaries are therefore built on
// their own host.
tasks.withType<KotlinNativeLink>().configureEach {
    if (binary.target.konanTarget != nativeHost) {
        onlyIf("the Kotlin/Native binary target matches the current host") { false }
    }
}
