import groovy.xml.MarkupBuilder
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlSlurper
import groovy.xml.XmlUtil
import org.codehaus.groovy.runtime.EncodingGroovyMethods
import java.io.FileFilter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
	id("com.github.breadmoirai.github-release")
}

val localProperties by extra {
	Properties().also { file("local.properties").takeIf(File::isFile)?.bufferedReader()?.use(it::load) }
}

val pagesDir by extra {
	file("docs")
}

subprojects {

	repositories {
		google()
		mavenCentral()
		mavenLocal()
		maven("https://jitpack.io")
	}

	tasks.withType<JavaCompile>().configureEach {
		options.isDeprecation = true
	}
}

val genPages = tasks.register("genPages") {
	group = project.name
	outputs.dir(pagesDir)

	doFirst {
		delete(pagesDir)
	}

	doLast {
		val gitignore = file(".gitignore")
		val gitignorePages = "!${pagesDir.name}/**"
		if (gitignore.isFile() && gitignore.useLines { lines -> lines.all { it != gitignorePages } }) {
			gitignore.appendText("%s%n".format(gitignorePages))
		}
		pagesDir.mkdirs()
	}
}

val genIndex = tasks.register("genIndex") {
	val repoInfo = File(pagesDir, "addons.xml")
	val repoInfoMd5 = File(pagesDir, "addons.xml.md5")
	val indexPage = File(pagesDir, "index.html")
	group = project.name
	inputs.dir(pagesDir)
	outputs.files(repoInfo, repoInfoMd5, indexPage)
	dependsOn(genPages)

	doFirst {
		delete(repoInfo, repoInfoMd5, indexPage)
	}

	doLast {
		val repoInfoText = StringWriter().use { out ->
			MarkupBuilder(out).run {
				withGroovyBuilder {
					"addons" {
						val parser = XmlSlurper()
						val builder = StreamingMarkupBuilder()
						pagesDir.listFiles(FileFilter { it.isDirectory() })?.sorted()?.forEach {
							val addonsInfo = parser.parse(File(it, repoInfo.name))
							mkp.yieldUnescaped(builder.bindNode(addonsInfo.children()))
						}
					}
				}
			}
			XmlUtil.serialize("$out")
		}
		repoInfo.writeText(repoInfoText)
		repoInfoMd5.writeText(EncodingGroovyMethods.md5(repoInfoText))
		indexPage.printWriter().use { out ->
			out.write("<!DOCTYPE html>")
			MarkupBuilder(out).run {
				doubleQuotes = true
				withGroovyBuilder {
					"html"("lang" to "en", "style" to "color-scheme: light dark;") {

						"head" {

							"meta"("charset" to "utf-8")

							"title" { mkp.yield(project.name) }
						}

						"body" {

							"table" {
								val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm")
								pagesDir.listFiles(FileFilter { it.isFile() && it != indexPage })?.sorted()?.forEach {
									val name = it.name
									val date = formatter.format(Date(it.lastModified()))
									val size = "${it.length()}B"

									"tr" {

										"td" { "a"("href" to name) { mkp.yield(name) } }

										"td" { mkp.yield(date) }

										"td"("style" to "text-align: right;") { mkp.yield(size) }
									}
								}
							}
						}
					}
				}
			}
		}
	}
}

tasks.githubRelease {
	val binaryWrapper = project(":binaryWrapper")
	val elementumService = project(":ElementumService")
	val lt2httpService = project(":Lt2httpService")
	dependsOn(elementumService.tasks.named("genPages"), lt2httpService.tasks.named("genPages"))
	owner = localProperties.getProperty("github.owner")
	repo = localProperties.getProperty("github.repo")
	authorization = localProperties.getProperty("github.authorization")
	tagName = "v${elementumService.extra["appVersionName"]}"
	targetCommitish = localProperties.getProperty("github.targetCommitish")
	releaseName = "${elementumService.name} ${elementumService.extra["appVersionName"]}"
	body = """${binaryWrapper.name} ${libs.versions.binaryWrapper.version.get()}
		|${lt2httpService.name} ${lt2httpService.extra["appVersionName"]}
		|${localProperties.getProperty("github.body") ?: ""}""".trimMargin()
	prerelease = true
	releaseAssets.from(genIndex.map { task ->
		pagesDir.listFiles(FileFilter { it.isFile() && it !in task.outputs.files })?.sorted() ?: listOf()
	})
}
