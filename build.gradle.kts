import com.github.breadmoirai.githubreleaseplugin.GithubReleaseTask
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlSlurper
import groovy.xml.XmlUtil

plugins {
	id 'com.github.breadmoirai.github-release'
}

ext {
	localProperties = new Properties()
	def localPropertiesFile = file 'local.properties'
	if (localPropertiesFile.isFile()) {
		localPropertiesFile.withReader { localProperties.load it }
	}
	pagesDir = file 'docs'
}

subprojects {

	repositories {
		google()
		mavenCentral()
		mavenLocal()
		maven { url 'https://jitpack.io' }
	}

	ext {
		localProperties = localProperties
		pagesDir = pagesDir
	}

	tasks.withType(JavaCompile).configureEach {
		options.deprecation = true
	}
}

def genPages = tasks.register('genPages') {
	group = project.name
	outputs.dir pagesDir

	doFirst {
		delete pagesDir
	}

	doLast {
		def gitignore = file '.gitignore'
		def gitignorePages = "!$pagesDir.name/**"
		if (gitignore.isFile() && !gitignore.any { it == gitignorePages }) {
			gitignore.withWriterAppend { it.writeLine gitignorePages }
		}
		pagesDir.mkdirs()
	}
}

def genIndex = tasks.register('genIndex') {
	group = project.name
	inputs.dir pagesDir

	ext {
		repoInfo = new File(pagesDir, 'addons.xml')
		repoInfoMd5 = new File(pagesDir, 'addons.xml.md5')
		indexPage = new File(pagesDir, 'index.html')
	}
	outputs.files repoInfo, repoInfoMd5, indexPage
	dependsOn genPages

	doFirst {
		delete repoInfo, repoInfoMd5, indexPage
	}

	doLast {
		repoInfo.withWriter {
			XmlUtil.serialize(new StreamingMarkupBuilder().bind {
				'addons' {
					pagesDir.listFiles({ it.isDirectory() } as FileFilter).sort().each {
						def addonsInfo = new XmlSlurper().parse(new File(it, 'addons.xml'))
						mkp.yield addonsInfo.'addon'
					}
				}
			} as Writable, it)
		}
		repoInfoMd5.text = repoInfo.text.md5()
		indexPage.withWriter {
			def builder = new StreamingMarkupBuilder()
			builder.setUseDoubleQuotes true
			it << '<!DOCTYPE html>' << builder.bind {
				'html'('lang': 'en', 'style': 'color-scheme: light dark;') {

					'head' {

						'meta'('charset': 'utf-8')

						'title'(project.name)
					}

					'body' {

						'table' {
							pagesDir.listFiles({ it.isFile() && it != indexPage } as FileFilter).sort().each {
								def name = XmlUtil.escapeXml it.name
								def date = new Date(it.lastModified()).format 'yyyy-MM-dd HH:mm'
								def size = "${it.length()}B"

								'tr' {

									'td' { 'a'('href': name, name) }

									'td'(date)

									'td'('style': 'text-align: right;', size)
								}
							}
						}
					}
				}
			}
		}
	}
}

tasks.named('githubRelease', GithubReleaseTask) {
	def binaryWrapper = project(':binaryWrapper')
	def elementumService = project(':ElementumService')
	def lt2httpService = project(':Lt2httpService')
	dependsOn elementumService.tasks.named('genPages'), lt2httpService.tasks.named('genPages')
	owner = localProperties.getProperty('github.owner')
	repo = localProperties.getProperty('github.repo')
	authorization = localProperties.getProperty('github.authorization')
	tagName = "v$elementumService.appVersionName"
	targetCommitish = localProperties.getProperty('github.targetCommitish')
	releaseName = "$elementumService.name $elementumService.appVersionName"
	body = """$binaryWrapper.name ${libs.versions.binaryWrapper.version.get()}
			|$lt2httpService.name $lt2httpService.appVersionName
			|${localProperties.getProperty('github.body') ?: ''}""".stripMargin()
	prerelease = true
	releaseAssets.from genIndex.map { task ->
		pagesDir.listFiles({
			it.isFile() && it != task.repoInfo && it != task.repoInfoMd5 && it != task.indexPage
		} as FileFilter).sort()
	}
}
