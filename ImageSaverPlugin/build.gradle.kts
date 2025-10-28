plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.library)
    id("org.jetbrains.compose")
    alias(libs.plugins.compose.compiler)
    id("com.vanniktech.maven.publish") version "0.34.0"
}

group = "com.kashif.image_saver_plugin"
version = "1.0"

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
            baseName = "imagesaverplugin"
            isStatic = true
        }
    }

    sourceSets {
        val desktopMain by getting
        commonMain.dependencies {
            api(projects.cameraK)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))

        }

        androidMain.dependencies {

        }

    }

    //https://kotlinlang.org/docs/native_objc_interop.html#export_of_kdoc_comments_to_generated_objective_c_headers
//    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
//        compilations["main"].compilerOptions.options.freeCompilerArgs.add("_Xexport_kdoc")
//    }

}

android {
    namespace = "com.kashif.image_saver_plugin"
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
        artifactId = "image_saver_plugin",
        version = "0.0.6"
    )

    pom {
        name.set("ImageSaverPlugin")
        description.set("Image Saver Plugin for CameraK")
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