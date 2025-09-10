import org.gradle.api.Project

private val EapBranchRegex = Regex("^.*/(.*)-eap$")

fun Project.resolveVersion(ktorVersion: String): String {
    return listOfNotNull(
        ktorVersion,
        System.getenv("GIT_BRANCH")
            ?.let(EapBranchRegex::matchEntire)
            ?.groupValues?.get(1),
        findProperty("versionSuffix")
    ).joinToString("-")
}