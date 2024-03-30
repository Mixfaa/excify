package com.mixfa.excify

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import java.util.*
import kotlin.reflect.KClass


private data class ProcessingTools(
    val resolver: Resolver,
    val filesList: MutableList<FileSpec.Builder>,
) {
    fun getFileSpecBuilderFor(prop: KSPropertyDeclaration): FileSpec.Builder {
        val klass = resolver.getClassDeclarationByName(prop.type.resolve().toClassName().canonicalName)
            ?: throw Exception("Can`t find class by name ${prop.qualifiedName}")

        return getFileSpecBuilderFor(klass)
    }

    fun getFileSpecBuilderFor(klass: KSClassDeclaration): FileSpec.Builder {
        val packageName = klass.packageName.asString()
        val filename = klass.excifyFilename()

        return filesList.find { it.packageName == packageName && it.name == filename } ?: run {
            val builder = FileSpec.builder(packageName, filename)
            filesList.add(builder)

            builder
        }
    }
}

class ExcifyProcessor(
    private val codeGenerator: CodeGenerator, private val logger: KSPLogger
) : SymbolProcessor {

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotatedSymbols = resolver.getSymbolsWithAnnotation(ExcifyCachedException::class.qualifiedName!!, true)

        val tools = ProcessingTools(resolver, mutableListOf())

        for (symbol in annotatedSymbols) {
            val annotation = symbol.getAnnotationsByType(ExcifyCachedException::class).first()
            when (symbol) {
                is KSClassDeclaration -> handleAnnotatedClass(symbol, annotation, tools)
                is KSPropertyDeclaration -> handleAnnotatedProperty(symbol, annotation, tools)
            }
        }

        val orThrowProps = resolver.getSymbolsWithAnnotation(ExcifyOptionalOrThrow::class.qualifiedName!!, true)
            .filterIsInstance<KSPropertyDeclaration>()

        for (prop in orThrowProps) {
            val annotation = prop.getAnnotationsByType(ExcifyOptionalOrThrow::class).first()
            handleAnnotatedOrThrow(prop, annotation, tools)
        }

        tools.filesList.forEach {
            it.build().writeTo(
                codeGenerator, Dependencies.ALL_FILES
            )
        }

        return emptyList()
    }


    private fun handleAnnotatedOrThrow(
        prop: KSPropertyDeclaration,
        annotation: ExcifyOptionalOrThrow,
        tools: ProcessingTools
    ) {

        val fileBuilder = tools.getFileSpecBuilderFor(prop)
        fileBuilder.addImport(
            prop.packageName.asString(),
            prop.simpleName.asString()
        )

        val typeClassName = when (val type = annotation.findType()) {
            is KSType -> type.toClassName()
            is KClass<*> -> type.asClassName()
            else -> throw Exception("Can`t resolve type")
        }

        fileBuilder
            .addFunction(
                FunSpec.builder(resolveOrThrowMethodName(prop, annotation))
                    .receiver(
                        Optional::class.asClassName().parameterizedBy(
                            typeClassName
                        )
                    )
                    .returns(typeClassName)
                    .addCode(
                        CodeBlock
                            .builder()
                            .beginControlFlow("return run") // because of bad kotlinpoet wired formatting
                            .beginControlFlow("orElseThrow")
                            .addStatement("%L", prop)
                            .endControlFlow()
                            .endControlFlow()
                            .build()

                    )
                    .build()
            )
    }

    /**
     * Generate .userNotFound like methods
     */
    private fun handleAnnotatedProperty(
        prop: KSPropertyDeclaration,
        annotation: ExcifyCachedException,
        tools: ProcessingTools
    ) {
        val methodName = resolveGetMethodName(prop, annotation)

        val classDeclaration = run {
            val propTypeName = prop.type.resolve().toClassName().canonicalName
            tools.resolver.getClassDeclarationByName(propTypeName) ?: throw AutoLoggingException(
                "Can`t find class by name $propTypeName",
                logger
            )
        }

        val companionObject = classDeclaration.findCompanionObjectOrThrow(logger).toClassName()
        val className = classDeclaration.toClassName()

        val fileBuilder = tools.getFileSpecBuilderFor(prop)
        fileBuilder.addImport(
            prop.packageName.asString(),
            prop.simpleName.asString()
        )

        // making extension method
        fileBuilder
            .addFunction(
                FunSpec.builder(
                    methodName
                )
                    .receiver(companionObject)
                    .returns(className)
                    .addStatement("return %L", prop)
                    .build()
            )

    }

    /**
     * Generate cached .get method for no args constructor
     */
    private fun handleAnnotatedClass(
        klass: KSClassDeclaration, annotation: ExcifyCachedException, tools: ProcessingTools
    ) {

        val companionObject = klass.findCompanionObjectOrThrow(logger).toClassName()

        val className = klass.toClassName()
        val noArgsConstructor = klass.getConstructors().firstOrNull { it.parameters.isEmpty() }

        if (noArgsConstructor == null) {
            logger.info("No args constructor not found for $klass but caching requested")
            throw Exception("$klass don`t no args constructor")
        }

        tools.getFileSpecBuilderFor(klass)
            .addProperty(
                PropertySpec.builder("cachedException", className, listOf(KModifier.PRIVATE)).mutable(false)
                    .initializer("%T()", className).build()
            ).addFunction(FunSpec.builder(annotation.methodName.ifBlank { "get" }).receiver(companionObject)
                .also { funcBuilder ->
                    noArgsConstructor.parameters.forEach { param ->
                        funcBuilder.addParameter(
                            ParameterSpec.builder(
                                name = param.name!!.getShortName(),
                                type = param.type.toTypeName(),
                                modifiers = param.asModifiers()
                            ).build()
                        )
                    }
                }.returns(className).let { builder ->
                    val returnStatement = "return cachedException"
                    builder.addStatement(returnStatement)
                }.build()
            )
    }
}

class BuilderProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return ExcifyProcessor(environment.codeGenerator, environment.logger)
    }
}

/*
@OptIn(KspExperimental::class)
    private fun handleAnnotatedClass(
        klass: KSClassDeclaration,
        annotation: ExcifyCachedException,
        cachedExceptions: Sequence<KSPropertyDeclaration>,
        orThrows: Sequence<KSPropertyDeclaration>
    ) {
        val companionObject = klass.findCompanionObject()?.toClassName()
            ?: run {
                logger.error("$klass don`t have companion object")
                throw Exception("$klass don`t have companion object")
            }

        val (fileBuilder, className) = makeFileSpecBuilderFor(klass)

        /**
         * Building get method returning pre constructed object
         */

        val noArgsConstructor = klass.getConstructors().firstOrNull { it.parameters.isEmpty() }

        if (noArgsConstructor == null) {
            logger.info("No args constructor not found for $klass but caching requested")
            throw Exception("$klass don`t no args constructor")
        }

        fileBuilder
            .addProperty(
                PropertySpec.builder("cachedException", className, listOf(KModifier.PRIVATE))
                    .mutable(false)
                    .initializer("%T()", className)
                    .build()
            )
            .addFunction(
                FunSpec.builder(annotation.methodName.ifBlank { "get" })
                    .receiver(companionObject).also { funcBuilder ->
                        noArgsConstructor.parameters.forEach { param ->
                            funcBuilder.addParameter(
                                ParameterSpec.builder(
                                    name = param.name!!.getShortName(),
                                    type = param.type.toTypeName(),
                                    modifiers = param.asModifiers()
                                ).build()
                            )
                        }
                    }
                    .returns(className).let { builder ->
                        val returnStatement = "return cachedException"
                        builder.addStatement(returnStatement)
                    }.build()
            )


        /**
         * making extension methods for cached objects (ExcifyCachedException)
         */

        cachedExceptions
            .filter { it.type.toString() == klass.simpleName.getShortName() }
            .forEach { cachedException ->
                val methodName = resolveGetMethodName(cachedException)

                fileBuilder.addImport(
                    cachedException.packageName.asString(),
                    cachedException.simpleName.asString()
                )

                // making extension method
                fileBuilder
                    .addFunction(
                        FunSpec.builder(
                            methodName
                        )
                            .receiver(companionObject)
                            .returns(className)
                            .addStatement("return %L", cachedException)
                            .build()
                    )

            }

        // generating orThrow methods
        orThrows
            .filter { it.type.toString() == klass.simpleName.getShortName() }
            .forEach { orThrow ->
                val orThrowAnnotation = orThrow.getAnnotationsByType(ExcifyOptionalOrThrow::class).first()

                fileBuilder.addImport(
                    orThrow.packageName.asString(),
                    orThrow.simpleName.asString()
                )

                val typeClassName = when (val type = orThrowAnnotation.findType()) {
                    is KSType -> type.toClassName()
                    is KClass<*> -> type.asClassName()
                    else -> throw Exception("Can`t resolve type")
                }

                fileBuilder
                    .addFunction(
                        FunSpec.builder(resolveOrThrowMethodName(orThrow, orThrowAnnotation))
                            .receiver(
                                Optional::class.asClassName().parameterizedBy(
                                    typeClassName
                                )
                            )
                            .returns(typeClassName)
                            .addCode(
                                CodeBlock
                                    .builder()
                                    .beginControlFlow("return run") // because of bad kotlinpoet wired formatting
                                    .beginControlFlow("orElseThrow")
                                    .addStatement("%L", orThrow)
                                    .endControlFlow()
                                    .endControlFlow()
                                    .build()

                            )
                            .build()
                    )

            }
        return fileBuilder.build()
    }
 */