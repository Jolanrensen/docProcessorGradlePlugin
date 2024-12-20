package nl.jolanrensen.docProcessor

import nl.jolanrensen.docProcessor.ProgrammingLanguage.KOTLIN
import java.io.File
import java.io.IOException

abstract class DocProcessorTest(name: String) {

    sealed interface Additional

    class AdditionalDocumentable(val documentableWrapper: DocumentableWrapper) : Additional

    class AdditionalDirectory(val relativePath: String = "src/main/kotlin/com/example/plugin") : Additional

    class AdditionalFile(
        val relativePath: String = "src/main/kotlin/com/example/plugin/file.txt",
        val contents: String,
    ) : Additional

    class AdditionalPath(val fullyQualifiedPath: String) : Additional

    fun createDocumentableWrapper(
        documentation: String,
        documentableSourceNoDoc: String,
        fullyQualifiedPath: String,
        docFileTextRange: IntRange,
        fileTextRange: IntRange,
        fullyQualifiedExtensionPath: String? = null,
        fullyQualifiedSuperPaths: List<String> = emptyList(),
        docIndent: Int = 0,
        fileName: String = "Test",
        imports: List<SimpleImportPath> = emptyList(),
        packageName: String = "com.example.plugin",
        language: ProgrammingLanguage = KOTLIN,
    ): DocumentableWrapper =
        DocumentableWrapper(
            docContent = documentation.asDocText().getDocContent(),
            programmingLanguage = language,
            imports = imports,
            rawSource = documentation + "\n" + documentableSourceNoDoc,
            fullyQualifiedPath = fullyQualifiedPath,
            fullyQualifiedExtensionPath = fullyQualifiedExtensionPath,
            fullyQualifiedSuperPaths = fullyQualifiedSuperPaths,
            file = File(
                "src/main/${if (language == KOTLIN) "kotlin" else "java"}/${
                    packageName.replace('.', '/')
                }/$fileName.${if (language == KOTLIN) "kt" else "java"}",
            ),
            docFileTextRange = docFileTextRange,
            docIndent = docIndent,
            annotations = emptyList(),
            fileTextRange = fileTextRange,
            origin = Unit,
        )

    fun String.textRangeOf(text: String): IntRange =
        indexOf(text).let { start ->
            start until (start + text.length)
        }

    @kotlin.jvm.Throws(IOException::class)
    fun processContent(
        documentableWrapper: DocumentableWrapper,
        processors: List<DocProcessor>,
        additionals: List<Additional> = emptyList(),
        processLimit: Int = 10_000,
    ): String {
        val allDocumentables = additionals
            .filterIsInstance<AdditionalDocumentable>()
            .map { it.documentableWrapper } + documentableWrapper

        val additionalPaths = additionals.filterIsInstance<AdditionalPath>()

        if (additionals.any { it is AdditionalDirectory || it is AdditionalFile }) TODO()

        val documentablesPerPath = allDocumentables
            .flatMap { doc -> doc.paths.map { it to doc } }
            .groupBy { it.first }
            .mapValues { it.value.map { it.second } }
            .toMutableMap()

        for (path in additionalPaths) {
            if (path.fullyQualifiedPath !in documentablesPerPath) {
                documentablesPerPath[path.fullyQualifiedPath] = emptyList()
            }
        }

        // Run all processors
        val modifiedDocumentables = processors.fold(
            initial = documentablesPerPath.toDocumentablesByPath(),
        ) { acc, processor ->
            println("Running processor: ${processor::class.qualifiedName}")
            processor.processSafely(processLimit = processLimit, documentablesByPath = acc)
        }

        val originalDoc = documentableWrapper.paths
            .mapNotNull { modifiedDocumentables.query(it, documentableWrapper) }
            .flatten()
            .firstOrNull {
                it.fullyQualifiedPath == documentableWrapper.fullyQualifiedPath &&
                    it.fullyQualifiedExtensionPath == documentableWrapper.fullyQualifiedExtensionPath &&
                    it.file == documentableWrapper.file &&
                    it.docFileTextRange == documentableWrapper.docFileTextRange
            } ?: error(
            "Original doc not found for ${
                documentableWrapper.fullyQualifiedPath
            } or ${
                documentableWrapper.fullyQualifiedExtensionPath
            }",
        )

        return originalDoc.docContent.toDocText(originalDoc.docIndent).value
    }
}
