// 应用模块构建脚本：仅依赖 AndroidX 基础库，无第三方依赖
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alex.mathmole"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alex.mathmole"
        minSdk = 26          // 面向较新设备，可直接使用自适应图标等特性
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        // 使用 AGP 内置的视图绑定，避免大量 findViewById 样板代码（非第三方依赖）
        viewBinding = true
    }
}

dependencies {
    // 仅 AndroidX 基础库
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    // 本地单元测试（仅用于纯逻辑校验，不进入安装包）
    testImplementation("junit:junit:4.13.2")
}
