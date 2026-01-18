plugins {
	alias(libs.plugins.android.library)
	`maven-publish`
}

android {
	namespace = "stef40.binarywrapper"
	buildToolsVersion = libs.versions.buildToolsVersion.get()

	compileSdk {
		version = release(libs.versions.compileSdk.get().toInt())
	}

	defaultConfig {
		minSdk = libs.versions.minSdk.get().toInt()

		aarMetadata {
			minCompileSdk = minSdk
		}
	}

	buildTypes {

		release {
			isMinifyEnabled = false
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
		}
	}

	buildFeatures {
		buildConfig = true
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
