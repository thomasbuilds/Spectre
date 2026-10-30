plugins {
  alias(libs.plugins.spotless)
}

spotless {
  kotlin {
    target("**/*.kt", "**/*.gradle.kts")
    ktlint()
  }
  format("misc") {
    target("**/*.md", "**/*.yml", "**/*.toml", "**/*.pro", "**/src/**/*.xml", "gradle.properties", "metadata/**/*.txt", "release")
    trimTrailingWhitespace()
    endWithNewline()
  }
}
