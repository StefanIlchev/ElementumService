import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.gradle.tasks.MergeSourceSetFolders
import com.android.build.gradle.tasks.PackageApplication
import groovy.json.JsonOutput
import groovy.xml.MarkupBuilder
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlSlurper
import groovy.xml.XmlUtil
import groovy.xml.slurpersupport.NodeChild
import org.apache.tools.ant.types.Commandline
import org.codehaus.groovy.runtime.EncodingGroovyMethods
import java.io.StringWriter
import java.util.Properties
import java.util.zip.ZipFile

plugins {
	id("com.android.application")
	kotlin("android")
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

val srcMainAbiHeadsGen by extra {
	"$srcMainGen/abiHeads"
}

val srcMainJniLibsGen by extra {
	"$srcMainGen/jniLibs"
}

val appVersionCode by extra {
	System.getProperty("version.code")?.toInt()
		?: localProperties.getProperty("elementum.version.code")?.toInt()
		?: libs.versions.elementum.versionCode.get().toInt()
}

val appVersionName by extra {
	System.getProperty("version.name")
		?: localProperties.getProperty("elementum.version.name")
		?: "$appVersionCode"
}

val addonId by extra {
	"plugin.video.elementum"
}

val addonZip: String? = System.getProperty("elementum.addon.zip")
	?: localProperties.getProperty("elementum.addon.zip")

val addonDir = addonZip?.let { layout.buildDirectory.file(file(it).nameWithoutExtension).get().asFile.path }

val addonIdDir by extra {
	addonDir?.let { "$it/$addonId" }
}

val addonBinDir by extra {
	addonIdDir?.let { "$it/resources/bin" }
}

val androidClientZip by extra {
	layout.buildDirectory.file("$addonId-$appVersionName.android_client.zip").get().asFile
}

val abiBins by extra {
	mapOf(
		"arm64-v8a" to "android_arm64",
		"armeabi-v7a" to "android_arm",
		"x86" to "android_x86",
		"x86_64" to "android_x64"
	)
}

val isAddonBinLib by extra {
	localProperties.getProperty("is.elementum.addon.bin.lib")?.toBoolean() ?: addonZip?.let {
		ZipFile(it)
	}?.use {
		abiBins.any { (_, bin) -> it.getEntry("$addonId/resources/bin/$bin/elementum")?.isDirectory != false }
	} ?: false
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
	65220
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

kotlin {
	jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

android {
	buildToolsVersion = libs.versions.buildToolsVersion.get()
	compileSdk = libs.versions.compileSdk.get().toInt()
	ndkVersion = libs.versions.ndkVersion.get()
	namespace = "service.elementum.android"
	testNamespace = "$namespace.test"
	testBuildType = System.getProperty("test.build.type") ?: "debug"

	if (isAddonBinLib) {
		externalNativeBuild {

			cmake {
				version = libs.versions.cmake.get()
				path = file("CMakeLists.txt")
			}
		}
	}

	buildFeatures {
		buildConfig = true
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

		if (isAddonBinLib) {
			@Suppress("UnstableApiUsage")
			externalNativeBuild {

				cmake {
					arguments(
						"-DCMAKE_REQUIRED=${libs.versions.cmake.get()}",
						"-DCXX_STANDARD=${libs.versions.cpp.get()}",
						"-DPROJECT_NAME=${project.name}",
						"-DSRC_MAIN_ABI_HEADS_GEN=$srcMainAbiHeadsGen",
						"-DSRC_MAIN_JNI_LIBS_GEN=$srcMainJniLibsGen"
					)
				}
			}
		}
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

		named("release") {
			val isNotTestBuildType = testBuildType != name
			isMinifyEnabled = isNotTestBuildType
			isShrinkResources = isNotTestBuildType
			if (isNotTestBuildType) {
				proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
			}
			signingConfig = signingConfigs["debug"]
		}
	}

	sourceSets {

		named("main") {

			assets {
				srcDir(srcMainAssetsGen)
			}

			jniLibs {
				srcDir(srcMainJniLibsGen)
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

fun toMethodJson(
	method: String,
	params: Map<String, *>
): String = JsonOutput.toJson(mapOf("jsonrpc" to "2.0", "id" to "1", "method" to method, "params" to params))

fun toGetAddonDetailsJson(
	addonId: String,
	properties: List<String>
) = toMethodJson("Addons.GetAddonDetails", mapOf("addonid" to addonId, "properties" to properties))

fun toSetAddonEnabledJson(
	addonId: String,
	enabled: Boolean
) = toMethodJson("Addons.SetAddonEnabled", mapOf("addonid" to addonId, "enabled" to enabled))

System.getProperty("adb.args")?.let {
	tasks.register<Exec>("adb") {
		group = project.name
		executable = android.adbExecutable.path
		args(*Commandline.translateCommandline(it))

		doFirst {
			println("adb ${args?.joinToString(" ")}")
		}
	}
}

tasks.register<Exec>("changeDataLocation") {
	group = project.name
	executable = android.adbExecutable.path
	args("shell", "echo", "xbmc.data=/sdcard$kodiDataDir", ">/sdcard/xbmc_env.properties")

	doFirst {
		println("adb ${args?.joinToString(" ")}")
	}
}

if (addonZip != null && addonDir != null && addonIdDir != null && addonBinDir != null) {

	val patchAddon = tasks.register("patchAddon") {
		group = project.name
		inputs.file(addonZip)
		outputs.dir(addonDir)

		doFirst {
			delete(addonDir)
		}

		doLast {
			copy {
				from(zipTree(addonZip))
				into(addonDir)
			}
			val parser = XmlSlurper()
			val builder = StreamingMarkupBuilder()
			val serviceRepoAddonId = "repository.service.elementum"
			val addonFile = file("$addonIdDir/addon.xml")
			val addonInfo = parser.parse(addonFile)
			val providerName = addonInfo.getProperty("@provider-name").toString()
			addonInfo.setProperty("@version", appVersionName)
			addonInfo.children().findAll(KotlinClosure1<NodeChild, Boolean>({
				name() == "requires"
			})).leftShift(StringWriter().use { out ->
				MarkupBuilder(out).run {
					withGroovyBuilder {
						"import"("addon" to serviceRepoAddonId, "optional" to "true")
					}
				}
				parser.parseText("$out")
			})
			addonFile.writeText(StringWriter().use { out ->
				MarkupBuilder(out).mkp.yieldUnescaped(builder.bindNode(addonInfo))
				XmlUtil.serialize("$out")
			})
			val repoAddonInfoFormat = StringWriter().use { out ->
				MarkupBuilder(out).run {
					withGroovyBuilder {
						"addon"(
							"provider-name" to providerName,
							"name" to "{name}",
							"id" to "{id}",
							"version" to appVersionName
						) {

							"extension"("point" to "xbmc.addon.repository") {

								"dir" {

									"checksum" { mkp.yield("{dataDir}addons.xml.md5") }

									"datadir"("zip" to "true") { mkp.yield("{dataDir}") }

									"info"("compressed" to "false") { mkp.yield("{dataDir}addons.xml") }
								}
							}

							"extension"("point" to "xbmc.addon.metadata") {

								"platform" { mkp.yield("all") }

								"summary"("lang" to "en") { mkp.yield("{summary}") }
							}
						}
					}
				}
				val repoAddonInfo = parser.parseText("$out")
				out.buffer.setLength(0)
				MarkupBuilder(out).mkp.yieldUnescaped(builder.bindNode(repoAddonInfo))
				XmlUtil.serialize("$out")
			}
			val startArgs = "${android.namespace}, $mainIntentAction, , version:$appVersionName%s"
			val globalsLine = """ADDON_VERSION = ADDON.getAddonInfo("version")"""
			val globalsLinePatched = """# BEGIN ${project.name}-patched: globals
				|#$globalsLine
				|ADDON_VERSION = ADDON.getAddonInfo('version')
				|GLOBALS = {}
				|
				|def makeAddon(id, info):
				|    import shutil
				|    fanartPng = os.path.join(ADDON_PATH, 'fanart.png')
				|    iconPng = os.path.join(ADDON_PATH, 'icon.png')
				|    idDir = os.path.join(ADDON_PATH, '..', id)
				|    infoFile = os.path.join(idDir, 'addon.xml')
				|    if os.path.exists(infoFile):
				|        try:
				|            with open(infoFile, 'r') as f:
				|                if info == f.read():
				|                    return True
				|        except Exception:
				|            pass
				|    try:
				|        shutil.rmtree(idDir, True)
				|        os.makedirs(idDir)
				|        with open(infoFile, 'w') as f:
				|            f.write(info)
				|        shutil.copy(fanartPng, os.path.join(idDir, 'fanart.png'))
				|        shutil.copy(iconPng, os.path.join(idDir, 'icon.png'))
				|        return True
				|    except Exception:
				|        pass
				|    return False
				|
				|def installAddon(id):
				|    import time
				|    from kodi_six import xbmc
				|    from elementum.provider import parse_json
				|    try:
				|        detailsJson = '${toGetAddonDetailsJson("%s", listOf("installed", "enabled"))}' % id
				|        enableJson = '${toSetAddonEnabledJson("%s", true)}' % id
				|        addon = parse_json(xbmc.executeJSONRPC(detailsJson)).get('result', {}).get('addon', {})
				|        if addon.get('installed', False):
				|            if not addon.get('enabled', False):
				|                xbmc.executeJSONRPC(enableJson)
				|            return
				|        xbmc.executebuiltin('UpdateLocalAddons')
				|        xbmc.executebuiltin('InstallAddon(%s)' % id)
				|        for _ in range(10):
				|            addon = parse_json(xbmc.executeJSONRPC(detailsJson)).get('result', {}).get('addon', {})
				|            if addon.get('installed', False):
				|                break
				|            time.sleep(1.0)
				|        xbmc.executeJSONRPC(enableJson)
				|        xbmc.executebuiltin('UpdateLocalAddons')
				|        xbmc.executebuiltin('UpdateAddonRepos')
				|    except Exception:
				|        pass
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
				|    if populateGlobals():
				|        args = []
				|        if ADDON.getSetting('local_port') != '':
				|            args.append('-remotePort=' + ADDON.getSetting('local_port'))
				|        if ADDON.getSetting('remote_host') != '':
				|            args.append('-localHost=' + ADDON.getSetting('remote_host'))
				|        if ADDON.getSetting('remote_port') != '':
				|            args.append('$argLocalPort=' + ADDON.getSetting('remote_port'))
				|        startApp(args)
				|# END ${project.name}-patched: globals""".trimMargin()
			val versionLine = "    installed_version = read_current_version(binary_dir)"
			val versionLinePatched = """# BEGIN ${project.name}-patched: version
				|#$versionLine
				|    installed_version = ADDON_VERSION
				|    binary_dir = ADDON_PATH
				|    dest_binary_dir = binary_dir
				|    binary_path = os.path.join(ADDON_PATH, 'fanart.png')
				|    dest_binary_path = binary_path
				|# END ${project.name}-patched: version""".trimMargin()
			val binaryLine = "    return dest_binary_dir, ensure_exec_perms(dest_binary_path)"
			val binaryLinePatched = """# BEGIN ${project.name}-patched: binary
				|#$binaryLine
				|    return 'N/A', 'N/A'
				|# END ${project.name}-patched: binary""".trimMargin()
			val stopLine = """    lockfile = os.path.join(get_addon_profile_dir(), ".lockfile")"""
			val stopLinePatched = """# BEGIN ${project.name}-patched: stop
				|#$stopLine
				|    lockfile = os.path.join(get_addon_profile_dir(), '.lockfile')
				|    if os.path.exists(lockfile):
				|        os.remove(lockfile)
				|# END ${project.name}-patched: stop""".trimMargin()
			val startLine = "            proc = subprocess.Popen(args, **kwargs)"
			val startLinePatched = """# BEGIN ${project.name}-patched: start
				|#$startLine
				|            from elementum.addon import installAddon, makeAddon, populateGlobals, startApp
				|            if populateGlobals():
				|                startApp(args[1:])
				|            repoAddonInfoFormat = '''$repoAddonInfoFormat'''
				|            serviceRepoAddonDataDir = '$repoUrl'
				|            if serviceRepoAddonDataDir != '':
				|                serviceRepoAddonId = '$serviceRepoAddonId'
				|                serviceRepoAddonInfo = repoAddonInfoFormat.format(
				|                    name = 'Elementum Service Repository',
				|                    id = serviceRepoAddonId,
				|                    dataDir = serviceRepoAddonDataDir,
				|                    summary = 'GitHub repository for Elementum Service updates'
				|                )
				|                if makeAddon(serviceRepoAddonId, serviceRepoAddonInfo):
				|                    installAddon(serviceRepoAddonId)
				|            return None
				|# END ${project.name}-patched: start""".trimMargin()
			val retryLine = "        data = _json(url)"
			val retryLinePatched = """# BEGIN ${project.name}-patched: retry
				|#$retryLine
				|        from elementum.addon import retryStartApp
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
			val installLine = """        return xbmc.executebuiltin("InstallAddon(%s)" % addonId)"""
			val installLinePatched = """# BEGIN ${project.name}-patched: install
				|#$installLine
				|        if addonId == 'repository.elementumorg':
				|            import zipfile
				|            from io import BytesIO
				|            from elementum.addon import installAddon, recvApp, retryStartApp
				|            addonZip = recvApp(addonId, ['$argAddonInfo=' + addonId])
				|            if addonZip != b'':
				|                try:
				|                    with zipfile.ZipFile(BytesIO(addonZip), 'r') as zip:
				|                        zip.extractall(os.path.join(ADDON_PATH, '..'))
				|                    installAddon(addonId)
				|                except Exception:
				|                    pass
				|            retryStartApp()
				|            return None
				|        return xbmc.executebuiltin('InstallAddon(%s)' % addonId)
				|# END ${project.name}-patched: install""".trimMargin()
			val infoLine = "        return info"
			val infoLinePatched = """# BEGIN ${project.name}-patched: info
				|#$infoLine
				|        from elementum.addon import getGlobals
				|        globals = getGlobals()
				|        result = {}
				|        homeDir = translatePath('special://home')
				|        homeDirReplacement = globals['special://home']
				|        xbmcDir = translatePath('special://xbmc')
				|        xbmcDirReplacement = globals['special://xbmc']
				|        for key, value in info.items():
				|            if value.startswith(homeDir):
				|                result[key] = value.replace(homeDir, homeDirReplacement, 1)
				|            elif value.startswith(xbmcDir):
				|                result[key] = value.replace(xbmcDir, xbmcDirReplacement, 1)
				|            else:
				|                result[key] = value
				|        return result
				|# END ${project.name}-patched: info""".trimMargin()
			val translateLine = "        return translatePath(*args, **kwargs)"
			val translateLinePatched = """# BEGIN ${project.name}-patched: translate
				|#$translateLine
				|        from elementum.addon import getGlobals
				|        result = translatePath(*args, **kwargs)
				|        homeDir = translatePath('special://home')
				|        if result.startswith(homeDir):
				|            return result.replace(homeDir, getGlobals()['special://home'], 1)
				|        xbmcDir = translatePath('special://xbmc')
				|        if result.startswith(xbmcDir):
				|            return result.replace(xbmcDir, getGlobals()['special://xbmc'], 1)
				|        return result
				|# END ${project.name}-patched: translate""".trimMargin()
			val patches = mapOf(
				file("$addonIdDir/resources/site-packages/elementum/addon.py") to mutableListOf(
					listOf(globalsLine, globalsLinePatched)
				),
				file("$addonIdDir/resources/site-packages/elementum/daemon.py") to mutableListOf(
					listOf(versionLine, versionLinePatched),
					listOf(binaryLine, binaryLinePatched),
					listOf(stopLine, stopLinePatched),
					listOf(startLine, startLinePatched)
				),
				file("$addonIdDir/resources/site-packages/elementum/navigation.py") to mutableListOf(
					listOf(retryLine, retryLinePatched)
				),
				file("$addonIdDir/resources/site-packages/elementum/rpc.py") to mutableListOf(
					listOf(installLine, installLinePatched),
					listOf(infoLine, infoLinePatched),
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
				exclude("**/.*", "$addonId/resources/bin")
			}
		}
	}

	val genMainJniLibs = tasks.register("genMainJniLibs") {
		group = project.name
		inputs.dir(addonDir)
		if (isAddonBinLib) {
			outputs.dirs(srcMainAbiHeadsGen, srcMainJniLibsGen)
		} else {
			outputs.dir(srcMainJniLibsGen)
		}
		dependsOn(patchAddon)

		doFirst {
			delete(srcMainAbiHeadsGen, srcMainJniLibsGen)
		}

		doLast {
			val regex = """^lib.*\.so$""".toRegex()
			abiBins.forEach { (abi, bin) ->
				if (isAddonBinLib) {
					copy {
						from("$addonBinDir/$bin")
						into("$srcMainAbiHeadsGen/$abi")
						include("*.h")
					}
				}
				copy {
					from("$addonBinDir/$bin")
					into("$srcMainJniLibsGen/$abi")
					if (isAddonBinLib) {
						include("*.so")
					} else {
						include("elementum", "lib*.so")
					}
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
		exclude("**/.*", "$addonId/resources/bin")

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
				executable = android.adbExecutable.path
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
				executable = android.adbExecutable.path
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
