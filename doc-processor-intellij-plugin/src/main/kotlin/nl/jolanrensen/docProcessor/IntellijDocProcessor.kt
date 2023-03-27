package nl.jolanrensen.docProcessor

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil.processElements
import kotlinx.coroutines.*
import nl.jolanrensen.docProcessor.defaultProcessors.*
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.psi.KtDeclaration
import java.io.File
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class IntellijDocProcessor(private val project: Project, private val processLimit: Int = 10_000) {

    @OptIn(ExperimentalTime::class)
    fun processFiles(psiFiles: List<PsiFile>) {
        // TODO resolveKDocLink()
        println("IntellijDocProcessor processFiles running!")

        val (sourceDocs, docWrapperToPsi) =
            measureTimedValue {
                getDocumentableWrappersWithUuidMap(psiFiles)
            }.let {
                println("getDocumentableWrappersWithUuidMap took ${it.duration} (${it.value.second.size} documentables)")
                it.value
            }

        // Find all processors
        Thread.currentThread().contextClassLoader = this.javaClass.classLoader
        val processors = findProcessors(
            listOf(
                // TODO make customizable
                INCLUDE_DOC_PROCESSOR,
                INCLUDE_FILE_DOC_PROCESSOR,
                INCLUDE_ARG_DOC_PROCESSOR,
                COMMENT_DOC_PROCESSOR,
                SAMPLE_DOC_PROCESSOR,
            )
        )

        // Run all processors
        val (modifiedDocumentables, time) = measureTimedValue {
            processors.fold(sourceDocs) { acc, processor ->
                processor.processSafely(processLimit = processLimit, documentablesByPath = acc)
            }
        }
        println("Running all processors took $time")

        measureTime {
            for (doc in modifiedDocumentables.flatMap { it.value.filter { it.isModified } }) {
                val psiElement = docWrapperToPsi[doc.identifier] ?: continue

                if (doc.docContent.isEmpty()) {
                    psiElement.docComment?.delete()
                    continue
                }

                val newComment = when (doc.programmingLanguage) {
                    ProgrammingLanguage.KOTLIN -> KDocElementFactory(project)
                        .createKDocFromText(
                            doc.docContent.toDoc()
                        )

                    ProgrammingLanguage.JAVA -> PsiElementFactory.getInstance(project)
                        .createDocCommentFromText(
                            doc.docContent.toDoc()
                        )
                }

                if (psiElement.docComment != null) {
                    psiElement.docComment?.replace(newComment)
                } else {
                    psiElement.addBefore(newComment, psiElement.firstChild)
                }
            }
        }.also {
            println("Updating psi took $it")
        }
        println("IntellijDocProcessor processFiles done!")
    }

    private fun getDocumentableWrappersWithUuidMap(
        psiFiles: List<PsiFile>,
    ): Pair<Map<String, List<DocumentableWrapper>>, Map<UUID, PsiElement>> {
        val uuidMap = Collections.synchronizedMap(mutableMapOf<UUID, PsiElement>())
        val documentablesPerPath = Collections.synchronizedMap(mutableMapOf<String, MutableList<DocumentableWrapper>>())

        for (file in psiFiles) {
            val klass = when (file.language) {
                is KotlinLanguage -> KtDeclaration::class.java
                is JavaLanguage -> PsiDocCommentOwner::class.java
                else -> continue
            }
            processElements(file, klass) { psiElement ->
                val wrapper = DocumentableWrapper.createFromIntellijOrNull(psiElement)
                if (wrapper != null) {
                    uuidMap[wrapper.identifier] = psiElement

                    documentablesPerPath.getOrPut(wrapper.fullyQualifiedPath) { mutableListOf() }
                        .add(wrapper)

                    if (wrapper.fullyQualifiedExtensionPath != null) {
                        documentablesPerPath.getOrPut(wrapper.fullyQualifiedExtensionPath!!) { mutableListOf() }
                            .add(wrapper)
                    }
                } else {
                    psiElement.kotlinFqName?.asString()?.let { fqName ->
                        documentablesPerPath.getOrPut(fqName) { mutableListOf() }
                    }
                }

                true
            }
        }

        return Pair(documentablesPerPath, uuidMap)
    }

    private fun getModifiedDocumentablesPerFile(
        modifiedSourceDocs: Map<String, List<DocumentableWrapper>>,
    ): Map<File, List<DocumentableWrapper>> =
        modifiedSourceDocs
            .entries
            .flatMap {
                it.value.filter {
                    it.isModified // filter out unmodified documentables
                }
            }
            .groupBy { it.file }
}