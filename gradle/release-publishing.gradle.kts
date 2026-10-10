import org.gradle.plugins.signing.Sign
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

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
val groupPath = artifactGroup.replace('.', '/')
val mavenRepositoryRoot = System.getProperty("maven.repo.local")
    ?.let { file(it) }
    ?: file("${System.getProperty("user.home")}/.m2/repository")
val mavenLocalRoot = mavenRepositoryRoot.resolve(groupPath)
val coreArtifactDir = mavenLocalRoot.resolve("flowbus-core/$artifactVersion")
val androidArtifactDir = mavenLocalRoot.resolve("flowbus/$artifactVersion")

fun Project.remoteMavenPublicationRequested(): Boolean =
    gradle.taskGraph.allTasks.any { task ->
        task.name.startsWith("publish") &&
            task.name != "publishToMavenLocal" &&
            !task.name.endsWith("ToMavenLocal")
    }

subprojects {
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
