import com.vanniktech.maven.publish.DeploymentValidation
import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar

plugins {
    // This plugin applies the correct loom variant based on the Minecraft version
    id("dev.kikugie.loom-back-compat")
    id("com.vanniktech.maven.publish")
}

// DO NOT set group = ...!
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()
    modCompileOnly("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")

    api("io.github.projectunified:faststats-core:${property("deps.faststats")}")
    api("io.github.projectunified:faststats-config:${property("deps.faststats")}")
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true, validateDeployment = DeploymentValidation.VALIDATED)
    if (gradle.startParameter.taskNames.contains("publishToMavenCentral")) {
        signAllPublications()
    }

    configure(
        JavaLibrary(
            javadocJar = JavadocJar.Javadoc(),
            sourcesJar = com.vanniktech.maven.publish.SourcesJar.Sources(),
        )
    )

    coordinates("${property("mod.group")}", "${property("mod.id")}", "${property("mod.version")}+${stonecutter.current.version}")

    pom {
        name = "FastStats Fabric"
        description = "Fabric and Quilt platform for the FastStats SDK"
        url = "https://github.com/ProjectUnified/FastStats"

        licenses {
            license {
                name = "MIT License"
                url = "https://github.com/ProjectUnified/FastStats/blob/master/LICENSE"
            }
        }

        developers {
            developer {
                name = "HSGamer"
                email = "huynhqtienvtag@gmail.com"
                url = "https://github.com/HSGamer"
            }
            developer {
                name = "FastStats"
                url = "https://github.com/faststats-dev/faststats-java"
            }
        }

        issueManagement {
            system = "github"
            url = "https://github.com/ProjectUnified/FastStats/issues"
        }

        scm {
            connection = "scm:git:https://github.com/ProjectUnified/FastStats.git"
            developerConnection = "scm:git:git@github.com:ProjectUnified/FastStats.git"
            url = "https://github.com/ProjectUnified/FastStats"
        }
    }
}
