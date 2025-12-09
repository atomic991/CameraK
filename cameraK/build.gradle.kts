import org.jetbrains.compose.compose
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.library)
    id("org.jetbrains.compose")
    alias(libs.plugins.compose.compiler)
    id("com.vanniktech.maven.publish") version "0.34.0"
}

group = "com.kashif.camera_compose"
version = "1.2"

kotlin {
    jvmToolchain(17)
    androidTarget {
        publishLibraryVariants("release")
    }
    jvm("desktop")

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "cameraK"
            isStatic = true
        }
    }

    sourceSets {
        val desktopMain by getting{
            dependencies{
                api(libs.javacv.platform)
            }
        }

        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.coroutines.test)
            api(libs.kermit)
            api(compose.ui)
            api(compose.foundation)
            api(libs.coil3.compose)
            api(libs.coil3.ktor)
            api(libs.atomicfu)
        }

        commonTest.dependencies {
            api(kotlin("test"))

        }

        androidMain.dependencies {
            api(libs.kotlinx.coroutines.android)
            api(libs.camera.core)
            api(libs.camera.camera2)
            api(libs.androidx.camera.view)
            api(libs.camera.lifecycle)
            api(libs.camera.extensions)
            api(libs.androidx.activityCompose)
            api(libs.androidx.startup.runtime)
            api(libs.core)

        }

    }

    //https://kotlinlang.org/docs/native_objc_interop.html#export_of_kdoc_comments_to_generated_objective_c_headers
//    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
//        compilations["main"].compilerOptions.options.freeCompilerArgs.add("_Xexport_kdoc")
//    }

}

android {
    namespace = "com.kashif.cameraK"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }

    publishing {
        singleVariant("release") {
            withJavadocJar()
            withSourcesJar()
        }

        // For debug variant, we exclude Javadoc and sources to prevent conflicts
        singleVariant("debug") {
            // Exclude Javadoc and sources JARs for debug variant
        }
    }
}

mavenPublishing {

    coordinates(
        groupId = "hr.mathcode.atomic-kmp",
        artifactId = "camerak",
        version = "0.0.12"
    )

    pom {
        name.set("CameraK")
        description.set("Camera Library to work on both Android/iOS.")
        inceptionYear.set("2025")
        url.set("https://github.com/atomic991/CameraK")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("Aron")
                name.set("atomic")
                email.set("aron@mathcode.hr")
            }
        }

        scm {
            url.set("https://github.com/atomic991/CameraK")
        }
    }

    // Configure publishing to Maven Central
    publishToMavenCentral()

    // Enable GPG signing for all publications
    signAllPublications()
}
