plugins {
	id("com.android.library")
	kotlin("android")
	id("maven-publish")
}

kotlin {
	jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

android {
	buildToolsVersion = libs.versions.buildToolsVersion.get()
	compileSdk = libs.versions.compileSdk.get().toInt()
	namespace = "stef40.binarywrapper"

	buildFeatures {
		buildConfig = true
	}

	defaultConfig {
		minSdk = libs.versions.minSdk.get().toInt()

		aarMetadata {
			minCompileSdk = minSdk
		}
	}

	buildTypes {

		named("release") {
			isMinifyEnabled = false
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
		}
	}

	publishing {

		singleVariant("release") {
			withSourcesJar()
		}
	}
}

publishing {

	publications {

		register<MavenPublication>("release") {
			groupId = "stef40"
			artifactId = "binary-wrapper"
			version = libs.versions.binaryWrapper.version.get()

			afterEvaluate {
				from(components["release"])
			}
		}
	}
}
