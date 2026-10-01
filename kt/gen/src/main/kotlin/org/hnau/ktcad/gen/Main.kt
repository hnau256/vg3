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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

private const val SCHEMA_PACKAGE = "org.hnau.ktcad.ir"
private const val ROOT_NAME = "Model"
private const val REF_PREFIX = "#/\$defs/"
private const val DISCRIMINATOR = "type"

private val SERIALIZABLE = ClassName("kotlinx.serialization", "Serializable")
private val SERIAL_NAME = ClassName("kotlinx.serialization", "SerialName")
private val JSON_CLASS_DISCRIMINATOR = ClassName("kotlinx.serialization.json", "JsonClassDiscriminator")
private val LIST = ClassName("kotlin.collections", "List")

/**
 * Generates `kotlinx.serialization` classes (package [SCHEMA_PACKAGE]) from the vg3 IR JSON Schema.
 *
 * Usage: `<schema.json> <output-dir>`.
 *
 * Mapping (schema -> Kotlin):
 * - `{"type":"object","properties":…}`      -> `@Serializable data class`
 * - `{"oneOf":[… "const":tag …]}`           -> `@Serializable sealed interface` with nested variants
 *                                              (`@SerialNumber(tag)`, discriminator = "type")
 * - `{"type":"string","enum":[…]}`          -> `@Serializable enum class` (`@SerialName` per entry)
 * - `{"$ref":"#/$defs/X"}`                  -> `X`
 * - `{"anyOf":[X,{"type":"null"}]}`         -> `X?`
 * - non-required properties become optional (schema `default`, or `= null` so the engine applies its own default)
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: <schema.json> <output-dir>" }
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
        else -> error("vg3 codegen: unsupported definition '$name': $definition")
    }
    return FileSpec.builder(SCHEMA_PACKAGE, name).addType(type).build()
}

private fun sealedType(name: String, definition: JsonObject): TypeSpec {
    val parent = ClassName(SCHEMA_PACKAGE, name)
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
        .apply { if (default != null) defaultValue("%L", default) }
        .build()
    val property = PropertySpec.builder(name, type)
        .initializer("%N", name)
        .build()
    return Field(parameter, property)
}

private fun resolveType(schema: JsonObject): TypeName {
    schema["\$ref"]?.let { reference ->
        return ClassName(SCHEMA_PACKAGE, reference.jsonPrimitive.content.removePrefix(REF_PREFIX))
    }
    schema["anyOf"]?.let { anyOf ->
        val concrete = anyOf.jsonArray.first {
            it.jsonObject["type"]?.jsonPrimitive?.contentOrNull != "null"
        }
        return resolveType(concrete.jsonObject).copy(nullable = true)
    }
    return when (schema["type"]?.jsonPrimitive?.contentOrNull) {
        "number" -> DOUBLE
        "integer" -> INT
        "string" -> STRING
        "boolean" -> BOOLEAN
        "array" -> LIST.parameterizedBy(resolveType(schema.getValue("items").jsonObject))
        else -> error("vg3 codegen: unsupported schema: $schema")
    }
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
