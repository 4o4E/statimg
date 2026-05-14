package top.e404.status.render

import java.io.File

object FontFileResolver {
    fun resolve(path: String): String {
        val configured = File(path)
        if (configured.isFile) return configured.path

        val fileName = configured.name
        return fontSearchDirs()
            .asSequence()
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().asSequence() }
            .firstOrNull { it.isFile && it.name.equals(fileName, ignoreCase = true) }
            ?.absolutePath
            ?: path
    }

    private fun fontSearchDirs(): List<File> = buildList {
        val home = System.getProperty("user.home")
        val windir = System.getenv("WINDIR")
        val localAppData = System.getenv("LOCALAPPDATA")

        add(File("font"))
        add(File("run/font"))
        if (!home.isNullOrBlank()) {
            add(File(home, ".local/share/fonts"))
            add(File(home, "Library/Fonts"))
        }
        if (!localAppData.isNullOrBlank()) {
            add(File(localAppData, "Microsoft/Windows/Fonts"))
        }
        if (!windir.isNullOrBlank()) {
            add(File(windir, "Fonts"))
        }
        add(File("/usr/share/fonts"))
        add(File("/usr/local/share/fonts"))
        add(File("/Library/Fonts"))
        add(File("/System/Library/Fonts"))
    }
}
