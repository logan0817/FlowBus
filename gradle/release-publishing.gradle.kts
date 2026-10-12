import org.gradle.plugins.signing.Sign
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.provider.Property

val mavenLocalPublishTasks = listOf(
    ":flowbus-core:publishToMavenLocal",
    ":library-android:publishToMavenLocal"
)
val mavenCentralPublishTasks = listOf(
    ":flowbus-core:publishAllPublicationsToMavenCentralRepository",
    ":library-android:publishAllPublicationsToMavenCentralRepository"
)
val artifactGroup = providers.gradleProperty("GROUP").get()
val artifactVersion = providers.gradleProperty("VERSION_NAME").get()
val centralDeploymentProject = rootProject.name
val centralPublications = mapOf(":flowbus-core" to "flowbus-core", ":library-android" to "flowbus")
val groupPath = artifactGroup.replace('.', '/')
val mavenRepositoryRoot = System.getProperty("maven.repo.local")
    ?.let { file(it) }
    ?: file("${System.getProperty("user.home")}/.m2/repository")
val mavenLocalRoot = mavenRepositoryRoot.resolve(groupPath)
val coreArtifactDir = mavenLocalRoot.resolve("flowbus-core/$artifactVersion")
val androidArtifactDir = mavenLocalRoot.resolve("flowbus/$artifactVersion")

fun Task.stagingCoordinate(name: String): Property<String> {
    val value = property(name)
    check(value is Property<*>) { "Central staging task no longer exposes $name as a Property; review the publishing plugin." }
    @Suppress("UNCHECKED_CAST")
    return value as Property<String>
}

val verifyCentralDeploymentName = tasks.register("verifyCentralDeploymentName") {
    group = "verification"
    description = "校验 Central 批次包含项目名，且实际 Maven 坐标不变；不签名、不上传。"
    doLast {
        centralPublications.forEach { (projectPath, expectedArtifactId) ->
            val module = project(projectPath)
            val staging = module.tasks.named("createStagingRepository").get()
            check(staging.stagingCoordinate("projectGroup").get() == artifactGroup &&
                staging.stagingCoordinate("artifactId").get() == centralDeploymentProject &&
                staging.stagingCoordinate("version").get() == artifactVersion) {
                "$projectPath Central staging identity must be $artifactGroup:$centralDeploymentProject:$artifactVersion."
            }
            val publications = module.extensions.getByType<PublishingExtension>()
                .publications.withType<MavenPublication>().toList()
            check(publications.isNotEmpty() && publications.all {
                it.groupId == artifactGroup && it.artifactId == expectedArtifactId && it.version == artifactVersion
            }) { "$projectPath Maven coordinates must remain $artifactGroup:$expectedArtifactId:$artifactVersion." }
        }
        logger.lifecycle("Central deployment name: $artifactGroup-$centralDeploymentProject-$artifactVersion")
    }
}

fun Project.remoteMavenPublicationRequested(): Boolean =
    gradle.taskGraph.allTasks.any { task ->
        task.name.startsWith("publish") &&
            task.name != "publishToMavenLocal" &&
            !task.name.endsWith("ToMavenLocal")
    }

subprojects {
    if (path in centralPublications) {
        afterEvaluate {
            tasks.named("createStagingRepository").configure {
                // 插件 0.33.0 按暂存任务的去重坐标生成批次名称，尚无公开的名称配置。
                // 等模块配置完成后只统一暂存标识，不改 MavenPublication；升级插件时须重新验证。
                stagingCoordinate("artifactId").set(centralDeploymentProject)
                dependsOn(verifyCentralDeploymentName)
            }
        }
    }
    // 排序约束放在真正写仓库的叶任务上，聚合任务的约束不会传递给它的依赖。
    tasks.withType<PublishToMavenLocal>().configureEach {
        mustRunAfter(rootProject.tasks.matching { it.name == "cleanMavenLocalArtifacts" })
    }
    tasks.withType<PublishToMavenRepository>().configureEach {
        mustRunAfter(rootProject.tasks.named("clean"))
    }
    plugins.withId("signing") {
        tasks.withType<Sign>().configureEach {
            onlyIf("remote Maven publication requires signatures") {
                rootProject.remoteMavenPublicationRequested()
            }
        }
    }

    tasks.matching { it.name == "publishAllPublicationsToMavenCentralRepository" }.configureEach {
        mustRunAfter(rootProject.tasks.named("clean"))
    }
}

val cleanMavenLocalArtifacts = tasks.register("cleanMavenLocalArtifacts") {
    group = "publishing"
    description = "Remove this release version from Maven local before publishing fresh artifacts."
    doLast {
        delete(coreArtifactDir, androidArtifactDir)
    }
}

tasks.register("publishToMavenLocal") {
    group = "publishing"
    description = "Local verification entry: publish flowbus-core and flowbus to Maven local from the main FlowBus project."
    dependsOn(cleanMavenLocalArtifacts)
    dependsOn(mavenLocalPublishTasks)
}

tasks.register("publishToMavenCentral") {
    group = "publishing"
    description = "Upload flowbus-core and flowbus to a Maven Central Portal deployment from the main FlowBus project."
    dependsOn(mavenCentralPublishTasks)
}

tasks.register("releaseToMavenCentral") {
    group = "publishing"
    description = "Compatibility release entry: clean, then upload a Central Portal deployment for manual release or drop."
    dependsOn("clean", "publishToMavenCentral")
}

tasks.named("publishToMavenCentral") {
    mustRunAfter("clean")
}
