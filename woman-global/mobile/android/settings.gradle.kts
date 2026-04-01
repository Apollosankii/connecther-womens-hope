pluginManagement {
    repositories {
        // Mirrors first: use when dl.google.com / repo.maven.apache.org do not resolve (DNS, firewall, region).
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
            content {
                // Kotlin Gradle plugin: prefer repo1/mavenCentral (Aliyun can confuse cache/locks on Windows).
                excludeGroupByRegex("org\\.jetbrains\\.kotlin")
            }
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/gradle-plugin")
            content {
                excludeGroupByRegex("org\\.jetbrains\\.kotlin")
            }
        }

        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven { url = uri("https://repo1.maven.org/maven2/") }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
            content {
                // Aliyun often lacks or lags AARs for these; resolve from repo1 / mavenCentral below.
                excludeGroupByRegex("com\\.paystack\\.android")
                excludeGroupByRegex("io\\.github\\.jan-tennert\\.supabase")
                excludeGroupByRegex("io\\.ktor")
                excludeGroupByRegex("org\\.jetbrains\\.kotlin")
            }
        }
        google()
        maven { url = uri("https://repo1.maven.org/maven2/") }
        mavenCentral()
    }
}

rootProject.name = "ConnectHer"
include(":app")
