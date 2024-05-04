import com.android.build.gradle.tasks.MergeSourceSetFolders
import com.android.build.gradle.tasks.PackageApplication
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlSlurper
import groovy.xml.XmlUtil
import org.apache.tools.ant.types.Commandline

plugins {
	id 'com.android.application'
	id 'org.jetbrains.kotlin.android'
}

ext {
	srcGen = layout.buildDirectory.file('src').get().asFile.path
	srcMainGen = "$srcGen/main"
	srcMainAssetsGen = "$srcMainGen/assets"
	srcMainJniLibsGen = "$srcMainGen/jniLibs"
	appVersionCode = System.getProperty('version.code') as Integer
			?: localProperties.getProperty('lt2http.version.code') as Integer
			?: libs.versions.lt2http.versionCode.get() as int
	appVersionName = System.getProperty('version.name')
			?: localProperties.getProperty('lt2http.version.name')
			?: "$appVersionCode"
	addonId = 'service.lt2http'
	addonZip = System.getProperty('lt2http.addon.zip') ?: localProperties.getProperty('lt2http.addon.zip')
	addonDir = addonZip?.with {
		layout.buildDirectory.file(file(it).name - ~/\.[^.]+$/).get().asFile.path
	}
	addonIdDir = addonDir?.with { "$it/$addonId" }
	binariesZip = System.getProperty('lt2http.binaries.zip') ?: localProperties.getProperty('lt2http.binaries.zip')
	binariesDir = binariesZip?.with {
		layout.buildDirectory.file(file(it).name - ~/\.[^.]+$/).get().asFile.path
	}
	binariesIdDir = binariesDir?.with { "$it/${file(it).name}" }
	androidClientZip = layout.buildDirectory.file("$addonId-${appVersionName}.android_client.zip").get().asFile
	abiBins = [
			'arm64-v8a'  : 'android-arm64',
			'armeabi-v7a': 'android-arm',
			'x86'        : 'android-x86',
			'x86_64'     : 'android-x64'
	]
	mainIntentAction = 'android.intent.action.MAIN'
	argAddonInfo = '-addonInfo'
	argLocalPort = '-localPort'
	argTranslatePath = '-translatePath'
	localPort = 65225
	kodiId = 'org.xbmc.kodi'
	dataDir = '/Download'
	kodiDataDir = "/Android/data/$kodiId/files"
	repoUrl = localProperties.getProperty('elementum.repo.url') ?: ''
}

kotlin {
	jvmToolchain libs.versions.jvmToolchain.get() as int
}

android {
	buildToolsVersion = libs.versions.buildToolsVersion.get()
	compileSdk = libs.versions.compileSdk.get() as int
	namespace = 'service.lt2http.android'
	testNamespace = "${namespace}.test"
	testBuildType = System.getProperty('test.build.type') ?: 'debug'

	buildFeatures {
		buildConfig = true
	}

	defaultConfig {
		minSdk = libs.versions.minSdk.get() as int
		targetSdk = compileSdk
		versionCode = appVersionCode
		versionName = appVersionName
		testInstrumentationRunner = 'androidx.test.runner.AndroidJUnitRunner'
		manifestPlaceholders = [
				'mainIntentAction': mainIntentAction
		]
		buildConfigField 'String', 'ADDON_ID', "\"$addonId\""
		buildConfigField 'String', 'ARG_ADDON_INFO', "\"$argAddonInfo\""
		buildConfigField 'String', 'ARG_LOCAL_PORT', "\"$argLocalPort\""
		buildConfigField 'String', 'ARG_TRANSLATE_PATH', "\"$argTranslatePath\""
		buildConfigField 'int', 'LOCAL_PORT', "$localPort"
		buildConfigField 'String', 'PROJECT_NAME', "\"$project.name\""
		buildConfigField 'String', 'REPO_URL', "\"$repoUrl\""
	}

	signingConfigs {

		named('debug') {
			storeFile = rootProject.file(localProperties.getProperty('store.file') ?: 'debug.keystore')
			storePassword = localProperties.getProperty('store.password') ?: 'android'
			keyAlias = localProperties.getProperty('key.alias') ?: 'androiddebugkey'
			keyPassword = localProperties.getProperty('key.password') ?: 'android'
		}
	}

	buildTypes {

		named('release') {
			def isNotTestBuildType = testBuildType != it.name
			minifyEnabled = isNotTestBuildType
			shrinkResources = isNotTestBuildType
			if (isNotTestBuildType) {
				proguardFiles += getDefaultProguardFile('proguard-android-optimize.txt')
			}
			signingConfig = signingConfigs.debug
		}
	}

	sourceSets {

		named('main') {

			assets {
				srcDir srcMainAssetsGen
			}

			jniLibs {
				srcDir srcMainJniLibsGen
			}
		}
	}

	splits {

		abi {
			enable = true
			universalApk = true
			exclude 'mips64',
					'mips',
					'armeabi',
					'riscv64'
		}
	}

	packagingOptions {

		jniLibs {
			keepDebugSymbols += '**/lib*.so'
			useLegacyPackaging = true
		}
	}
}

androidComponents {

	onVariants(selector().all()) { variant ->
		variant.outputs.each {
			def fileName = it.outputFileName.get()
			def name = fileName - ~/\.[^.]+$/
			it.outputFileName.set "$name-${it.versionName.get()}${fileName.substring name.length()}"
		}
	}
}

dependencies {
	implementation project(':binaryWrapper')
	androidTestImplementation libs.androidTest.runner
	androidTestImplementation libs.androidTest.junit
	testImplementation libs.test.junit
}

System.getProperty('adb.args')?.with { adbArgs ->
	tasks.register('adb', Exec) {
		group = project.name
		executable = android.adbExecutable.path
		args Commandline.translateCommandline(adbArgs)
	}
}

tasks.register('changeDataLocation', Exec) {
	group = project.name
	executable = android.adbExecutable.path
	args 'shell', 'echo', "xbmc.data=/sdcard$kodiDataDir", '>/sdcard/xbmc_env.properties'
}

if (addonIdDir != null && binariesIdDir != null) {

	def patchAddon = tasks.register('patchAddon') {
		group = project.name
		inputs.files addonZip, binariesZip
		outputs.dirs addonDir, binariesDir

		doFirst {
			delete addonDir, binariesDir
		}

		doLast {
			copy {
				from zipTree(addonZip)
				into addonDir
			}
			copy {
				from zipTree(binariesZip)
				into binariesDir
			}
			def addonFile = file "$addonIdDir/addon.xml"
			def addonInfo = new XmlSlurper().parse addonFile
			addonInfo.@'version' = appVersionName
			addonFile.withWriter {
				XmlUtil.serialize(new StreamingMarkupBuilder().bind {
					mkp.yield addonInfo
				} as Writable, it)
			}
			def startArgs = "$android.namespace, $mainIntentAction, , version:$appVersionName%s"
			def globalsLine = 'ADDON_VERSION = ADDON.getAddonInfo("version")'
			def globalsLinePatched = """# BEGIN $project.name-patched: globals
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
					|    data = recvApp('$android.namespace', ['$argTranslatePath'])
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
					|        homeDirReplacement, xbmcDirReplacement = f.read().split('\\0')
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
					|# END $project.name-patched: globals""".stripMargin()
			def configLine = 'config_path = os.path.join(translatePath("special://temp/"), "lt2http-config.json")'
			def configLinePatched = """# BEGIN $project.name-patched: config
					|#$configLine
					|config_path = translatePath('special://lt2http-config.json')
					|# END $project.name-patched: config""".stripMargin()
			def versionLine = '    installed_version = read_current_version(binary_dir)'
			def versionLinePatched = """# BEGIN $project.name-patched: version
					|#$versionLine
					|    installed_version = ADDON_VERSION
					|    binary_dir = ADDON_PATH
					|    dest_binary_dir = binary_dir
					|    binary_path = os.path.join(ADDON_PATH, 'fanart.png')
					|    dest_binary_path = binary_path
					|# END $project.name-patched: version""".stripMargin()
			def binaryLine = '    return dest_binary_dir, ensure_exec_perms(dest_binary_path)'
			def binaryLinePatched = """# BEGIN $project.name-patched: binary
					|#$binaryLine
					|    return 'N/A', 'N/A'
					|# END $project.name-patched: binary""".stripMargin()
			def stopLine = '    lockfile = os.path.join(ADDON_PATH, ".lockfile")'
			def stopLinePatched = """# BEGIN $project.name-patched: stop
					|#$stopLine
					|    from lt2http.addon import populateGlobals, startApp
					|    if not populateGlobals():
					|        return False
					|    lockfile = os.path.join(ADDON_PATH, '.lockfile')
					|    if os.path.exists(lockfile):
					|        os.remove(lockfile)
					|# END $project.name-patched: stop""".stripMargin()
			def startLine = '        return subprocess.Popen(args, **kwargs)'
			def startLinePatched = """# BEGIN $project.name-patched: start
					|#$startLine
					|        startApp(args[1:])
					|# END $project.name-patched: start""".stripMargin()
			def retryLine = '        data = _json(url)'
			def retryLinePatched = """# BEGIN $project.name-patched: retry
					|#$retryLine
					|        from elementum.addon import retryStartApp
					|        try:
					|            data = _json(url)
					|        except ConnectionResetError:
					|            retryStartApp()
					|            raise
					|        except urllib_error.URLError as e:
					|            if isinstance(e.reason, IOError) or \\
					|                    isinstance(e.reason, OSError) or \\
					|                    'Connection refused' in e.reason:
					|                retryStartApp()
					|            raise
					|# END $project.name-patched: retry""".stripMargin()
			def translateLine = '    return translatePath(path)'
			def translateLinePatched = """# BEGIN $project.name-patched: translate
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
					|# END $project.name-patched: translate""".stripMargin()
			def patches = [
					(file("$addonIdDir/resources/site-packages/lt2http/addon.py"))     : [
							[globalsLine, globalsLinePatched]
					],
					(file("$addonIdDir/resources/site-packages/lt2http/daemon.py"))    : [
							[configLine, configLinePatched],
							[versionLine, versionLinePatched],
							[binaryLine, binaryLinePatched],
							[stopLine, stopLinePatched],
							[startLine, startLinePatched]
					],
					(file("$addonIdDir/resources/site-packages/lt2http/navigation.py")): [
							[retryLine, retryLinePatched]
					],
					(file("$addonIdDir/resources/site-packages/lt2http/util.py"))      : [
							[translateLine, translateLinePatched]
					]
			]
			patches.each { f, patch ->
				def tmp = file "${f.path}.tmp"
				if (!f.renameTo(tmp)) {
					throw new GradleException(tmp.path)
				}
				copy {
					from f.parent
					into f.parent
					include tmp.name
					rename { f.name }
					filter { line ->
						def index = patch.findIndexOf { it[0] == line }
						index < 0 ? line : patch.removeAt(index)[1]
					}
				}
				if (!patch.isEmpty()) {
					def missing = patch*.join '\n\n' join '\n\n\n\n'
					throw new GradleException("$f.path missing:\n$missing")
				}
				delete tmp
			}
		}
	}

	def genMainAssets = tasks.register('genMainAssets') {
		group = project.name
		inputs.dir addonDir
		outputs.dir srcMainAssetsGen
		dependsOn patchAddon

		doFirst {
			delete srcMainAssetsGen
		}

		doLast {
			copy {
				from addonDir
				into srcMainAssetsGen
				exclude '**/.*'
			}
		}
	}

	def genMainJniLibs = tasks.register('genMainJniLibs') {
		group = project.name
		inputs.dir binariesDir
		outputs.dir srcMainJniLibsGen
		dependsOn patchAddon

		doFirst {
			delete srcMainJniLibsGen
		}

		doLast {
			abiBins.each { abi, bin ->
				copy {
					from "$binariesIdDir/$bin"
					into "$srcMainJniLibsGen/$abi"
					rename { it ==~ /^lib.*\.so$/ ? it : "lib${it}.so" }
				}
			}
		}
	}

	tasks.withType(JavaCompile).configureEach {
		dependsOn genMainAssets, genMainJniLibs
	}

	tasks.withType(MergeSourceSetFolders).configureEach {
		mustRunAfter genMainAssets, genMainJniLibs
	}

	def zipAndroidClient = tasks.register('zipAndroidClient', Zip) {
		group = project.name
		inputs.dir addonDir
		outputs.file androidClientZip
		dependsOn patchAddon
		from addonDir
		destinationDirectory = androidClientZip.parentFile
		archiveFileName = androidClientZip.name
		exclude '**/.*'

		doFirst {
			delete androidClientZip
		}
	}

	tasks.register('pushAndroidClient') {
		group = project.name
		dependsOn zipAndroidClient

		ext {
			destinationDir = "/sdcard$dataDir"
		}

		doFirst {
			exec {
				executable = android.adbExecutable.path
				args 'shell', 'rm', '-f', "$destinationDir/$androidClientZip.name"
			}
		}

		doLast {
			exec {
				executable = android.adbExecutable.path
				args 'push', androidClientZip.path, destinationDir
			}
		}
	}

	tasks.register('genPages') {
		def packageRelease = tasks.named('packageRelease', PackageApplication)
		group = project.name
		outputs.dir pagesDir
		dependsOn rootProject.tasks.named('genPages'), packageRelease, zipAndroidClient
		finalizedBy rootProject.tasks.named('genIndex')

		doLast {
			copy {
				from packageRelease.get().outputDirectory
				into pagesDir
				include '*.apk'
			}
			copy {
				from androidClientZip
				into pagesDir
			}
			def repoZip = new File(pagesDir, "$addonId/$addonId-${appVersionName}.zip")
			copy {
				from androidClientZip
				into repoZip.parentFile
				rename { repoZip.name }
			}
			def addonFile = file "$addonIdDir/addon.xml"
			def addonInfo = new XmlSlurper().parse addonFile
			copy {
				from addonIdDir
				into new File(pagesDir, addonId)
				include addonInfo.'extension'.'assets'.'*'*.text()
			}
			def repoInfo = new File(pagesDir, "$addonId/addons.xml")
			repoInfo.withWriter {
				XmlUtil.serialize(new StreamingMarkupBuilder().bind {
					'addons' {
						mkp.yield addonInfo
					}
				} as Writable, it)
			}
			def repoInfoMd5 = new File(pagesDir, "$addonId/addons.xml.md5")
			repoInfoMd5.text = repoInfo.text.md5()
		}
	}
}
