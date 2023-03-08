package nl.jolanrensen.docProcessor

import org.junit.Test

class TestSample : DocProcessorFunctionalTest(name = "sample") {

    private val processors = listOf(
        "SAMPLE_DOC_PROCESSOR",
    )

    @Test
    fun todo() {
        TODO()
    }
}