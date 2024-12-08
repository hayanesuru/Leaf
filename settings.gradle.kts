pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "leaf"
for (name in listOf("Leaf-API", "Leaf-Server", "paper-api-generator")) {
    val projName = name.lowercase()
    include(projName)
    findProject(":$projName")!!.projectDir = file(name)
}

if (!file(".git").exists()) {
    val errorText = """

        =====================[ ERROR ]=====================
         The project directory is not a properly cloned Git repository.

         See https://github.com/Winds-Studio/Leaf/blob/HEAD/CONTRIBUTING.md
         for further information on building and modifying Leaf.
        ===================================================
    """.trimIndent()
    error(errorText)
}
