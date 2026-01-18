pluginManagement {

	repositories {
		maven("https://jitpack.io")
		google {
			content {
				includeGroupByRegex("""com\.android.*""")
				includeGroupByRegex("""com\.google.*""")
				includeGroupByRegex("androidx.*")
			}
		}
		mavenCentral()
		gradlePluginPortal()
		mavenLocal()
	}
}

include(
	":binaryWrapper",
	":ElementumService",
	":Lt2httpService"
)
