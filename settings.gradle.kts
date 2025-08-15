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
		id("com.android.application") version "8.12.0"
		id("com.android.library") version "8.12.0"

		// https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib
		kotlin("android") version "2.2.10"

		// https://mvnrepository.com/artifact/com.github.breadmoirai.github-release/com.github.breadmoirai.github-release.gradle.plugin
		id("com.github.breadmoirai.github-release") version "2.5.2"
	}
}

dependencyResolutionManagement {

	versionCatalogs {

		create("libs") {

			version("binaryWrapper.version", "1.0.26")

			version("elementum.versionCode", "98")

			version("lt2http.versionCode", "43")

			// https://developer.android.com/build/jdks
			version("jvmToolchain", "21")

			// https://developer.android.com/tools/releases/build-tools
			version("buildToolsVersion", "36.0.0")

			// https://developer.android.com/tools/releases/platforms
			version("compileSdk", "36")
			version("minSdk", "26")

			// https://developer.android.com/ndk/downloads
			version("ndkVersion", "28.2.13676358")

			// https://developer.android.com/ndk/guides/cmake
			version("cmake", "4.1.0")

			// https://developer.android.com/ndk/guides/cpp-support
			version("cpp", "17")

			// https://mvnrepository.com/artifact/androidx.test/runner
			library("androidTest.runner", "androidx.test:runner:1.7.0")

			// https://mvnrepository.com/artifact/androidx.test.ext/junit-ktx
			library("androidTest.junit", "androidx.test.ext:junit-ktx:1.3.0")

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
