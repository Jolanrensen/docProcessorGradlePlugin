@file:OptIn(ExperimentalTypeInference::class)

package nl.jolanrensen.kdocInclude

import kotlin.experimental.ExperimentalTypeInference

/**
 * Fully qualified path tree DSL.
 *
 * @sample main
 */
fun main() {
    val tree = MutableFullyQualifiedPathTree.build<String> {
        packagePart("com", "example") {
            packagePart("other") {
                content = "Some free floating kdoc in another package."
            }
            clazz("MyClass") {
                content = "Some kdoc for MyClass."
                callable("myFunction") {
                    content = "Some kdoc for myFunction."
                }
            }
            packagePart("plugin") {
                clazz("Main") {
                    content = "Main Class"

                    callable("myFunction") {
                        content = "My Function"
                    }

                    clazz("InnerClass") {
                        content = "Inner Class"
                        callable("innerFunction") {
                            content = "Inner Class Callable"
                        }
                    }
                }
            }
        }
    }

    println(tree.readableString)
    tree.forEach { println(it.readableString) }

    println("----------------------------")

    val treeCopy = tree.copy()
    println(treeCopy.readableString)
    treeCopy.forEach { println(it.readableString) }

//    val queried = tree.query("com.example.plugin.Main.myFunction")!!
//    println(queried)
//    println(queried.getPathFromRoot().map { it.name })
//    println()
//    tree.forEach { println(it.readableString) }
}

interface FullyQualifiedPathTree<T> :
    FullyQualifiedPathNode<T>,
    HasPackageParts<T>,
    HasClasses<T>,
    HasCallables<T> {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T> build(
            @BuilderInference builder: MutablePackageNode<T>.() -> Unit
        ): FullyQualifiedPathTree<T> = MutableFullyQualifiedPathTree.build(builder)
    }

    override val parent: FullyQualifiedPathNode<T>?
        get() = null

    override val name: String
        get() = ""

    override val readableString: String
        get() = "FullyQualifiedPathTree(${
            buildList {
                if (packageParts.isNotEmpty()) {
                    this += "packageParts: ${packageParts.values.map { it.readableString }}"
                }
                if (classes.isNotEmpty()) {
                    this += "classes: ${classes.values.map { it.readableString }}"
                }
                if (callables.isNotEmpty()) {
                    this += "callables: ${callables.values.map { it.readableString }}"
                }
                if (content != null) {
                    this += "content: $content"
                }
            }.joinToString(", ")
        })"

    fun copy(): FullyQualifiedPathTree<T> = (this as MutableFullyQualifiedPathTree<T>).copy()

}

class MutableFullyQualifiedPathTree<T> internal constructor(
    override val packageParts: MutableMap<String, MutablePackageNode<T>> = mutableMapOf(),
    override val classes: MutableMap<String, MutableClassesNode<T>> = mutableMapOf(),
    override val callables: MutableMap<String, MutableCallablesNode<T>> = mutableMapOf(),
    override var content: T? = null,
) : MutablePackageNode<T>("", null, packageParts, classes, callables, content),
    FullyQualifiedPathTree<T> {

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T> build(
            @BuilderInference builder: MutableFullyQualifiedPathTree<T>.() -> Unit
        ): MutableFullyQualifiedPathTree<T> = MutableFullyQualifiedPathTree<T>().apply(builder)
    }

    override val parent: FullyQualifiedPathNode<T>?
        get() = null

    override val name: String
        get() = ""

    override val readableString: String
        get() = "FullyQualifiedPathTree(${
            buildList {
                if (packageParts.isNotEmpty()) {
                    this += "packageParts: ${packageParts.values.map { it.readableString }}"
                }
                if (classes.isNotEmpty()) {
                    this += "classes: ${classes.values.map { it.readableString }}"
                }
                if (callables.isNotEmpty()) {
                    this += "callables: ${callables.values.map { it.readableString }}"
                }
                if (content != null) {
                    this += "content: $content"
                }
            }.joinToString(", ")
        })"

    override fun removeNode(node: FullyQualifiedPathNode<T>) {
        packageParts.forEach { it.value.removeNode(node) }
        packageParts.filterValues { it == node }.keys.forEach { packageParts.remove(it) }
        classes.forEach { it.value.removeNode(node) }
        classes.filterValues { it == node }.keys.forEach { classes.remove(it) }
        callables.forEach { it.value.removeNode(node) }
        callables.filterValues { it == node }.keys.forEach { callables.remove(it) }
    }

    override fun removeNode(path: String) {
        query(path)?.let { removeNode(it) }
    }

    fun addNode(node: FullyQualifiedPathNode<T>) {
        val path = node.getPathFromRoot()
        val packageParts = path.filter { it::class == MutablePackageNode::class || it::class == PackageNode::class }
        val classes = path.filter { it::class == MutableClassesNode::class || it::class == ClassesNode::class }
        val callables =
            path.singleOrNull { it::class == MutableCallablesNode::class || it::class == CallablesNode::class }

        packagePart(*packageParts.map { it.name }.toTypedArray()) {
            clazz(*classes.map { it.name }.toTypedArray()) {
                callable(callables?.name) {
                    content = node.content
                }
            }
        }
    }

    override fun copy(): MutableFullyQualifiedPathTree<T> = build {
        this@MutableFullyQualifiedPathTree.forEach {
            addNode(it)
        }
    }
}

sealed interface FullyQualifiedPathNode<T> : Iterable<FullyQualifiedPathNode<T>> {
    val readableString: String
    val parent: FullyQualifiedPathNode<T>?
    val name: String

    var content: T? // always mutable

    val path: String
        get() = parent?.path.let { if (it.isNullOrEmpty()) "" else "$it." } + name

    val root: FullyQualifiedPathNode<T>
        get() = parent?.root ?: this

    fun query(path: String): FullyQualifiedPathNode<T>?

    fun getPathFromRoot(): List<FullyQualifiedPathNode<T>> = buildList<FullyQualifiedPathNode<T>> {
        var current: FullyQualifiedPathNode<T>? = this@FullyQualifiedPathNode
        while (current != null) {
            add(current)
            current = current.parent
        }
    }.reversed()
}


interface PackageNode<T> :
    FullyQualifiedPathNode<T>,
    HasPackageParts<T>,
    HasClasses<T>,
    HasCallables<T>

open class MutablePackageNode<T> internal constructor(
    override val name: String,
    override val parent: FullyQualifiedPathNode<T>?,
    override val packageParts: MutableMap<String, MutablePackageNode<T>> = mutableMapOf(),
    override val classes: MutableMap<String, MutableClassesNode<T>> = mutableMapOf(),
    override val callables: MutableMap<String, MutableCallablesNode<T>> = mutableMapOf(),
    override var content: T? = null,
) : MutableClassesNode<T>(name, parent, classes, callables, content),
    PackageNode<T> {

    override fun query(path: String): FullyQualifiedPathNode<T>? {
        val part = path.substringBefore(".")
        if (part == name) return this
        return (callables[part] ?: classes[part] ?: packageParts[part])
            ?.query(path.substringAfter("."))
    }

    override fun iterator(): Iterator<FullyQualifiedPathNode<T>> = iterator {
        yieldAll(super.iterator())
        packageParts.values.forEach { yieldAll(it) }
    }

    override fun removeNode(node: FullyQualifiedPathNode<T>) {
        packageParts.forEach { it.value.removeNode(node) }
        packageParts.filterValues { it == node }.keys.forEach { packageParts.remove(it) }
        super.removeNode(node)
    }

    override fun removeNode(path: String) {
        query(path)?.let { removeNode(it) }
    }

    private fun packagePart(
        name: String,
        @BuilderInference block: MutablePackageNode<T>.() -> Unit = {}
    ): MutablePackageNode<T> =
        also { require('.' !in name) { "Package must be split on ." } }
            .packageParts
            .getOrPut(name) { MutablePackageNode(name, this) }
            .apply(block)

    fun packagePart(
        vararg names: String,
        @BuilderInference block: MutablePackageNode<T>.() -> Unit = {}
    ): PackageNode<T> =
        names.fold(this) { acc, name -> acc.packagePart(name) }.apply(block)

    override val readableString: String
        get() = "PackageNode(${
            buildList {
                this += "name: $name"
                this += "path: $path"
                if (packageParts.isNotEmpty()) {
                    this += "packageParts: ${packageParts.values.map { it.readableString }}"
                }
                if (classes.isNotEmpty()) {
                    this += "classes: ${classes.values.map { it.readableString }}"
                }
                if (callables.isNotEmpty()) {
                    this += "callables: ${callables.values.map { it.readableString }}"
                }
                if (content != null) {
                    this += "content: $content"
                }
            }.joinToString(", ")
        })"

    override fun toString(): String = readableString
}

interface ClassesNode<T> :
    FullyQualifiedPathNode<T>,
    HasClasses<T>,
    HasCallables<T>

open class MutableClassesNode<T> internal constructor(
    override val name: String,
    override val parent: FullyQualifiedPathNode<T>?,
    override val classes: MutableMap<String, MutableClassesNode<T>> = mutableMapOf(),
    override val callables: MutableMap<String, MutableCallablesNode<T>> = mutableMapOf(),
    override var content: T? = null,
) : MutableCallablesNode<T>(name, parent, content),
    ClassesNode<T> {

    override fun query(path: String): FullyQualifiedPathNode<T>? {
        val part = path.substringBefore(".")
        if (part == name) return this
        return (callables[part] ?: classes[part])
            ?.query(path.substringAfter("."))
    }

    override fun iterator(): Iterator<FullyQualifiedPathNode<T>> = iterator {
        yieldAll(super.iterator())
        callables.values.forEach { yieldAll(it) }
        classes.values.forEach { yieldAll(it) }
    }

    override fun removeNode(node: FullyQualifiedPathNode<T>) {
        classes.forEach { it.value.removeNode(node) }
        classes.filterValues { it == node }.keys.forEach { classes.remove(it) }
        callables.forEach { it.value.removeNode(node) }
        callables.filterValues { it == node }.keys.forEach { callables.remove(it) }
        super.removeNode(node)
    }

    override fun removeNode(path: String) {
        query(path)?.let { removeNode(it) }
    }

    private fun clazz(
        name: String,
        @BuilderInference block: MutableClassesNode<T>.() -> Unit = {}
    ): MutableClassesNode<T> =
        also { require('.' !in name) { "Multiple classes must be split on ." } }
            .classes.getOrPut(name) { MutableClassesNode(name, this) }.apply(block)

    fun clazz(vararg names: String, @BuilderInference block: MutableClassesNode<T>.() -> Unit = {}): ClassesNode<T> =
        names.fold(this) { acc, name -> acc.clazz(name) }.apply(block)

    fun callable(
        name: String? = null,
        @BuilderInference block: MutableCallablesNode<T>.() -> Unit = {}
    ): CallablesNode<T> =
        if (name == null) {
            this
        } else {
            callables.getOrPut(name) { MutableCallablesNode(name, this) }
        }.apply(block)

    override val readableString: String
        get() = "ClassesNode(${
            buildList {
                this += "name: $name"
                this += "path: $path"
                if (classes.isNotEmpty()) {
                    this += "classes: ${classes.values.map { it.readableString }}"
                }
                if (callables.isNotEmpty()) {
                    this += "callables: ${callables.values.map { it.readableString }}"
                }
                if (content != null) {
                    this += "content: $content"
                }
            }.joinToString(", ")
        })"

    override fun toString(): String = readableString
}

interface CallablesNode<T> :
    FullyQualifiedPathNode<T>

open class MutableCallablesNode<T> internal constructor(
    override val name: String,
    override val parent: FullyQualifiedPathNode<T>?,
    override var content: T? = null,
) : CallablesNode<T>, IsMutable<T> {

    override fun query(path: String): FullyQualifiedPathNode<T>? {
        val part = path.substringBefore(".")
        if (part == name) return this
        return null
    }

    override fun iterator(): Iterator<FullyQualifiedPathNode<T>> = iterator { yield(this@MutableCallablesNode) }
    override fun removeNode(node: FullyQualifiedPathNode<T>) {}

    override fun removeNode(path: String) {
        query(path)?.let { removeNode(it) }
    }

    override val readableString: String
        get() = "CallablesNode(${
            buildList {
                this += "name: $name"
                this += "path: $path"
                if (content != null) {
                    this += "content: $content"
                }
            }.joinToString(", ")
        })"

    override fun toString(): String = readableString
}

interface HasPackageParts<T> {
    val packageParts: Map<String, PackageNode<T>>
}

interface HasClasses<T> {
    val classes: Map<String, ClassesNode<T>>
}

interface HasCallables<T> {
    val callables: Map<String, CallablesNode<T>>
}

interface IsMutable<T> {
    fun removeNode(node: FullyQualifiedPathNode<T>)
    fun removeNode(path: String)
}