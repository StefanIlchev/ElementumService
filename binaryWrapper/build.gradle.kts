plugins {
	id 'com.android.library'
	id 'org.jetbrains.kotlin.android'
	id 'maven-publish'
}

kotlin {
	jvmToolchain libs.versions.jvmToolchain.get() as int
}

android {
	buildToolsVersion = libs.versions.buildToolsVersion.get()
	compileSdk = libs.versions.compileSdk.get() as int
	namespace = 'ilchev.stefan.binarywrapper'

	buildFeatures {
		buildConfig = true
	}

	defaultConfig {
		minSdk = libs.versions.minSdk.get() as int

		aarMetadata {
			minCompileSdk = minSdk
		}
	}

	buildTypes {

		named('release') {
			minifyEnabled = false
			proguardFiles += getDefaultProguardFile('proguard-android-optimize.txt')
		}
	}

	publishing {

		singleVariant('release') {
			withSourcesJar()
		}
	}
}

publishing {

	publications {

		register('release', MavenPublication) {
			groupId = 'ilchev.stefan'
			artifactId = 'binary-wrapper'
			version = libs.versions.binaryWrapper.version.get()

			afterEvaluate {
				from components.release
			}
		}
	}
}
