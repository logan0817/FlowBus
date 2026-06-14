import org.gradle.plugins.signing.Sign

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
val mavenLocalRoot = file("${System.getProperty("user.home")}/.m2/repository/$groupPath")
val coreArtifactDir = mavenLocalRoot.resolve("flowbus-core/$artifactVersion")
val androidArtifactDir = mavenLocalRoot.resolve("flowbus/$artifactVersion")

fun Project.remoteMavenPublicationRequested(): Boolean =
    gradle.taskGraph.allTasks.any { task ->
        task.name.startsWith("publish") &&
            task.name != "publishToMavenLocal" &&
            !task.name.endsWith("ToMavenLocal")
    }

subprojects {
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

gradle.projectsEvaluated {
    mavenLocalPublishTasks.forEach { taskPath ->
        tasks.getByPath(taskPath).mustRunAfter(cleanMavenLocalArtifacts)
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
