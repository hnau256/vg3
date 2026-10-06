package org.hnau.ktcad.gen

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

private const val DEFAULT_SCHEMA_PACKAGE = "org.hnau.ktcad.ir"
private const val ROOT_NAME = "Model"
private const val REF_PREFIX = "#/\$defs/"
private const val DISCRIMINATOR = "type"

/** Target package for the generated classes; set from the command line (see [main]). */
private var schemaPackage = DEFAULT_SCHEMA_PACKAGE

/** Kotlin type used for non-empty arrays (`minItems >= 1`); set from the command line. */
private var nonEmptyList = ClassName("arrow.core", "NonEmptyList")

/** Serializer the generated non-empty properties are annotated with; set from the command line. */
private var nonEmptyListSerializer = ClassName("arrow.core.serialization", "NonEmptyListSerializer")

private val SERIALIZABLE = ClassName("kotlinx.serialization", "Serializable")
private val SERIAL_NAME = ClassName("kotlinx.serialization", "SerialName")
private val JVM_INLINE = ClassName("kotlin.jvm", "JvmInline")
private val JSON_CLASS_DISCRIMINATOR = ClassName("kotlinx.serialization.json", "JsonClassDiscriminator")
private val LIST = ClassName("kotlin.collections", "List")

/**
 * Generates `kotlinx.serialization` classes from the vg3 IR JSON Schema.
 *
 * Usage: `<schema.json> <output-dir> [package] [non-empty-list] [non-empty-serializer]`.
 *
 * Mapping (schema -> Kotlin):
 * - `{"type":"object","properties":…}`      -> `@Serializable data class`
 * - `{"oneOf":[… "const":tag …]}`           -> `@Serializable sealed interface` with nested variants
 *                                              (`@SerialNumber(tag)`, discriminator = "type")
 * - `{"type":"string","enum":[…]}`          -> `@Serializable enum class` (`@SerialName` per entry)
 * - `{"$ref":"#/$defs/X"}`                  -> `X`
 * - `{"anyOf":[X,{"type":"null"}]}`         -> `X?`
 * - array with `minItems >= 1` (no `maxItems`) -> `NonEmptyList<T>` with `@Serializable(with = …)`
 * - non-required properties become optional (schema `default`, or `= null` so the engine applies its own default)
 */
fun main(args: Array<String>) {
    require(args.size in 2..5) { "usage: <schema.json> <output-dir> [package] [non-empty-list] [non-empty-serializer]" }
    schemaPackage = args.getOrNull(2) ?: DEFAULT_SCHEMA_PACKAGE
    args.getOrNull(3)?.let { nonEmptyList = ClassName.bestGuess(it) }
    args.getOrNull(4)?.let { nonEmptyListSerializer = ClassName.bestGuess(it) }
    val schema = Json.parseToJsonElement(File(args[0]).readText()).jsonObject
    val definitions = schema.getValue("\$defs").jsonObject
    val outputDir = File(args[1]).apply { mkdirs() }

    definitions.forEach { (name, definition) ->
        buildFile(name, definition.jsonObject).writeTo(outputDir)
    }

    val rootDefinition = schema.filterKeys { it != "\$defs" }
    buildFile(ROOT_NAME, JsonObject(rootDefinition)).writeTo(outputDir)

    println("vg3 codegen: wrote ${definitions.size + 1} file(s) to ${outputDir.absolutePath}")
}

private fun buildFile(name: String, definition: JsonObject): FileSpec {
    val type = when {
        definition.containsKey("oneOf") -> sealedType(name, definition)
        definition["enum"] != null -> enumType(name, definition)
        definition["type"]?.jsonPrimitive?.contentOrNull == "object" -> objectType(name, definition)
        isScalar(definition) -> valueClass(name, definition)
        else -> error("vg3 codegen: unsupported definition '$name': $definition")
    }
    return FileSpec.builder(schemaPackage, name).addType(type).build()
}

/** A named scalar (e.g. `BodyIndex`): a `@JvmInline value class` over its primitive. */
private fun valueClass(name: String, definition: JsonObject): TypeSpec {
    val type = primitiveType(definition)
    val property = PropertySpec.builder("value", type).initializer("value").build()
    return TypeSpec.classBuilder(name)
        .addAnnotation(JVM_INLINE)
        .addAnnotation(SERIALIZABLE)
        .addModifiers(KModifier.VALUE)
        .primaryConstructor(
            FunSpec.constructorBuilder().addParameter("value", type).build(),
        )
        .addProperty(property)
        .build()
}

private fun sealedType(name: String, definition: JsonObject): TypeSpec {
    val parent = ClassName(schemaPackage, name)
    val builder = TypeSpec.interfaceBuilder(name)
        .addModifiers(KModifier.SEALED)
        .addAnnotation(SERIALIZABLE)
        .addAnnotation(
            AnnotationSpec.builder(JSON_CLASS_DISCRIMINATOR)
                .addMember("%S", DISCRIMINATOR)
                .build(),
        )

    definition.getValue("oneOf").jsonArray.forEach { branch ->
        val branchObject = branch.jsonObject
        val tag = branchObject
            .getValue("properties").jsonObject
            .getValue(DISCRIMINATOR).jsonObject
            .getValue("const").jsonPrimitive.content

        builder.addType(
            objectType(
                name = pascalCase(tag),
                definition = branchObject,
                superinterface = parent,
                serialName = tag,
            ),
        )
    }
    return builder.build()
}

private fun objectType(
    name: String,
    definition: JsonObject,
    superinterface: ClassName? = null,
    serialName: String? = null,
): TypeSpec {
    val properties = definition["properties"]?.jsonObject.orEmpty()
    val required = definition["required"]
        ?.jsonArray?.map { it.jsonPrimitive.content }?.toSet()
        .orEmpty()
    val fields = properties
        .filterKeys { it != DISCRIMINATOR }
        .map { (propertyName, propertySchema) ->
            fieldOf(
                name = propertyName,
                schema = propertySchema.jsonObject,
                required = propertyName in required,
            )
        }

    val builder = if (fields.isEmpty()) {
        TypeSpec.objectBuilder(name)
    } else {
        val constructor = FunSpec.constructorBuilder()
        fields.forEach { constructor.addParameter(it.parameter) }
        TypeSpec.classBuilder(name)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(constructor.build())
            .apply { fields.forEach { addProperty(it.property) } }
    }

    builder.addAnnotation(SERIALIZABLE)
    if (serialName != null) {
        builder.addAnnotation(
            AnnotationSpec.builder(SERIAL_NAME).addMember("%S", serialName).build(),
        )
    }
    if (superinterface != null) {
        builder.addSuperinterface(superinterface)
    }
    return builder.build()
}

private fun enumType(name: String, definition: JsonObject): TypeSpec {
    val builder = TypeSpec.enumBuilder(name).addAnnotation(SERIALIZABLE)
    definition.getValue("enum").jsonArray.forEach { value ->
        val wire = value.jsonPrimitive.content
        builder.addEnumConstant(
            enumConstantName(wire),
            TypeSpec.anonymousClassBuilder()
                .addAnnotation(AnnotationSpec.builder(SERIAL_NAME).addMember("%S", wire).build())
                .build(),
        )
    }
    return builder.build()
}

private data class Field(
    val parameter: ParameterSpec,
    val property: PropertySpec,
)

private fun fieldOf(name: String, schema: JsonObject, required: Boolean): Field {
    val hasDefault = schema.containsKey("default")
    val baseType = resolveType(schema)
    val nullable = !required && !hasDefault
    val type = if (nullable) baseType.copy(nullable = true) else baseType
    val default = when {
        hasDefault -> literal(schema.getValue("default").jsonPrimitive)
        !required -> "null"
        else -> null
    }

    val parameter = ParameterSpec.builder(name, type)
        .apply {
            if (default != null) defaultValue("%L", default)
            if (isNonEmptyList(schema)) {
                addAnnotation(
                    AnnotationSpec.builder(SERIALIZABLE)
                        .addMember("with = %T::class", nonEmptyListSerializer)
                        .build(),
                )
            }
        }
        .build()
    val property = PropertySpec.builder(name, type)
        .initializer("%N", name)
        .build()
    return Field(parameter, property)
}

/**
 * A non-empty list: an array with `minItems >= 1` and no `maxItems` (fixed-size arrays like the
 * `matrix` stay plain `List`). Maps to the Kotlin `NonEmptyList` with its Arrow serializer.
 */
private fun isNonEmptyList(schema: JsonObject): Boolean {
    val type = schema["type"]
    if (type is JsonArray) return false
    return type?.jsonPrimitive?.contentOrNull == "array" &&
        (schema["minItems"]?.jsonPrimitive?.intOrNull ?: 0) >= 1 &&
        !schema.containsKey("maxItems")
}

private fun resolveType(schema: JsonObject): TypeName {
    schema["\$ref"]?.let { reference ->
        return ClassName(schemaPackage, reference.jsonPrimitive.content.removePrefix(REF_PREFIX))
    }
    schema["anyOf"]?.let { anyOf ->
        val concrete = anyOf.jsonArray.first {
            it.jsonObject["type"]?.jsonPrimitive?.contentOrNull != "null"
        }
        return resolveType(concrete.jsonObject).copy(nullable = true)
    }
    val type = schema["type"]
    // A nullable primitive: `"type": ["number", "null"]`.
    if (type is JsonArray) {
        val names = type.map { it.jsonPrimitive.content }
        val concrete = names.first { it != "null" }
        return primitiveTypeName(concrete).copy(nullable = "null" in names)
    }
    return when (type?.jsonPrimitive?.contentOrNull) {
        "array" -> {
            val element = resolveType(schema.getValue("items").jsonObject)
            if (isNonEmptyList(schema)) nonEmptyList.parameterizedBy(element) else LIST.parameterizedBy(element)
        }
        else -> primitiveType(schema)
    }
}

private fun isScalar(schema: JsonObject): Boolean {
    val type = schema["type"] ?: return false
    val names = if (type is JsonArray) {
        type.map { it.jsonPrimitive.content }
    } else {
        listOf(type.jsonPrimitive.content)
    }
    return names.any { it in setOf("number", "integer", "string", "boolean") }
}

/** The Kotlin primitive backing a scalar schema type. */
private fun primitiveType(schema: JsonObject): TypeName = primitiveTypeName(
    schema["type"]?.jsonPrimitive?.contentOrNull ?: error("vg3 codegen: not a scalar: $schema"),
)

private fun primitiveTypeName(name: String): TypeName = when (name) {
    "number" -> DOUBLE
    "integer" -> INT
    "string" -> STRING
    "boolean" -> BOOLEAN
    else -> error("vg3 codegen: not a scalar type: $name")
}

private fun literal(value: JsonPrimitive): String = when {
    value.booleanOrNull != null -> value.booleanOrNull.toString()
    value.isString -> "\"${value.content.replace("\"", "\\\"")}\""
    else -> value.content
}

private fun pascalCase(value: String): String =
    value.split('_', '-').joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }

private fun enumConstantName(value: String): String =
    value.uppercase().replace('-', '_')
