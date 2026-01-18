import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.gradle.tasks.MergeSourceSetFolders
import com.android.build.gradle.tasks.PackageApplication
import groovy.xml.MarkupBuilder
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlSlurper
import groovy.xml.XmlUtil
import groovy.xml.slurpersupport.NodeChild
import org.apache.tools.ant.types.Commandline
import org.codehaus.groovy.runtime.EncodingGroovyMethods
import java.io.StringWriter
import java.util.Properties

plugins {
	alias(libs.plugins.android.application)
}

val localProperties: Properties by rootProject.extra

val pagesDir: File by rootProject.extra

val srcGen: String by extra {
	layout.buildDirectory.file("src").get().asFile.path
}

val srcMainGen by extra {
	"$srcGen/main"
}

val srcMainAssetsGen by extra {
	"$srcMainGen/assets"
}

val srcMainJniLibsGen by extra {
	"$srcMainGen/jniLibs"
}

val appVersionCode by extra {
	System.getProperty("version.code")?.toInt()
		?: localProperties.getProperty("lt2http.version.code")?.toInt()
		?: libs.versions.lt2http.versionCode.get().toInt()
}

val appVersionName by extra {
	System.getProperty("version.name")
		?: localProperties.getProperty("lt2http.version.name")
		?: "$appVersionCode"
}

val addonId by extra {
	"service.lt2http"
}

val addonZip: String? = System.getProperty("lt2http.addon.zip")
	?: localProperties.getProperty("lt2http.addon.zip")

val addonDir = addonZip?.let { layout.buildDirectory.file(file(it).nameWithoutExtension).get().asFile.path }

val addonIdDir by extra {
	addonDir?.let { "$it/$addonId" }
}

val binariesZip: String? = System.getProperty("lt2http.binaries.zip")
	?: localProperties.getProperty("lt2http.binaries.zip")

val binariesDir = binariesZip?.let { layout.buildDirectory.file(file(it).nameWithoutExtension).get().asFile.path }

val binariesIdDir by extra {
	binariesDir?.let { "$it/${file(it).name}" }
}

val androidClientZip by extra {
	layout.buildDirectory.file("$addonId-$appVersionName.android_client.zip").get().asFile
}

val abiBins by extra {
	mapOf(
		"arm64-v8a" to "android-arm64",
		"armeabi-v7a" to "android-arm",
		"x86" to "android-x86",
		"x86_64" to "android-x64"
	)
}

val mainIntentAction by extra {
	"android.intent.action.MAIN"
}

val argAddonInfo by extra {
	"-addonInfo"
}

val argLocalPort by extra {
	"-localPort"
}

val argTranslatePath by extra {
	"-translatePath"
}

val localPort by extra {
	65225
}

val kodiId by extra {
	"org.xbmc.kodi"
}

val dataDir by extra {
	"/Download"
}

val kodiDataDir by extra {
	"/Android/data/$kodiId/files"
}

val repoUrl by extra {
	localProperties.getProperty("elementum.repo.url") ?: ""
}

android {
	namespace = "service.lt2http.android"
	testNamespace = "$namespace.test"
	testBuildType = System.getProperty("test.build.type") ?: "debug"
	buildToolsVersion = libs.versions.buildToolsVersion.get()

	compileSdk {
		version = release(libs.versions.compileSdk.get().toInt())
	}

	defaultConfig {
		minSdk = libs.versions.minSdk.get().toInt()
		targetSdk = compileSdk
		versionCode = appVersionCode
		versionName = appVersionName
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		manifestPlaceholders["mainIntentAction"] = mainIntentAction
		buildConfigField("String", "ADDON_ID", "\"$addonId\"")
		buildConfigField("String", "ARG_ADDON_INFO", "\"$argAddonInfo\"")
		buildConfigField("String", "ARG_LOCAL_PORT", "\"$argLocalPort\"")
		buildConfigField("String", "ARG_TRANSLATE_PATH", "\"$argTranslatePath\"")
		buildConfigField("int", "LOCAL_PORT", "$localPort")
		buildConfigField("String", "PROJECT_NAME", "\"${project.name}\"")
		buildConfigField("String", "REPO_URL", "\"$repoUrl\"")
	}

	signingConfigs {

		named("debug") {
			storeFile = rootProject.file(localProperties.getProperty("store.file") ?: "debug.keystore")
			storePassword = localProperties.getProperty("store.password") ?: "android"
			keyAlias = localProperties.getProperty("key.alias") ?: "androiddebugkey"
			keyPassword = localProperties.getProperty("key.password") ?: "android"
		}
	}

	buildTypes {

		release {
			val isNotTestBuildType = testBuildType != name
			isMinifyEnabled = isNotTestBuildType
			isShrinkResources = isNotTestBuildType
			if (isNotTestBuildType) {
				proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
			}
			signingConfig = signingConfigs["debug"]
		}
	}

	buildFeatures {
		buildConfig = true
	}

	sourceSets {

		named("main") {

			assets {
				directories += srcMainAssetsGen
			}

			jniLibs {
				directories += srcMainJniLibsGen
			}
		}
	}

	splits {

		abi {
			isEnable = true
			isUniversalApk = true
			exclude(
				"mips64",
				"mips",
				"armeabi",
				"riscv64"
			)
		}
	}

	packaging {

		jniLibs {
			keepDebugSymbols += "**/lib*.so"
			useLegacyPackaging = true
		}
	}
}

androidComponents {

	onVariants { variant ->
		variant.outputs.forEach {
			if (it !is VariantOutputImpl) return@forEach
			it.outputFileName = file(it.outputFileName.get()).run {
				"$nameWithoutExtension-${it.versionName.get()}.$extension"
			}
		}
	}
}

dependencies {
	implementation(project(":binaryWrapper"))
	androidTestImplementation(libs.androidTest.runner)
	androidTestImplementation(libs.androidTest.junit)
	testImplementation(libs.test.junit)
}

System.getProperty("adb.args")?.let {
	tasks.register<Exec>("adb") {
		group = project.name

		doFirst {
			executable(androidComponents.sdkComponents.adb.get())
			args(*Commandline.translateCommandline(it))
			println("adb ${args.joinToString(" ")}")
		}
	}
}

tasks.register<Exec>("changeDataLocation") {
	group = project.name

	doFirst {
		executable(androidComponents.sdkComponents.adb.get())
		args("shell", "echo", "xbmc.data=/sdcard$kodiDataDir", ">/sdcard/xbmc_env.properties")
		println("adb ${args.joinToString(" ")}")
	}
}

if (addonZip != null && addonDir != null && addonIdDir != null &&
	binariesZip != null && binariesDir != null && binariesIdDir != null
) {

	val patchAddon = tasks.register("patchAddon") {
		group = project.name
		inputs.files(addonZip, binariesZip)
		outputs.dirs(addonDir, binariesDir)

		doFirst {
			delete(addonDir, binariesDir)
		}

		doLast {
			copy {
				from(zipTree(addonZip))
				into(addonDir)
			}
			copy {
				from(zipTree(binariesZip))
				into(binariesDir)
			}
			val parser = XmlSlurper()
			val builder = StreamingMarkupBuilder()
			val addonFile = file("$addonIdDir/addon.xml")
			val addonInfo = parser.parse(addonFile)
			addonInfo.setProperty("@version", appVersionName)
			addonFile.writeText(StringWriter().use { out ->
				MarkupBuilder(out).mkp.yieldUnescaped(builder.bindNode(addonInfo))
				XmlUtil.serialize("$out")
			})
			val startArgs = "${android.namespace}, $mainIntentAction, , version:$appVersionName%s"
			val globalsLine = """ADDON_VERSION = ADDON.getAddonInfo("version")"""
			val globalsLinePatched = """# BEGIN ${project.name}-patched: globals
					|#$globalsLine
					|ADDON_VERSION = ADDON.getAddonInfo('version')
					|GLOBALS = {}
					|
					|def startApp(args):
					|    import six
					|    from kodi_six import xbmc
					|    dataFragment = '%00'.join(six.moves.urllib_parse.quote(arg, '') for arg in args)
					|    dataSuffix = '#' + dataFragment if dataFragment != '' else ''
					|    xbmc.executebuiltin('StartAndroidActivity($startArgs)' % dataSuffix)
					|
					|def recvData(port):
					|    import socket
					|    try:
					|        chunks = []
					|        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as client:
					|            client.connect(('127.0.0.1', port))
					|            while True:
					|                chunk = client.recv(0x2000)
					|                if not chunk:
					|                    break
					|                chunks.append(chunk)
					|        return b''.join(chunks)
					|    except Exception:
					|        pass
					|    return b''
					|
					|def recvApp(expected, args):
					|    import time
					|    remotePort = ADDON.getSetting('remote_port')
					|    port = $localPort
					|    if remotePort != '':
					|        port = int(remotePort)
					|        args.append('$argLocalPort=' + remotePort)
					|    startApp(args)
					|    expectedB = expected.encode()
					|    for _ in range(100):
					|        data = recvData(port)
					|        if expectedB in data:
					|            return data
					|        time.sleep(0.1)
					|    return b''
					|
					|def populateGlobals():
					|    data = recvApp('${android.namespace}', ['$argTranslatePath'])
					|    if data != b'':
					|        with open(os.path.join(ADDON_PATH, '.globals'), 'wb') as f:
					|            f.write(data)
					|        return True
					|    return False
					|
					|def getGlobals():
					|    if GLOBALS:
					|        return GLOBALS
					|    with open(os.path.join(ADDON_PATH, '.globals'), 'r') as f:
					|        homeDirReplacement, xbmcDirReplacement = f.read().split('\0')
					|        GLOBALS['special://home'] = homeDirReplacement
					|        GLOBALS['special://xbmc'] = xbmcDirReplacement
					|    return GLOBALS
					|
					|def retryStartApp():
					|    from lt2http.daemon import config_path, write_binary_config
					|    if populateGlobals():
					|        args = ['--config=' + config_path]
					|        write_binary_config()
					|        startApp(args)
					|# END ${project.name}-patched: globals""".trimMargin()
			val configLine = """config_path = os.path.join(translatePath("special://temp/"), "lt2http-config.json")"""
			val configLinePatched = """# BEGIN ${project.name}-patched: config
					|#$configLine
					|config_path = translatePath('special://lt2http-config.json')
					|# END ${project.name}-patched: config""".trimMargin()
			val versionLine = """    installed_version = read_current_version(binary_dir)"""
			val versionLinePatched = """# BEGIN ${project.name}-patched: version
					|#$versionLine
					|    installed_version = ADDON_VERSION
					|    binary_dir = ADDON_PATH
					|    dest_binary_dir = binary_dir
					|    binary_path = os.path.join(ADDON_PATH, 'fanart.png')
					|    dest_binary_path = binary_path
					|# END ${project.name}-patched: version""".trimMargin()
			val binaryLine = """    return dest_binary_dir, ensure_exec_perms(dest_binary_path)"""
			val binaryLinePatched = """# BEGIN ${project.name}-patched: binary
					|#$binaryLine
					|    return 'N/A', 'N/A'
					|# END ${project.name}-patched: binary""".trimMargin()
			val stopLine = """    lockfile = os.path.join(ADDON_PATH, ".lockfile")"""
			val stopLinePatched = """# BEGIN ${project.name}-patched: stop
					|#$stopLine
					|    from lt2http.addon import populateGlobals, startApp
					|    if not populateGlobals():
					|        return False
					|    lockfile = os.path.join(ADDON_PATH, '.lockfile')
					|    if os.path.exists(lockfile):
					|        os.remove(lockfile)
					|# END ${project.name}-patched: stop""".trimMargin()
			val startLine = """        return subprocess.Popen(args, **kwargs)"""
			val startLinePatched = """# BEGIN ${project.name}-patched: start
					|#$startLine
					|        startApp(args[1:])
					|# END ${project.name}-patched: start""".trimMargin()
			val retryLine = """        data = _json(url)"""
			val retryLinePatched = """# BEGIN ${project.name}-patched: retry
					|#$retryLine
					|        from lt2http.addon import retryStartApp
					|        try:
					|            data = _json(url)
					|        except ConnectionResetError:
					|            retryStartApp()
					|            raise
					|        except urllib_error.URLError as e:
					|            if isinstance(e.reason, IOError) or \
					|                    isinstance(e.reason, OSError) or \
					|                    'Connection refused' in e.reason:
					|                retryStartApp()
					|            raise
					|# END ${project.name}-patched: retry""".trimMargin()
			val translateLine = """    return translatePath(path)"""
			val translateLinePatched = """# BEGIN ${project.name}-patched: translate
					|#$translateLine
					|    from lt2http.addon import getGlobals
					|    if path == 'special://lt2http-config.json':
					|        result = os.path.join(translatePath('special://temp'), 'lt2http-config.json')
					|        return result.replace('$kodiDataDir/.kodi/temp/', '$dataDir/', 1)
					|    result = translatePath(path)
					|    homeDir = translatePath('special://home')
					|    if result.startswith(homeDir):
					|        return result.replace(homeDir, getGlobals()['special://home'], 1)
					|    xbmcDir = translatePath('special://xbmc')
					|    if result.startswith(xbmcDir):
					|        return result.replace(xbmcDir, getGlobals()['special://xbmc'], 1)
					|    return result
					|# END ${project.name}-patched: translate""".trimMargin()
			val patches = mapOf(
				file("$addonIdDir/resources/site-packages/lt2http/addon.py") to mutableListOf(
					listOf(globalsLine, globalsLinePatched)
				),
				file("$addonIdDir/resources/site-packages/lt2http/daemon.py") to mutableListOf(
					listOf(configLine, configLinePatched),
					listOf(versionLine, versionLinePatched),
					listOf(binaryLine, binaryLinePatched),
					listOf(stopLine, stopLinePatched),
					listOf(startLine, startLinePatched)
				),
				file("$addonIdDir/resources/site-packages/lt2http/navigation.py") to mutableListOf(
					listOf(retryLine, retryLinePatched)
				),
				file("$addonIdDir/resources/site-packages/lt2http/util.py") to mutableListOf(
					listOf(translateLine, translateLinePatched)
				)
			)
			patches.forEach { (f, patch) ->
				val tmp = file("${f.path}.tmp")
				if (!f.renameTo(tmp)) {
					throw GradleException(tmp.path)
				}
				copy {
					from(f.parent)
					into(f.parent)
					include(tmp.name)
					rename { f.name }
					filter { line ->
						val index = patch.indexOfFirst { it[0] == line }
						if (index < 0) line else patch.removeAt(index)[1]
					}
				}
				if (patch.isNotEmpty()) {
					val missing = patch.joinToString("\n\n") { it.joinToString("\n\n\n\n") }
					throw GradleException("${f.path} missing:\n$missing")
				}
				delete(tmp)
			}
		}
	}

	val genMainAssets = tasks.register("genMainAssets") {
		group = project.name
		inputs.dir(addonDir)
		outputs.dir(srcMainAssetsGen)
		dependsOn(patchAddon)

		doFirst {
			delete(srcMainAssetsGen)
		}

		doLast {
			copy {
				from(addonDir)
				into(srcMainAssetsGen)
				exclude("**/.*")
			}
		}
	}

	val genMainJniLibs = tasks.register("genMainJniLibs") {
		group = project.name
		inputs.dir(binariesDir)
		outputs.dir(srcMainJniLibsGen)
		dependsOn(patchAddon)

		doFirst {
			delete(srcMainJniLibsGen)
		}

		doLast {
			val regex = """^lib.*\.so$""".toRegex()
			abiBins.forEach { (abi, bin) ->
				copy {
					from("$binariesIdDir/$bin")
					into("$srcMainJniLibsGen/$abi")
					rename { if (it.matches(regex)) it else "lib$it.so" }
				}
			}
		}
	}

	tasks.withType<JavaCompile>().configureEach {
		dependsOn(genMainAssets, genMainJniLibs)
	}

	tasks.withType<MergeSourceSetFolders>().configureEach {
		mustRunAfter(genMainAssets, genMainJniLibs)
	}

	val zipAndroidClient = tasks.register<Zip>("zipAndroidClient") {
		group = project.name
		inputs.dir(addonDir)
		outputs.file(androidClientZip)
		dependsOn(patchAddon)
		from(addonDir)
		destinationDirectory = androidClientZip.parentFile
		archiveFileName = androidClientZip.name
		exclude("**/.*")

		doFirst {
			delete(androidClientZip)
		}
	}

	tasks.register("pushAndroidClient") {
		val destinationDir = "/sdcard$dataDir"
		group = project.name
		dependsOn(zipAndroidClient)

		doFirst {
			providers.exec {
				executable(androidComponents.sdkComponents.adb.get())
				args("shell", "rm", "-f", "$destinationDir/${androidClientZip.name}")
				isIgnoreExitValue = true
				println("adb ${args.joinToString(" ")}")
			}.run {
				println(standardOutput.asText.get())
				println(standardError.asText.get())
				result.get().assertNormalExitValue()
			}
		}

		doLast {
			providers.exec {
				executable(androidComponents.sdkComponents.adb.get())
				args("push", androidClientZip.path, destinationDir)
				isIgnoreExitValue = true
				println("adb ${args.joinToString(" ")}")
			}.run {
				println(standardOutput.asText.get())
				println(standardError.asText.get())
				result.get().assertNormalExitValue()
			}
		}
	}

	tasks.register("genPages") {
		val packageRelease = tasks.named<PackageApplication>("packageRelease")
		group = project.name
		outputs.dir(pagesDir)
		dependsOn(rootProject.tasks.named("genPages"), packageRelease, zipAndroidClient)
		finalizedBy(rootProject.tasks.named("genIndex"))

		doLast {
			copy {
				from(packageRelease.get().outputDirectory)
				into(pagesDir)
				include("*.apk")
			}
			copy {
				from(androidClientZip)
				into(pagesDir)
			}
			val repoZip = File(pagesDir, "$addonId/$addonId-$appVersionName.zip")
			copy {
				from(androidClientZip)
				into(repoZip.parentFile)
				rename { repoZip.name }
			}
			val parser = XmlSlurper()
			val builder = StreamingMarkupBuilder()
			val addonFile = file("$addonIdDir/addon.xml")
			val addonInfo = parser.parse(addonFile)
			copy {
				from(addonIdDir)
				into(File(pagesDir, addonId))
				include(addonInfo.children().findAll(KotlinClosure1<NodeChild, Boolean>({
					name() == "extension"
				})).children().findAll(KotlinClosure1<NodeChild, Boolean>({
					name() == "assets"
				})).children().map { "$it" })
			}
			val repoInfo = File(pagesDir, "$addonId/addons.xml")
			val repoInfoText = StringWriter().use { out ->
				MarkupBuilder(out).run {
					withGroovyBuilder {
						"addons" {
							mkp.yieldUnescaped(builder.bindNode(addonInfo))
						}
					}
				}
				XmlUtil.serialize("$out")
			}
			repoInfo.writeText(repoInfoText)
			val repoInfoMd5 = File(pagesDir, "$addonId/addons.xml.md5")
			repoInfoMd5.writeText(EncodingGroovyMethods.md5(repoInfoText))
		}
	}
}
