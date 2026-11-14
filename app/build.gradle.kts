plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.akkageugot.demo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.akkageugot.demo"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "engine"
    productFlavors {
        create("fake") {
            dimension = "engine"
            buildConfigField("boolean", "USE_FAKE_ENGINE", "true")
        }
        create("real") {
            dimension = "engine"
            buildConfigField("boolean", "USE_FAKE_ENGINE", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":contract"))
    implementation(project(":engine"))
    implementation(project(":ui"))
    implementation(libs.androidx.core.ktx)
}
