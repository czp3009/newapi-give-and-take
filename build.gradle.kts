import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.kotlinNativeNpmPublishing)
}

group = "com.hiczp"
version = BuildConfig.VERSION

tasks.register<PrintVersion>("printVersion")

val nativeHost = HostManager.host

kotlin {
    jvmToolchain(JavaVersion.VERSION_25.majorVersion.toInt())
    applyDefaultHierarchyTemplate()

    val mainEntryPoint = "com.hiczp.newapi.giveandtake.main"

    // The JVM target mainly exists to run the common tests quickly.
    jvm()
    mingwX64 {
        binaries.executable {
            entryPoint = mainEntryPoint
        }
    }
    linuxX64 {
        binaries.executable {
            entryPoint = mainEntryPoint
        }
    }
    macosArm64 {
        binaries.executable {
            entryPoint = mainEntryPoint
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.cli)
            implementation(libs.ktorfit.lib)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.json.io)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.io.core)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.slf4j.simple)
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

kotlinNativeNpmPublishing {
    packageName.set("@czp3009/newapi-give-and-take")
    description.set("Reward new-api users based on the usage of their contributed channels")
    license.set("MIT")
    repository.set("https://github.com/czp3009/newapi-give-and-take")
    homepage.set("https://github.com/czp3009/newapi-give-and-take")
    keywords.addAll("new-api", "channel", "quota", "reward", "kotlin-native", "npm", "npx")
    access.set("public")
    registry.set(providers.gradleProperty("npmRegistry"))
    otp.set(providers.gradleProperty("npmOtp"))

    stage {
        main {
            readme()
        }
    }
}

// Link only when both the target OS and architecture match the current host.
// Native dependencies such as the Curl engine need a compatible toolchain for final linking.
tasks.withType<KotlinNativeLink>().configureEach {
    if (binary.target.konanTarget != nativeHost) {
        onlyIf("the Kotlin/Native binary target matches the current host") { false }
    }
}
