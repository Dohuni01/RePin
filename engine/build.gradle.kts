plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.akkageugot.demo.engine"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
    }

    useLibrary("android.car")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    api(project(":contract"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Pleos Connect SDK — 전부 인터페이스 뒤에 두고 목업과 함께 쓴다 (docs/PLEOS_SDK_NOTES.md)
    implementation(libs.pleos.speech.to.text)
    implementation(libs.pleos.text.to.speech)
    implementation(libs.pleos.navi.helper)
    implementation(libs.pleos.vehicle)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
