package org.hnau.ktcad.gen.solid

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

/** FQN of the class whose sealed variants drive the `Solid` generation. */
const val NODE_NAME = "org.hnau.ktcad.ir.Node"
private const val OPERAND_NAME = "org.hnau.ktcad.ir.Operand"
private const val DOMAIN_PACKAGE = "org.hnau.ktcad"

/**
 * Generates the reference-based `Solid` domain layer from the generated `ir.Node`.
 *
 * The `ir` package is a plain input (found by name, not by a marker annotation), so the
 * schema→Kotlin generator stays generic; all domain knowledge lives here.
 *
 * For each `Node` variant it emits a matching `Solid` variant with `Operand` fields widened to
 * `Solid` (`List<Operand>` → `List<Solid>`), plus:
 * - `Solid.lower(operand: (Solid) -> Operand): Node` — the structural mapper that asks the caller
 *   for each child's index (storage stays outside);
 * - factories (`box`, `fuse`, …).
 *
 * No `Node -> Solid` mapper is generated: the CLI never returns geometry to Kotlin.
 */
class SolidProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()
        generated = true

        val node: KSClassDeclaration? =
            resolver.getClassDeclarationByName(resolver.getKSNameFromString(NODE_NAME))
        if (node == null) {
            logger.warn("SolidProcessor: $NODE_NAME not found; nothing to generate")
            return emptyList()
        }
        val operand: KSClassDeclaration? =
            resolver.getClassDeclarationByName(resolver.getKSNameFromString(OPERAND_NAME))
        if (operand == null) {
            logger.warn("SolidProcessor: $OPERAND_NAME not found; nothing to generate")
            return emptyList()
        }

        val variants: List<KSClassDeclaration> = node.getSealedSubclasses().toList().sortedBy { it.simpleName.asString() }
        if (variants.isEmpty()) {
            logger.warn("SolidProcessor: $NODE_NAME has no sealed subclasses; nothing to generate")
            return emptyList()
        }

        // `Node` lives in the main source set; when the processor also runs for test sources the IR
        // is already compiled, so there is no originating file to attach to (and nothing new to emit).
        val origin = node.containingFile ?: return emptyList()

        val file = generate(node, operand, variants)
        val dependencies = Dependencies(aggregating = false, origin)
        file.writeTo(codeGenerator, dependencies)
        logger.info("SolidProcessor: generated Solid for ${variants.size} variants")
        return emptyList()
    }

    private fun generate(
        node: KSClassDeclaration,
        operand: KSClassDeclaration,
        variants: List<KSClassDeclaration>,
    ): FileSpec {
        val nodeType = node.toClassName()
        val operandType = operand.toClassName()
        val solidName = ClassName(DOMAIN_PACKAGE, "Solid")
        val operandFn = LambdaTypeName.get(
            parameters = listOf(ParameterSpec.unnamed(solidName)),
            returnType = operandType,
        )

        val builder = FileSpec.builder(DOMAIN_PACKAGE, "Solid")
            .addType(solidSealedType(variants, operand, solidName))
            .addFunction(lowerMapper(variants, operand, nodeType, solidName, operandFn))
        variants.forEach { builder.addFunction(factory(it, operand, solidName)) }
        return builder.build()
    }

    // --- Solid sealed hierarchy ----------------------------------------

    private fun solidSealedType(
        variants: List<KSClassDeclaration>,
        operand: KSClassDeclaration,
        solidName: ClassName,
    ): TypeSpec {
        val builder = TypeSpec.interfaceBuilder("Solid").addModifiers(KModifier.SEALED)
        variants.forEach { variant ->
            val name = variant.simpleName.asString()
            if (variant.classKind == ClassKind.OBJECT) {
                builder.addType(
                    TypeSpec.objectBuilder(name).addSuperinterface(solidName).build(),
                )
                return@forEach
            }
            val parameters = variant.primaryConstructor!!.parameters
            val type = TypeSpec.classBuilder(name).addSuperinterface(solidName)
            if (parameters.isEmpty()) {
                builder.addType(type.build())
                return@forEach
            }
            val constructor = FunSpec.constructorBuilder()
            val properties = parameters.map { parameter ->
                val parameterName = parameter.name!!.asString()
                val typeName = solidType(parameter.type.resolve(), operand, solidName)
                constructor.addParameter(parameterName, typeName)
                PropertySpec.builder(parameterName, typeName).initializer(parameterName).build()
            }
            type.addModifiers(KModifier.DATA)
                .primaryConstructor(constructor.build())
                .apply { properties.forEach(::addProperty) }
            builder.addType(type.build())
        }
        return builder.build()
    }

    // --- Solid -> Node -------------------------------------------------

    private fun lowerMapper(
        variants: List<KSClassDeclaration>,
        operand: KSClassDeclaration,
        nodeType: ClassName,
        solidName: ClassName,
        operandFn: LambdaTypeName,
    ): FunSpec {
        val body = CodeBlock.builder().add("return when (this) {\n").indent()
        variants.forEach { variant ->
            val name = variant.simpleName.asString()
            body.add("is %T.%L -> ", solidName, name)
            if (variant.classKind == ClassKind.OBJECT) {
                body.add("%T.%L\n", nodeType, name)
            } else {
                body.add("%T.%L(", nodeType, name)
                body.add(argumentList(variant) { parameter ->
                    when (operandKind(parameter.type.resolve(), operand)) {
                        OperandKind.SINGLE -> CodeBlock.of("operand(%N)", parameter.name!!.asString())
                        OperandKind.LIST -> CodeBlock.of("%N.map(operand)", parameter.name!!.asString())
                        OperandKind.NONE -> CodeBlock.of("%N", parameter.name!!.asString())
                    }
                })
                body.add(")\n")
            }
        }
        body.unindent().add("}\n")
        return FunSpec.builder("lower")
            .receiver(solidName)
            .addModifiers(KModifier.INTERNAL)
            .addParameter("operand", operandFn)
            .returns(nodeType)
            .addCode(body.build())
            .build()
    }

    // --- factories -----------------------------------------------------

    private fun factory(
        variant: KSClassDeclaration,
        operand: KSClassDeclaration,
        solidName: ClassName,
    ): FunSpec {
        val name = variant.simpleName.asString().replaceFirstChar(Char::lowercaseChar)
        val builder = FunSpec.builder(name).returns(solidName)
        if (variant.classKind == ClassKind.OBJECT) {
            return builder
                .addCode("return %T.%L", solidName, variant.simpleName.asString())
                .build()
        }
        val constructor = variant.primaryConstructor!!
        val arguments = constructor.parameters.map { parameter ->
            val parameterName = parameter.name!!.asString()
            val type = solidType(parameter.type.resolve(), operand, solidName)
            builder.addParameter(
                ParameterSpec.builder(parameterName, type)
                    .apply {
                        if (parameter.hasDefault) {
                            defaultValue("%L", defaultSource(parameter.type.resolve()))
                        }
                    }
                    .build(),
            )
            CodeBlock.of("%N", parameterName)
        }
        builder.addCode(
            "return %T.%L(%L)",
            solidName,
            variant.simpleName.asString(),
            CodeBlock.of(arguments.joinToString(", ") { "%L" }, *arguments.toTypedArray()),
        )
        return builder.build()
    }

    // --- helpers -------------------------------------------------------

    /** `Solid` field type: `Operand` → `Solid`, `List<Operand>` → `List<Solid>`, others unchanged. */
    private fun solidType(type: KSType, operand: KSClassDeclaration, solidName: ClassName): TypeName {
        // `Operand` itself widens to `Solid`.
        if (isOperand(type, operand)) {
            return if (type.isMarkedNullable) solidName.copy(nullable = true) else solidName
        }
        // A parameterized `ir` type (e.g. `List<Operand>`): widen its arguments.
        if (type.arguments.isNotEmpty()) {
            val base = (type.declaration as KSClassDeclaration).toClassName()
            val widened = type.arguments.map { argument ->
                solidType(argument.type!!.resolve(), operand, solidName)
            }
            val parameterized = base.parameterizedBy(widened)
            return if (type.isMarkedNullable) parameterized.copy(nullable = true) else parameterized
        }
        return type.toTypeName()
    }

    private fun isOperand(type: KSType, operand: KSClassDeclaration): Boolean =
        type.declaration.qualifiedName?.asString() == operand.qualifiedName?.asString()

    /** Is a field an operand, a list of operands, or neither? */
    private enum class OperandKind { SINGLE, LIST, NONE }

    private fun operandKind(type: KSType, operand: KSClassDeclaration): OperandKind {
        if (isOperand(type, operand)) return OperandKind.SINGLE
        val argument = type.arguments.singleOrNull()?.type?.resolve() ?: return OperandKind.NONE
        return if (isOperand(argument, operand)) OperandKind.LIST else OperandKind.NONE
    }

    private fun argumentList(
        variant: KSClassDeclaration,
        render: (KSValueParameter) -> CodeBlock,
    ): CodeBlock {
        val parameters = variant.primaryConstructor!!.parameters
        if (parameters.isEmpty()) return CodeBlock.of("")
        val format = parameters.joinToString(", ") { "${it.name!!.asString()} = %L" }
        return CodeBlock.of(format, *parameters.map(render).toTypedArray())
    }

    private fun defaultSource(type: KSType): String =
        if (type.isMarkedNullable) "null" else "false"

}
