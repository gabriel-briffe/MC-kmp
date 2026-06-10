import org.gradle.api.tasks.Copy
import io.github.frankois944.spmForKmp.swiftPackageConfig
import java.net.URI

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("io.github.frankois944.spmForKmp") version "1.9.1"
}

kotlin {
    androidTarget()

    swiftPackageConfig {
        create("spmMaplibre") {
            dependency {
                remotePackageVersion(
                    url = URI("https://github.com/maplibre/maplibre-gl-native-distribution.git"),
                    products = { add("MapLibre", exportToKotlin = true) },
                    packageName = "maplibre-gl-native-distribution",
                    version = project.properties["maplibreIosVersion"]!!.toString(),
                )
            }
        }
    }

    iosSimulatorArm64().apply {
        binaries.framework {
            baseName = "composeApp"
        }
        val rpath =
            "${layout.buildDirectory.get()}/spmKmpPlugin/iosSimulatorArm64/scratch/arm64-apple-ios-simulator/release/"
        binaries.all { linkerOpts("-F$rpath", "-rpath", rpath) }
    }

    tasks.register("embedAndSignPodAppleFrameworkForXcode") {
        dependsOn("embedAndSignAppleFrameworkForXcode")
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/compose/resourceGenerator/kotlin/commonResClass"))
            kotlin.srcDir(layout.buildDirectory.dir("generated/compose/resourceGenerator/kotlin/commonMainResourceAccessors"))
            dependencies {
                implementation(compose.runtime)
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)
                implementation(libs.maplibre.compose)
                implementation(libs.spatialk.geojson)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.21")
                implementation("io.ktor:ktor-client-core:2.3.12")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
            }
        }
        androidMain {
            dependencies {
                implementation(libs.maplibre.android)
                implementation("androidx.activity:activity-compose:1.9.1")
                implementation("io.ktor:ktor-client-okhttp:2.3.12")
                implementation("androidx.datastore:datastore-preferences:1.1.1")
                implementation("androidx.work:work-runtime-ktx:2.9.0")
                implementation("androidx.lifecycle:lifecycle-process:2.8.4")
                // Glance for modern app widgets
                implementation("androidx.glance:glance-appwidget:1.1.1")
            }
        }
        iosMain {
            dependencies {
                implementation("io.ktor:ktor-client-darwin:2.3.12")
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        androidUnitTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("androidx.test:core:1.5.0")
                implementation("androidx.test.ext:junit:1.1.5")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}

compose.resources {
    packageOfResClass = "mountaincircles.composeapp.generated.resources"
}

// Copy fonts to iOS app bundle
tasks.register<Copy>("copyFontsToIosApp") {
    from(layout.projectDirectory.dir("../iosApp/iosApp/assets/fonts"))
    into(layout.projectDirectory.dir("../iosApp/iosApp/fonts"))
}

// Task to copy font assets to iOS bundle after framework build
tasks.register<Copy>("copyFontsToIosBundle") {
    from(layout.projectDirectory.dir("../iosApp/iosApp/assets/fonts"))
    into(layout.projectDirectory.dir("../iosApp/build/Build/Products/Debug-iphonesimulator/Mountain Circles.app/fonts"))
    dependsOn("linkDebugFrameworkIosSimulatorArm64")
}

tasks.named("linkDebugFrameworkIosSimulatorArm64") {
    finalizedBy("copyFontsToIosBundle")
}

// Task to convert SVG files to Android drawable XML format
tasks.register<Copy>("convertSvgToAndroidDrawables") {
    from(layout.projectDirectory.dir("src/commonMain/composeResources/drawable")) {
        include("*.svg")
    }
    into(layout.projectDirectory.dir("src/androidMain/res/drawable"))

    // Convert .svg extension to .xml and fix naming
    eachFile {
        var newName = name.replace(".svg", ".xml")
        // Android resource names cannot start with numbers
        if (newName.matches(Regex("^\\d.*"))) {
            newName = "wb_$newName"
        }
        path = path.replace(name, newName)
    }

    // Transform SVG content to Android vector format
    filter { line ->
        when {
            line.contains("<svg") -> {
                // Extract viewBox from SVG - viewBox="x y width height"
                val viewBoxMatch = Regex("viewBox=\"([\\d\\s]+)\"").find(line)
                val viewBoxValues = viewBoxMatch?.groupValues?.get(1)?.split("\\s+".toRegex())
                val width = viewBoxValues?.get(2) ?: "250"
                val height = viewBoxValues?.get(3) ?: "250"

                """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:viewportWidth="$width"
    android:viewportHeight="$height"
    android:width="${width}dp"
    android:height="${height}dp">"""
            }
            line.contains("</svg>") -> "</vector>"
            line.contains("<style") -> "" // Remove CSS style start
            line.contains("</style>") -> "" // Remove CSS style end
            line.contains(".svg-wb") -> "" // Remove CSS class definition
            line.contains("<path") -> {
                // Convert SVG path to Android path with white color
                var result = line.replace("<path", "<path android:fillColor=\"#FFFFFF\" android:strokeColor=\"#FFFFFF\"")
                    .replace("class=\"[^\"]*\"", "") // Remove CSS class references
                    .replace("d=", "android:pathData=") // Convert SVG d attribute to Android pathData

                // Remove any remaining class attributes with a more comprehensive regex
                result = Regex("class=\"[^\"]*\"").replace(result, "")

                // Clean up extra spaces
                result = Regex("\\s+").replace(result, " ")

                // Add Android attributes if this is a self-closing tag
                if (result.contains("/>")) {
                    result = result.replace("/>", " android:strokeWidth=\"3\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" android:strokeMiterLimit=\"10\" />")
                }
                result
            }
            // Skip empty lines and other SVG elements we don't need
            line.trim().isEmpty() -> ""
            line.contains("<?xml") -> ""
            else -> line
        }
    }
}

// Wind barb icons are now in version control in src/androidMain/res/drawable/

android {
    namespace = "org.mountaincircles.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.mountaincircles.app"
        minSdk = 24
        targetSdk = 35
        // REMINDER: When updating versionCode/versionName, also update MainMenuComposable.kt
        versionCode = 58
        versionName = "1.1.47"

        // Widget salt for cache invalidation - MANUALLY increment this value when widgets are updated
        // Example: "1" → "2" → "3" → "4" → "5" etc. (only when you make widget changes)
        manifestPlaceholders["widgetSalt"] = "15"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}