import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "ir.pnifox.desktop.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "pnifox"
            packageVersion = "2.3.0"
            description = "pnifox — Multi-Engine Censorship Circumvention Client (AETHER, Xray, Sing-box, MHR, DNSTT)"
            vendor = "pnifox"
            includeAllModules = true

            appResourcesRootDir.set(project.layout.projectDirectory.dir("cores"))

            windows {
                menu = true
                shortcut = true
                perUserInstall = true
                menuGroup = "pnifox"
                dirChooser = true
                upgradeUuid = "9a1c4f0d-61b2-4e7a-9b25-8304c92a7b11"
                val ico = project.file("src/main/resources/pnifox_icon.ico")
                if (ico.exists()) {
                    iconFile.set(ico)
                }
            }
        }
    }
}
