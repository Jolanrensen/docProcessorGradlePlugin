package nl.jolanrensen.docProcessor

/**
 * TODO
 */
class CompletionInfo(
    val tag: String,
    val blockText: String? = "@$tag ",
    val presentableBlockText: String? = "@$tag",
    val moveCaretOffsetBlock: Int? = 0,
    val inlineText: String? = "{@$tag }",
    val presentableInlineText: String? = "{@$tag }",
    val moveCaretOffsetInline: Int? = -1,
    val tailText: String = "",
)
