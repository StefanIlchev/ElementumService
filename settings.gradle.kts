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
		id("com.android.application") version "8.7.0"
		id("com.android.library") version "8.7.0"

		// https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib
		kotlin("android") version "2.0.20"

		// https://plugins.gradle.org/plugin/com.github.breadmoirai.github-release
		id("com.github.breadmoirai.github-release") version "2.5.2"
	}
}

dependencyResolutionManagement {

	versionCatalogs {

		create("libs") {

			version("binaryWrapper.version", "1.0.24")

			version("elementum.versionCode", "89")

			version("lt2http.versionCode", "41")

			// https://developer.android.com/build/jdks
			version("jvmToolchain", "21")

			// https://developer.android.com/tools/releases/build-tools
			version("buildToolsVersion", "35.0.0")

			// https://developer.android.com/tools/releases/platforms
			version("compileSdk", "34")
			version("minSdk", "26")

			// https://developer.android.com/ndk/downloads
			version("ndkVersion", "27.1.12297006")

			// https://developer.android.com/ndk/guides/cmake
			version("cmake", "3.30.3")

			// https://isocpp.org/std/the-standard
			version("cpp", "20")

			// https://mvnrepository.com/artifact/androidx.test/runner
			library("androidTest.runner", "androidx.test:runner:1.6.2")

			// https://mvnrepository.com/artifact/androidx.test.ext/junit-ktx
			library("androidTest.junit", "androidx.test.ext:junit-ktx:1.2.1")

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
