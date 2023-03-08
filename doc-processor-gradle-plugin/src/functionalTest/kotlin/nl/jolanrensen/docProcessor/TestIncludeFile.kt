package nl.jolanrensen.docProcessor

import org.junit.Test

class TestIncludeFile : DocProcessorFunctionalTest(name = "includeFile") {

    private val processors = listOf(
        "INCLUDE_FILE_DOC_PROCESSOR",
    )

    @Test
    fun todo() {
        TODO()
    }
}