plugins {
  id("quran.android.library.compose")
  id("app.cash.sqldelight")
  alias(libs.plugins.metro)
}

sqldelight {
  databases {
    create("HadithDatabase") {
      packageName.set("com.quran.mobile.feature.hadith.data")
    }
  }
}

android.namespace = "com.quran.mobile.feature.hadith"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":common:pages"))
  implementation(project(":common:ui:core"))

  implementation(libs.sqldelight.android.driver)

  // compose
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)

  // implementation but removed for release builds
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.ui.tooling)

  // immutable collections
  implementation(libs.kotlinx.collections.immutable)
}
