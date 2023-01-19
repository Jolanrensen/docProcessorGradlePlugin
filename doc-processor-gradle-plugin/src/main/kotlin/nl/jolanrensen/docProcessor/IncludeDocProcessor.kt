package nl.jolanrensen.docProcessor

class IncludeDocProcessor: DocProcessor {

    private val includeRegex = Regex("""@include(\s+)(\[?)(.+)(]?)""")

    private val DocumentableWithSource.hasInclude
        get() = tags.any { it == "include" }

    override fun process(docsByPath: Map<String, List<DocumentableWithSource>>): Map<String, List<DocumentableWithSource>> {
        val mutableSourceDocs = docsByPath
            .mapValues { (_, docs) -> docs.toMutableList() }

        var i = 0
        while (mutableSourceDocs.any { it.value.any { it.hasInclude } }) {
            if (i++ > 10_000) {
                val circularRefs = mutableSourceDocs
                    .filter { it.value.any { it.hasInclude } }
                    .entries
                    .joinToString("\n\n") { (path, documentables) ->
                        buildString {
                            appendLine("$path:")
                            appendLine(documentables.joinToString("\n\n") {
                                it.queryFile().getDocContent().toDoc(4)
                            })
                        }
                    }
                error("Circular references detected in @include statements:\n$circularRefs")
            }

            mutableSourceDocs
                .filter { it.value.any { it.hasInclude } }
                .forEach { (path, documentables) ->
                    documentables.replaceAll { documentable ->
                        val kdoc = documentable.docContent
                        val processedKdoc = kdoc.replace(includeRegex) { match ->
                            // get the full include path
                            val includePath = match.value.getAtSymbolTargetName("include")
                            val parentPath = path.take(path.lastIndexOf('.').coerceAtLeast(0))
                            val includeQuery = expandInclude(include = includePath, parent = parentPath)

                            // query the tree for the include path
                            val queried = mutableSourceDocs[includeQuery]

                            // replace the include statement with the kdoc of the queried node (if found)
                            queried
                                ?.firstOrNull { it != documentable }
                                ?.docContent
                                ?: match.value
                        }

                        val wasModified = kdoc != processedKdoc

                        if (wasModified) {
                            val hasInclude = processedKdoc
                                .split('\n')
                                .any { it.trim().startsWith("@include") }

                            val newTags = documentable.tags.let {
                                if (hasInclude) it + "include"
                                else it - "include"
                            }

                            documentable.copy(
                                kdocContent = processedKdoc,
                                tags = newTags,
                                isModified = true,
                            )
                        } else {
                            documentable
                        }
                    }
                }
        }
        return mutableSourceDocs
    }
}