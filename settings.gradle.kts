pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        
        // JitPack for sherpa-onnx (may take 2-5 minutes on first build)
        maven { 
            url = uri("https://jitpack.io")
            content {
                // Only use JitPack for sherpa-onnx to improve resolution speed
                includeGroup("com.github.k2-fsa.sherpa-onnx")
            }
        }
    }
    
    // Improve dependency resolution performance
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    
    // Add resolution strategy for sherpa-onnx
    versionCatalogs {
        create("libs") {
            version("sherpa-onnx", "v1.13.8")
        }
    }
}
rootProject.name = "iTantraAndroidSmoke"
include(":app")
