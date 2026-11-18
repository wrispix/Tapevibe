package com.tapevibe.data

import com.tapevibe.model.Project
import kotlinx.serialization.json.Json
import java.io.File

data class ProjectInfo(val id: String, val name: String, val modified: Long)

/**
 * Projects live in `<root>/<id>/project.json` with audio in `<root>/<id>/samples/`.
 */
class ProjectStore(private val root: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    private val lastFile get() = File(root, ".last")

    init {
        root.mkdirs()
    }

    fun projectDir(id: String) = File(root, id)
    fun samplesDir(id: String) = File(projectDir(id), "samples").apply { mkdirs() }
    fun samplePath(id: String, file: String?): String? =
        file?.let { File(samplesDir(id), it) }?.takeIf { it.isFile }?.absolutePath

    fun newId(name: String): String {
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(24).ifEmpty { "project" }
        var id = "$slug-${System.currentTimeMillis().toString(36)}"
        var n = 1
        while (projectDir(id).exists()) id = "$slug-${System.currentTimeMillis().toString(36)}-${n++}"
        return id
    }

    fun save(id: String, project: Project) {
        val dir = projectDir(id).apply { mkdirs() }
        val tmp = File(dir, "project.json.tmp")
        tmp.writeText(json.encodeToString(Project.serializer(), project))
        val target = File(dir, "project.json")
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
        lastFile.writeText(id)
    }

    fun load(id: String): Project? = runCatching {
        json.decodeFromString(Project.serializer(), File(projectDir(id), "project.json").readText())
    }.getOrNull()

    fun lastProjectId(): String? = lastFile.takeIf { it.isFile }?.readText()?.trim()?.takeIf {
        File(projectDir(it), "project.json").isFile
    }

    fun list(): List<ProjectInfo> = root.listFiles().orEmpty()
        .filter { File(it, "project.json").isFile }
        .mapNotNull { dir ->
            val p = load(dir.name) ?: return@mapNotNull null
            ProjectInfo(dir.name, p.name, File(dir, "project.json").lastModified())
        }
        .sortedByDescending { it.modified }

    fun delete(id: String) {
        projectDir(id).deleteRecursively()
    }

    /** Copies a project's directory (including samples) under a new id. */
    fun duplicate(id: String, newName: String): String? {
        val p = load(id) ?: return null
        val newId = newId(newName)
        projectDir(id).copyRecursively(projectDir(newId))
        save(newId, p.copy(name = newName))
        return newId
    }

    /** Removes sample files no track references. */
    fun pruneSamples(id: String, project: Project) {
        val used = project.tracks.mapNotNull { it.sampleFile }.toSet()
        samplesDir(id).listFiles().orEmpty().filter { it.name !in used }.forEach { it.delete() }
    }

    fun encode(project: Project): String = json.encodeToString(Project.serializer(), project)
    fun decode(text: String): Project = json.decodeFromString(Project.serializer(), text)
}
