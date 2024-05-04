import com.android.build.gradle.tasks.MergeSourceSetFolders
import com.android.build.gradle.tasks.PackageApplication
import groovy.json.JsonOutput
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlSlurper
import groovy.xml.XmlUtil
import org.apache.tools.ant.types.Commandline

import java.util.zip.ZipFile

plugins {
	id 'com.android.application'
	id 'org.jetbrains.kotlin.android'
}

ext {
	srcGen = layout.buildDirectory.file('src').get().asFile.path
	srcMainGen = "$srcGen/main"
	srcMainAssetsGen = "$srcMainGen/assets"
	srcMainAbiHeadsGen = "$srcMainGen/abiHeads"
	srcMainJniLibsGen = "$srcMainGen/jniLibs"
	appVersionCode = System.getProperty('version.code') as Integer
			?: localProperties.getProperty('elementum.version.code') as Integer
			?: libs.versions.elementum.versionCode.get() as int
	appVersionName = System.getProperty('version.name')
			?: localProperties.getProperty('elementum.version.name')
			?: "$appVersionCode"
	addonId = 'plugin.video.elementum'
	addonZip = System.getProperty('elementum.addon.zip') ?: localProperties.getProperty('elementum.addon.zip')
	addonDir = addonZip?.with {
		layout.buildDirectory.file(file(it).name - ~/\.[^.]+$/).get().asFile.path
	}
	addonIdDir = addonDir?.with { "$it/$addonId" }
	addonBinDir = addonIdDir?.with { "$it/resources/bin" }
	androidClientZip = layout.buildDirectory.file("$addonId-${appVersionName}.android_client.zip").get().asFile
	abiBins = [
			'arm64-v8a'  : 'android_arm64',
			'armeabi-v7a': 'android_arm',
			'x86'        : 'android_x86',
			'x86_64'     : 'android_x64'
	]
	isAddonBinLib = localProperties.getProperty('is.elementum.addon.bin.lib') as Boolean ?: addonZip?.with {
		new ZipFile(it)
	}?.withCloseable {
		abiBins.any { abi, bin ->
			it.getEntry("$addonId/resources/bin/$bin/elementum")?.isDirectory() != false
		}
	} ?: false
	mainIntentAction = 'android.intent.action.MAIN'
	argAddonInfo = '-addonInfo'
	argLocalPort = '-localPort'
	argTranslatePath = '-translatePath'
	localPort = 65220
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
	ndkVersion = libs.versions.ndkVersion.get()
	namespace = 'service.elementum.android'
	testNamespace = "${namespace}.test"
	testBuildType = System.getProperty('test.build.type') ?: 'debug'

	if (isAddonBinLib) {
		externalNativeBuild {

			cmake {
				version = libs.versions.cmake.get()
				path = 'CMakeLists.txt'
			}
		}
	}

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

		if (isAddonBinLib) {
			externalNativeBuild {

				cmake {
					arguments "-DPROJECT_NAME=$project.name",
							"-DSRC_MAIN_ABI_HEADS_GEN=$srcMainAbiHeadsGen",
							"-DSRC_MAIN_JNI_LIBS_GEN=$srcMainJniLibsGen"
				}
			}
		}
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

static String toMethodJson(method, params) {
	JsonOutput.toJson(['jsonrpc': '2.0', 'id': '1', 'method': method, 'params': params])
}

static String toGetAddonDetailsJson(addonId, properties) {
	toMethodJson('Addons.GetAddonDetails', ['addonid': addonId, 'properties': properties])
}

static String toSetAddonEnabledJson(addonId, enabled) {
	toMethodJson('Addons.SetAddonEnabled', ['addonid': addonId, 'enabled': enabled])
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

if (addonBinDir != null) {

	def patchAddon = tasks.register('patchAddon') {
		group = project.name
		inputs.file addonZip
		outputs.dir addonDir

		doFirst {
			delete addonDir
		}

		doLast {
			copy {
				from zipTree(addonZip)
				into addonDir
			}
			def serviceRepoAddonId = 'repository.service.elementum'
			def addonFile = file "$addonIdDir/addon.xml"
			def addonInfo = new XmlSlurper().parse addonFile
			def providerName = addonInfo.@'provider-name'
			addonInfo.@'version' = appVersionName
			addonInfo.'requires'.appendNode {
				'import'('addon': serviceRepoAddonId, 'optional': 'true')
			}
			addonFile.withWriter {
				XmlUtil.serialize(new StreamingMarkupBuilder().bind {
					mkp.yield addonInfo
				} as Writable, it)
			}
			def repoAddonInfoFormat = XmlUtil.serialize(new StreamingMarkupBuilder().bind {
				'addon'('id': '{id}', 'name': '{name}', 'provider-name': providerName, 'version': appVersionName) {

					'extension'('point': 'xbmc.addon.repository') {

						'dir' {

							'checksum'("{dataDir}addons.xml.md5")

							'datadir'('zip': 'true', '{dataDir}')

							'info'('compressed': 'false', "{dataDir}addons.xml")
						}
					}

					'extension'('point': 'xbmc.addon.metadata') {

						'platform'('all')

						'summary'('lang': 'en', '{summary}')
					}
				}
			})
			def startArgs = "$android.namespace, $mainIntentAction, , version:$appVersionName%s"
			def globalsLine = 'ADDON_VERSION = ADDON.getAddonInfo("version")'
			def globalsLinePatched = """# BEGIN $project.name-patched: globals
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
					|        detailsJson = '${toGetAddonDetailsJson('%s', ['installed', 'enabled'])}' % id
					|        enableJson = '${toSetAddonEnabledJson('%s', true)}' % id
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
					|    if populateGlobals():
					|        args = []
					|        if ADDON.getSetting('local_port') != '':
					|            args.append('-remotePort=' + ADDON.getSetting('local_port'))
					|        if ADDON.getSetting('remote_host') != '':
					|            args.append('-localHost=' + ADDON.getSetting('remote_host'))
					|        if ADDON.getSetting('remote_port') != '':
					|            args.append('$argLocalPort=' + ADDON.getSetting('remote_port'))
					|        startApp(args)
					|# END $project.name-patched: globals""".stripMargin()
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
					|    lockfile = os.path.join(ADDON_PATH, '.lockfile')
					|    if os.path.exists(lockfile):
					|        os.remove(lockfile)
					|# END $project.name-patched: stop""".stripMargin()
			def startLine = '            proc = subprocess.Popen(args, **kwargs)'
			def startLinePatched = """# BEGIN $project.name-patched: start
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
			def installLine = '        return xbmc.executebuiltin("InstallAddon(%s)" % addonId)'
			def installLinePatched = """# BEGIN $project.name-patched: install
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
					|# END $project.name-patched: install""".stripMargin()
			def infoLine = '        return info'
			def infoLinePatched = """# BEGIN $project.name-patched: info
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
					|# END $project.name-patched: info""".stripMargin()
			def translateLine = '        return translatePath(*args, **kwargs)'
			def translateLinePatched = """# BEGIN $project.name-patched: translate
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
					|# END $project.name-patched: translate""".stripMargin()
			def patches = [
					(file("$addonIdDir/resources/site-packages/elementum/addon.py"))     : [
							[globalsLine, globalsLinePatched]
					],
					(file("$addonIdDir/resources/site-packages/elementum/daemon.py"))    : [
							[versionLine, versionLinePatched],
							[binaryLine, binaryLinePatched],
							[stopLine, stopLinePatched],
							[startLine, startLinePatched]
					],
					(file("$addonIdDir/resources/site-packages/elementum/navigation.py")): [
							[retryLine, retryLinePatched]
					],
					(file("$addonIdDir/resources/site-packages/elementum/rpc.py"))       : [
							[installLine, installLinePatched],
							[infoLine, infoLinePatched],
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
				exclude '**/.*', "$addonId/resources/bin"
			}
		}
	}

	def genMainJniLibs = tasks.register('genMainJniLibs') {
		group = project.name
		inputs.dir addonDir
		if (isAddonBinLib) {
			outputs.dirs srcMainAbiHeadsGen, srcMainJniLibsGen
		} else {
			outputs.dir srcMainJniLibsGen
		}
		dependsOn patchAddon

		doFirst {
			delete srcMainAbiHeadsGen, srcMainJniLibsGen
		}

		doLast {
			abiBins.each { abi, bin ->
				if (isAddonBinLib) {
					copy {
						from "$addonBinDir/$bin"
						into "$srcMainAbiHeadsGen/$abi"
						include '*.h'
					}
				}
				copy {
					from "$addonBinDir/$bin"
					into "$srcMainJniLibsGen/$abi"
					if (isAddonBinLib) {
						include '*.so'
					} else {
						include 'elementum', 'lib*.so'
					}
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
		exclude '**/.*', "$addonId/resources/bin"

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
