plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "dev.thomasbuilds.spectre"
  compileSdk = 37

  defaultConfig {
    applicationId = "dev.thomasbuilds.spectre"
    minSdk = 31
    targetSdk = 37
    versionCode = 103999
    versionName = "0.1.3"
  }

  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
      versionNameSuffix = providers.gradleProperty("commit").map { "-debug-$it" }.getOrElse("-debug")
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      vcsInfo { include = false }
    }
  }

  androidResources {
    localeFilters += "en"
  }

  lint {
    checkAllWarnings = true
    warningsAsErrors = true
    checkReleaseBuilds = false
    disable += setOf("SyntheticAccessor", "LogConditional", "MemberExtensionConflict")
    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
  }

  dependenciesInfo {
    includeInApk = false
  }

  packaging {
    jniLibs {
      keepDebugSymbols += "**/*.so"
    }
  }
}

base {
  archivesName = "spectre"
}

tasks.named { it.contains("ArtProfile") }.configureEach { enabled = false }

kotlin {
  jvmToolchain(25)
  compilerOptions { allWarningsAsErrors = providers.environmentVariable("CI").isPresent }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.core)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.kotlinx.coroutines.android)
}
