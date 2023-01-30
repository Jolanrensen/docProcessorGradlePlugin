package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocumentableWithSource
import nl.jolanrensen.docProcessor.ProcessDocsAction
import nl.jolanrensen.docProcessor.TagDocProcessor

const val INCLUDE_ARG_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.IncludeArgDocProcessor"

/**
 * Include arg doc processor.
 *
 * TODO
 */
class IncludeArgDocProcessor : TagDocProcessor() {

    private val useArgumentTag = "includeArg"

    private val declareArgumentTag = "arg"
    override fun tagIsSupported(tag: String): Boolean =
        tag in listOf(useArgumentTag, declareArgumentTag)

    override fun shouldContinue(
        i: Int,
        anyModifications: Boolean,
        parameters: ProcessDocsAction.Parameters,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): Boolean {
        if (i >= parameters.processLimit)
            onProcesError(filteredDocumentables, allDocumentables)

        // We can break out of the recursion if there are no more changes. We don't need to throw an error if an
        // argument is not found, as it might be defined in a different file.
        if (i > 0 && !anyModifications) {
            val argsNotFound = argsNotFound.flatMap { (documentable, args) ->
                args.map { arg -> "`${documentable.path}` -> @$useArgumentTag $arg" }
            }.joinToString("\n")
            println("IncludeArgDocProcessor WARNING: Could not find all arguments:[\n$argsNotFound\n]")
            return false
        }

        return super.shouldContinue(i, anyModifications, parameters, filteredDocumentables, allDocumentables)
    }

    // @arg map for path -> arg name -> value
    private val argMap: MutableMap<DocumentableWithSource, MutableMap<String, String>> = mutableMapOf()

    private val argsNotFound: MutableMap<DocumentableWithSource, MutableSet<String>> = mutableMapOf()
    override fun processTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): String {
        val trimmedTagWithContent = tagWithContent
            .trimStart()
            .removePrefix("{")
            .removeSuffix("}")

        val isArgDeclaration = trimmedTagWithContent.startsWith("@$declareArgumentTag")

        if (isArgDeclaration) {
            val argDeclaration = trimmedTagWithContent
                .removePrefix("@$declareArgumentTag")
                .trimStart()
            val name = argDeclaration.takeWhile { !it.isWhitespace() }
            val value = argDeclaration.removePrefix(name).trimStart()

            argMap.getOrPut(documentable) { mutableMapOf() }[name] = value
            argsNotFound.getOrPut(documentable) { mutableSetOf() } -= name
            return ""
        } else {
            val argTarget = trimmedTagWithContent
                .removePrefix("@$useArgumentTag")
                .trim()

            val arg = argMap[documentable]?.get(argTarget)
            if (arg == null) {
                argsNotFound.getOrPut(documentable) { mutableSetOf() } += argTarget
            }

            return arg ?: tagWithContent
        }
    }

    override fun processInnerTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): String = processTagWithContent(
        tagWithContent = tagWithContent,
        path = path,
        documentable = documentable,
        docContent = docContent,
        filteredDocumentables = filteredDocumentables,
        allDocumentables = allDocumentables,
    )
}