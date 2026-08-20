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
    // sherpa-onnx publishes prebuilt Android AARs only as GitHub Release assets, not to
    // Maven Central/JitPack proper. This mirrors the exact mechanism the upstream project's
    // own jitpack.yml uses (fetch the release asset, treat it as the artifact) but resolves
    // it directly from GitHub Releases instead of routing through JitPack's build service.
    // See docs/AI_ARCHITECTURE.md "sherpa-onnx Android integration" for details.
    ivy {
      url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download")
      patternLayout {
        artifact("v[revision]/[module]-[revision].[ext]")
      }
      metadataSources { artifact() }
      content { includeGroup("k2-fsa") }
    }
  }
}

rootProject.name = "MeetMind"

include(":app")
