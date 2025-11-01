pluginManagement {

	repositories {
		maven("https://jitpack.io")
		google()
		mavenCentral()
		gradlePluginPortal()
		mavenLocal()
	}

	plugins {

		// https://mvnrepository.com/artifact/com.android.tools.build/gradle
		id("com.android.application") version "8.13.0"
		id("com.android.library") version "8.13.0"

		// https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib
		kotlin("android") version "2.2.21"

		// https://mvnrepository.com/artifact/com.github.breadmoirai.github-release/com.github.breadmoirai.github-release.gradle.plugin
		id("com.github.breadmoirai.github-release") version "2.5.2"
	}
}

dependencyResolutionManagement {

	versionCatalogs {

		create("libs") {

			version("binaryWrapper.version", "1.0.27")

			version("elementum.versionCode", "99")

			version("lt2http.versionCode", "43")

			// https://developer.android.com/build/jdks
			version("jvmToolchain", "21")

			// https://developer.android.com/tools/releases/build-tools
			version("buildToolsVersion", "36.1.0")

			// https://developer.android.com/tools/releases/platforms
			version("compileSdk", "36")
			version("minSdk", "26")

			// https://developer.android.com/ndk/downloads
			version("ndkVersion", "29.0.14206865")

			// https://developer.android.com/ndk/guides/cmake
			version("cmake", "4.1.2")

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
