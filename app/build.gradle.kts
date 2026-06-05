plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

val taggedVersion =
  providers
    .exec {
      isIgnoreExitValue = true
      commandLine("git", "describe", "--tags", "--abbrev=0")
    }.standardOutput.asText
    .map { it.trim().removePrefix("v") }

val appVersionName =
  (findProperty("appVersionName") as String?)
    ?: taggedVersion.orNull?.ifEmpty { null }
    ?: "0.0.0-dev"
val appVersionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 1

val commit = (findProperty("commit") as String?)?.takeIf { it.isNotBlank() }
val debugVersionSuffix = if (commit != null) "-debug-$commit" else "-debug"

android {
  namespace = "dev.thomasbuilds.spectre"
  compileSdk {
    version = release(37)
  }

  defaultConfig {
    applicationId = "dev.thomasbuilds.spectre"
    minSdk = 33
    targetSdk = 37
    versionCode = appVersionCode
    versionName = appVersionName
    vectorDrawables.useSupportLibrary = true
  }

  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
      versionNameSuffix = debugVersionSuffix
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
  }
  buildFeatures {
    compose = true
  }

  lint {
    checkAllWarnings = true
    disable += setOf("SyntheticAccessor", "LogConditional", "MemberExtensionConflict")
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
}

base {
  archivesName = "spectre"
}

kotlin {
  jvmToolchain(25)
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.process)
}
