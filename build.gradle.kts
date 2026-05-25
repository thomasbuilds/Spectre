// Top-level build file. Shared configuration for all modules lives here.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.spotless)
}

spotless {
  kotlin {
    target("**/*.kt")
    targetExclude("**/build/**")
    ktlint()
    trimTrailingWhitespace()
    endWithNewline()
  }
  kotlinGradle {
    target("**/*.gradle.kts")
    targetExclude("**/build/**")
    ktlint()
    trimTrailingWhitespace()
    endWithNewline()
  }
  format("xml") {
    target("**/src/**/*.xml")
    targetExclude("**/build/**")
    trimTrailingWhitespace()
    endWithNewline()
  }
}
