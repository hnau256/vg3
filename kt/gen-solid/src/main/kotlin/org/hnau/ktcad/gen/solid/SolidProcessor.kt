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

private const val DOMAIN_PACKAGE = "org.hnau.ktcad"
private const val IR_PACKAGE = "$DOMAIN_PACKAGE.ir"

private val FOLD = ClassName(
    "org.hnau.commons.gen.fold.annotations",
    "Fold",
)

/**
 * Generates the reference-based domain layers from the generated `ir` sealed nodes.
 *
 * The `ir` package is a plain input (found by name, not by a marker annotation), so the
 * schema→Kotlin generator stays generic; all domain knowledge lives here.
 *
 * Each domain mirrors an IR sealed type, widening its arena references to domain types:
 * - `Body<BodyIndex>` → `Solid` (operands are `Solid`; the `SketchIndex` profile widens to `Region`);
 * - `Sketch<SketchIndex>` → `Region` (operands are `Region`).
 *
 * For each domain it emits a matching sealed hierarchy plus:
 * - `<Domain>.lower(operand: (Domain) -> SelfIndex, <ref>: (RefDomain) -> RefIndex)` — the
 *   structural mapper that asks the caller for each child's index (storage stays outside);
 * - factories (`box`, `fuse`, … for `Solid`; `circle`, `polygon`, … for `Region`) for every domain,
 *   each returning its concrete variant subtype.
 *
 * No domain → IR mapper is generated: the CLI never returns geometry to Kotlin.
 */

/** An external arena index (in the IR) that widens to a domain type in a generated domain. */
private data class Reference(
    val indexFqn: String,
    val domainName: String,
    val lambdaName: String,
)

/** One generated domain: `nodeFqn` (sealed IR) → `domainName` (sealed domain). */
private data class Domain(
    val nodeFqn: String,
    val domainName: String,
    val selfIndexFqn: String,
    val references: List<Reference>,
    val factories: Boolean,
)

private val DOMAINS = listOf(
    Domain(
        nodeFqn = "$IR_PACKAGE.Body",
        domainName = "Solid",
        selfIndexFqn = "$IR_PACKAGE.BodyIndex",
        references = listOf(
            Reference(
                indexFqn = "$IR_PACKAGE.SketchIndex",
                domainName = "Region",
                lambdaName = "sketch",
            ),
        ),
        factories = true,
    ),
    Domain(
        nodeFqn = "$IR_PACKAGE.Sketch",
        domainName = "Region",
        selfIndexFqn = "$IR_PACKAGE.SketchIndex",
        references = emptyList(),
        factories = true,
    ),
)

class SolidProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private var generated = false

    override fun process(
        resolver: Resolver,
    ): List<KSAnnotated> {
        if (generated) return emptyList()
        generated = true

        DOMAINS.forEach { domain ->
            val node: KSClassDeclaration? =
                resolver.getClassDeclarationByName(resolver.getKSNameFromString(domain.nodeFqn))
            if (node == null) {
                logger.warn("SolidProcessor: ${domain.nodeFqn} not found; nothing to generate")
                return@forEach
            }
            // The `ir` lives in the main source set; when the processor also runs for test sources
            // the IR is already compiled, so there is no originating file (and nothing new to emit).
            val origin = node.containingFile ?: return@forEach
            val file = generate(
                domain = domain,
                node = node,
            )
            file.writeTo(
                codeGenerator = codeGenerator,
                dependencies = Dependencies(
                    false,
                    origin,
                ),
            )
            logger.info("SolidProcessor: generated ${domain.domainName}")
        }
        return emptyList()
    }

    private fun generate(
        domain: Domain,
        node: KSClassDeclaration,
    ): FileSpec {
        val nodeType = node.toClassName()
        val variants: List<KSClassDeclaration> =
            node.getSealedSubclasses().toList().sortedBy { it.simpleName.asString() }
        if (variants.isEmpty()) {
            logger.warn("SolidProcessor: ${domain.nodeFqn} has no sealed subclasses")
        }
        val domainType = ClassName(
            DOMAIN_PACKAGE,
            domain.domainName,
        )

        val builder = FileSpec.builder(
            packageName = DOMAIN_PACKAGE,
            fileName = domain.domainName,
        )
            .addType(
                sealedType(
                    domain = domain,
                    variants = variants,
                    domainType = domainType,
                ),
            )
            .addFunction(
                lowerMapper(
                    domain = domain,
                    variants = variants,
                    nodeType = nodeType,
                    domainType = domainType,
                ),
            )
        if (domain.factories) {
            variants.forEach {
                builder.addFunction(
                    factory(
                        domain = domain,
                        variant = it,
                        domainType = domainType,
                    ),
                )
            }
        }
        return builder.build()
    }

    // --- sealed hierarchy ----------------------------------------------

    private fun sealedType(
        domain: Domain,
        variants: List<KSClassDeclaration>,
        domainType: ClassName,
    ): TypeSpec {
        val builder = TypeSpec.interfaceBuilder(domain.domainName)
            .addModifiers(KModifier.SEALED)
            .addAnnotation(FOLD)
        variants.forEach { variant ->
            val name = variant.simpleName.asString()
            if (variant.classKind == ClassKind.OBJECT) {
                builder.addType(TypeSpec.objectBuilder(name).addSuperinterface(domainType).build())
                return@forEach
            }
            val parameters = orderedParameters(variant)
            val type = TypeSpec.classBuilder(name).addSuperinterface(domainType)
            if (parameters.isEmpty()) {
                builder.addType(type.build())
                return@forEach
            }
            val constructor = FunSpec.constructorBuilder()
            val properties = parameters.map { parameter ->
                val parameterName = parameter.name!!.asString()
                val typeName = domainType(
                    domain = domain,
                    type = parameter.type.resolve(),
                    domainType = domainType,
                )
                constructor.addParameter(
                    ParameterSpec.builder(
                        parameterName,
                        typeName,
                    )
                        .apply {
                            if (parameter.hasDefault) {
                                defaultValue(
                                    "%L",
                                    defaultSource(parameter.type.resolve()),
                                )
                            }
                        }
                        .build(),
                )
                PropertySpec.builder(
                    parameterName,
                    typeName,
                ).initializer(parameterName).build()
            }
            type.addModifiers(KModifier.DATA)
                .primaryConstructor(constructor.build())
                .apply { properties.forEach(::addProperty) }
            builder.addType(type.build())
        }
        return builder.build()
    }

    // --- Domain -> IR --------------------------------------------------

    private fun lowerMapper(
        domain: Domain,
        variants: List<KSClassDeclaration>,
        nodeType: ClassName,
        domainType: ClassName,
    ): FunSpec {
        val builder = FunSpec.builder("lower")
            .receiver(domainType)
            .addModifiers(KModifier.INTERNAL)
            .addParameter(
                "operand",
                LambdaTypeName.get(
                    parameters = listOf(ParameterSpec.unnamed(domainType)),
                    returnType = ClassName.bestGuess(domain.selfIndexFqn),
                ),
            )
        domain.references.forEach { reference ->
            builder.addParameter(
                reference.lambdaName,
                LambdaTypeName.get(
                    parameters = listOf(
                        ParameterSpec.unnamed(
                            ClassName(
                                DOMAIN_PACKAGE,
                                reference.domainName,
                            ),
                        ),
                    ),
                    returnType = ClassName.bestGuess(reference.indexFqn),
                ),
            )
        }

        val body = CodeBlock.builder().add("return when (this) {\n").indent()
        variants.forEach { variant ->
            val name = variant.simpleName.asString()
            body.add(
                "is %T.%L -> ",
                domainType,
                name,
            )
            if (variant.classKind == ClassKind.OBJECT) {
                body.add(
                    "%T.%L\n",
                    nodeType,
                    name,
                )
            } else {
                body.add(
                    "%T.%L(",
                    nodeType,
                    name,
                )
                body.add(
                    argumentList(variant = variant) { parameter ->
                        renderField(
                            domain = domain,
                            parameter = parameter,
                        )
                    },
                )
                body.add(")\n")
            }
        }
        body.unindent().add("}\n")
        return builder.returns(nodeType).addCode(body.build()).build()
    }

    private fun renderField(
        domain: Domain,
        parameter: KSValueParameter,
    ): CodeBlock {
        val name = parameter.name!!.asString()
        return when (val kind = fieldKind(
            domain = domain,
            type = parameter.type.resolve(),
        )) {
            FieldKind.NONE -> CodeBlock.of(
                "%N",
                name,
            )

            FieldKind.SELF_SINGLE -> CodeBlock.of(
                "operand(%N)",
                name,
            )

            FieldKind.SELF_LIST -> CodeBlock.of(
                "%N.map(operand)",
                name,
            )

            is FieldKind.REF_SINGLE -> CodeBlock.of(
                "%N(%N)",
                kind.reference.lambdaName,
                name,
            )

            is FieldKind.REF_LIST -> CodeBlock.of(
                "%N.map(%N)",
                name,
                kind.reference.lambdaName,
            )
        }
    }

    // --- factories -----------------------------------------------------

    private fun factory(
        domain: Domain,
        variant: KSClassDeclaration,
        domainType: ClassName,
    ): FunSpec {
        val name = variant.simpleName.asString().replaceFirstChar(Char::lowercaseChar)
        val variantName = variant.simpleName.asString()
        val builder = FunSpec.builder(name).returns(domainType.nestedClass(variantName))
        if (variant.classKind == ClassKind.OBJECT) {
            return builder
                .addCode(
                    "return %T.%L",
                    domainType,
                    variantName,
                )
                .build()
        }
        val arguments = orderedParameters(variant).map { parameter ->
            val parameterName = parameter.name!!.asString()
            val type = domainType(
                domain = domain,
                type = parameter.type.resolve(),
                domainType = domainType,
            )
            builder.addParameter(
                ParameterSpec.builder(
                    parameterName,
                    type,
                )
                    .apply {
                        if (parameter.hasDefault) {
                            defaultValue(
                                "%L",
                                defaultSource(parameter.type.resolve()),
                            )
                        }
                    }
                    .build(),
            )
            CodeBlock.of(
                "%N",
                parameterName,
            )
        }
        builder.addCode(
            "return %T.%L(%L)",
            domainType,
            variantName,
            CodeBlock.of(
                arguments.joinToString(separator = ", ") { "%L" },
                *arguments.toTypedArray(),
            ),
        )
        return builder.build()
    }

    // --- helpers -------------------------------------------------------

    /** Domain field type: self/ref indices widen, parameterized types widen their arguments. */
    private fun domainType(
        domain: Domain,
        type: KSType,
        domainType: ClassName,
    ): TypeName {
        if (isType(
                type = type,
                fqn = domain.selfIndexFqn,
            )
        ) {
            return domainType.nullableIf(type)
        }
        domain.references.forEach { reference ->
            if (isType(
                    type = type,
                    fqn = reference.indexFqn,
                )
            ) {
                return ClassName(
                    DOMAIN_PACKAGE,
                    reference.domainName,
                ).nullableIf(type)
            }
        }
        if (type.arguments.isNotEmpty()) {
            val base = (type.declaration as KSClassDeclaration).toClassName()
            val widened = type.arguments.map { argument ->
                domainType(
                    domain = domain,
                    type = argument.type!!.resolve(),
                    domainType = domainType,
                )
            }
            return base.parameterizedBy(widened).nullableIf(type)
        }
        return type.toTypeName()
    }

    private fun TypeName.nullableIf(
        type: KSType,
    ): TypeName =
        if (type.isMarkedNullable) copy(nullable = true) else this

    private fun isType(
        type: KSType,
        fqn: String,
    ): Boolean =
        type.declaration.qualifiedName?.asString() == fqn

    /** What role a field plays: self operand, external reference, or neither. */
    private sealed interface FieldKind {
        object NONE : FieldKind
        object SELF_SINGLE : FieldKind
        object SELF_LIST : FieldKind
        data class REF_SINGLE(
            val reference: Reference,
        ) : FieldKind

        data class REF_LIST(
            val reference: Reference,
        ) : FieldKind
    }

    private fun fieldKind(
        domain: Domain,
        type: KSType,
    ): FieldKind {
        if (isType(
                type = type,
                fqn = domain.selfIndexFqn,
            )
        ) return FieldKind.SELF_SINGLE
        val argument = type.arguments.singleOrNull()?.type?.resolve()
        if (argument != null && isType(
                type = argument,
                fqn = domain.selfIndexFqn,
            )
        ) return FieldKind.SELF_LIST
        domain.references.forEach { reference ->
            if (isType(
                    type = type,
                    fqn = reference.indexFqn,
                )
            ) return FieldKind.REF_SINGLE(reference)
            if (argument != null && isType(
                    type = argument,
                    fqn = reference.indexFqn,
                )
            ) {
                return FieldKind.REF_LIST(reference)
            }
        }
        return FieldKind.NONE
    }

    /** The variant's constructor parameters, required ones first (defaulted ones last). */
    private fun orderedParameters(
        variant: KSClassDeclaration,
    ): List<KSValueParameter> =
        variant.primaryConstructor!!.parameters.sortedBy { it.hasDefault }

    private fun argumentList(
        variant: KSClassDeclaration,
        render: (KSValueParameter) -> CodeBlock,
    ): CodeBlock {
        val parameters = variant.primaryConstructor!!.parameters
        if (parameters.isEmpty()) return CodeBlock.of("")
        val format = parameters.joinToString(separator = ", ") { "${it.name!!.asString()} = %L" }
        return CodeBlock.of(
            format,
            *parameters.map(render).toTypedArray(),
        )
    }

    /**
     * The Kotlin default for a parameter that has one in the IR. KSP does not expose the actual
     * value, so only the cases the generator can reproduce are supported; anything else fails the
     * build loudly instead of silently emitting a wrong default.
     *
     * An enum's default is taken to be its first constant: the IR marks the default variant with
     * `#[default]`, and enum constants are emitted in declaration order.
     */
    private fun defaultSource(
        type: KSType,
    ): String {
        if (type.isMarkedNullable) {
            return "null"
        }
        val declaration = type.declaration
        val name = declaration.qualifiedName?.asString()
        if (name == "kotlin.Boolean") {
            return "false"
        }
        if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.ENUM_CLASS) {
            val first = declaration.declarations
                .filterIsInstance<KSClassDeclaration>()
                .firstOrNull()
                ?.simpleName
                ?.asString()
            check(value = first != null) { "SolidProcessor: enum $name has no constants" }
            return "$name.$first"
        }
        error("SolidProcessor: unsupported non-null default for $name")
    }
}
