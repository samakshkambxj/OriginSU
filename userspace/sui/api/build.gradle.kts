import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.refine) apply false
}

apply(from = "manifest.gradle.kts")

val apiVersionName = extra["api_version_name"] as String
val groupIdBase = "dev.rikka.shizuku"
val pomUrl = "https://github.com/RikkaApps/Shizuku-API"
val pomScmUrl = "https://github.com/RikkaApps/Shizuku-API"
val pomScmConnection = "scm:git@github.com:RikkaApps/Shizuku-API.git"
val pomLicenceName = "MIT License"
val pomLicenceUrl = "https://github.com/RikkaApps/Shizuku-API/blob/master/LICENSE"
val pomDeveloperName = "Rikka"
val pomDeveloperUrl = "https://github.com/RikkaW"

allprojects {
    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }
}

subprojects {
    group = groupIdBase
    version = apiVersionName

    afterEvaluate {
        if ((findProperty("publishLibrary") as? Boolean) != true) {
            return@afterEvaluate
        }

        pluginManager.apply("maven-publish")
        pluginManager.apply("signing")

        println("${displayName}: ${group}:${project.name}:${version}")

        extensions.configure<LibraryExtension>("android") {
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                    withJavadocJar()
                }
            }
        }

        val pomName = extra["POM_NAME"] as String
        val pomDescription = extra["POM_DESCRIPTION"] as String

        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    groupId = project.group.toString()
                    artifactId = project.name
                    version = apiVersionName
                    afterEvaluate {
                        from(components["release"])
                    }
                    pom {
                        name.set(pomName)
                        description.set(pomDescription)
                        url.set(pomUrl)
                        licenses {
                            license {
                                name.set(pomLicenceName)
                                url.set(pomLicenceUrl)
                            }
                        }
                        developers {
                            developer {
                                name.set(pomDeveloperName)
                                url.set(pomDeveloperUrl)
                            }
                        }
                        scm {
                            connection.set(pomScmConnection)
                            url.set(pomScmUrl)
                        }
                    }
                }
            }
            repositories {
                maven {
                    name = "ossrh"
                    url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                    credentials(PasswordCredentials::class)
                }
            }
        }

        extensions.configure<SigningExtension> {
            val signingKey = findProperty("signingKey") as String?
            val signingPassword = findProperty("signingPassword") as String?
            val secretKeyRingFile = findProperty("signing.secretKeyRingFile") as String?
            val publishing = extensions.getByType<PublishingExtension>()

            when {
                secretKeyRingFile != null && file(secretKeyRingFile).exists() -> sign(publishing.publications)
                signingKey != null -> {
                    useInMemoryPgpKeys(signingKey, signingPassword)
                    sign(publishing.publications)
                }
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
