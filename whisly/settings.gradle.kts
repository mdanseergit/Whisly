pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // Supply-chain risk: required only by the pinned
    // com.github.k2-fsa.sherpa-onnx:sherpa-onnx:1.13.5 AAR. Its JitPack POM declares
    // Apache-2.0 and SCM https://github.com/k2-fsa/sherpa-onnx.git (verified 2026-11-22).
    maven { url = uri("https://jitpack.io") }
  }
}

rootProject.name = "Whisly"

include(":app")
