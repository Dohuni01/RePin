import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// 서명 자격증명은 local.properties(git 제외)에서 읽는다. 없으면 release 서명은 건너뛴다.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
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

    signingConfigs {
        val storeFilePath = localProps.getProperty("RELEASE_STORE_FILE")
        if (storeFilePath != null) {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
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
