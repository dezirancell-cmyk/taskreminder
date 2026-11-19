import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.taskreminder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.taskreminder"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "2.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
}

// دانلود فونت وزیرمتن موقع ساخت (اگر دانلود نشد، اپ با فونت پیش‌فرض کار می‌کند)
val downloadFonts by tasks.registering {
    doLast {
        val dir = file("src/main/assets/fonts")
        dir.mkdirs()
        val sources = mapOf(
            "Vazirmatn-Regular.ttf" to listOf(
                "https://cdn.jsdelivr.net/npm/vazirmatn@33.0.3/fonts/ttf/Vazirmatn-Regular.ttf",
                "https://github.com/rastikerdar/vazirmatn/raw/v33.003/fonts/ttf/Vazirmatn-Regular.ttf",
                "https://cdn.jsdelivr.net/gh/rastikerdar/vazirmatn@master/fonts/ttf/Vazirmatn-Regular.ttf"
            ),
            "Vazirmatn-Bold.ttf" to listOf(
                "https://cdn.jsdelivr.net/npm/vazirmatn@33.0.3/fonts/ttf/Vazirmatn-Bold.ttf",
                "https://github.com/rastikerdar/vazirmatn/raw/v33.003/fonts/ttf/Vazirmatn-Bold.ttf",
                "https://cdn.jsdelivr.net/gh/rastikerdar/vazirmatn@master/fonts/ttf/Vazirmatn-Bold.ttf"
            )
        )
        sources.forEach { (name, urls) ->
            val target = File(dir, name)
            if (target.exists() && target.length() > 20_000) return@forEach
            for (u in urls) {
                try {
                    val tmp = File(dir, "$name.tmp")
                    URL(u).openStream().use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
                    val head = tmp.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
                    val isTtf = head[0] == 0.toByte() && head[1] == 1.toByte() && head[2] == 0.toByte() && head[3] == 0.toByte()
                    if (tmp.length() > 20_000 && isTtf) {
                        tmp.renameTo(target)
                        println("Font downloaded: $name")
                        break
                    } else {
                        tmp.delete()
                    }
                } catch (e: Exception) {
                    println("Font download failed from $u: ${e.message}")
                }
            }
        }
    }
}

tasks.configureEach {
    if (name == "preBuild") dependsOn(downloadFonts)
}
