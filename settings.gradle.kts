pluginManagement {

	repositories {
		google()
		mavenCentral()
		mavenLocal()
		maven("https://jitpack.io")
		gradlePluginPortal()
	}

	plugins {

		// https://mvnrepository.com/artifact/com.android.tools.build/gradle
		id("com.android.application") version "8.4.0"
		id("com.android.library") version "8.4.0"

		// https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib
		kotlin("android") version "1.9.24"

		// https://plugins.gradle.org/plugin/com.github.breadmoirai.github-release
		id("com.github.breadmoirai.github-release") version "2.5.2"
	}
}

dependencyResolutionManagement {

	versionCatalogs {

		create("libs") {

			version("binaryWrapper.version", "1.0.23")

			version("elementum.versionCode", "88")

			version("lt2http.versionCode", "40")

			// https://developer.android.com/build/jdks
			version("jvmToolchain", "21")

			// https://developer.android.com/tools/releases/build-tools
			version("buildToolsVersion", "34.0.0")

			// https://developer.android.com/tools/releases/platforms
			version("compileSdk", "34")
			version("minSdk", "26")

			// https://developer.android.com/ndk/downloads
			version("ndkVersion", "26.3.11579264")

			// https://developer.android.com/ndk/guides/cmake
			version("cmake", "3.22.1")

			// https://mvnrepository.com/artifact/androidx.test/runner
			library("androidTest.runner", "androidx.test:runner:1.5.2")

			// https://mvnrepository.com/artifact/androidx.test.ext/junit-ktx
			library("androidTest.junit", "androidx.test.ext:junit-ktx:1.1.5")

			// https://mvnrepository.com/artifact/junit/junit
			library("test.junit", "junit:junit:4.13.2")
		}
	}
}

include(
	":binaryWrapper",
	":ElementumService",
	":Lt2httpService"
)
