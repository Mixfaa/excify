import com.google.devtools.ksp.KSTypeNotPresentException
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import java.util.*
import kotlin.reflect.KClass

private fun KSClassDeclaration.findCompanionObject(): KSClassDeclaration? =
    declarations.filterIsInstance<KSClassDeclaration>().firstOrNull { it.isCompanionObject }

private fun KSValueParameter.asModifiers(): Iterable<KModifier> {
    return buildList {
        if (this@asModifiers.isVararg) add(KModifier.VARARG)
        if (this@asModifiers.isCrossInline) add(KModifier.CROSSINLINE)
    }
}

/**
 * returns KSType or KClass
 */
@OptIn(KspExperimental::class)
private fun ExcifyOptionalOrThrow.findType(): Any = try {
    this.type
} catch (ex: KSTypeNotPresentException) {
    ex.ksType
}


@OptIn(KspExperimental::class)
private fun resolveGetMethodName(cachedException: KSPropertyDeclaration): String {
    var methodName = cachedException.getAnnotationsByType(ExcifyCachedException::class).first().methodName

    if (methodName.isNotBlank()) return methodName
    methodName = cachedException.simpleName.getShortName()

    if (methodName.lowercase().endsWith("exception"))
        methodName = methodName.substring(0, methodName.length - "exception".length)

    return methodName
}

private fun resolveOrThrowMethodName(orThrow: KSPropertyDeclaration, annotation: ExcifyOptionalOrThrow): String {
    var methodName = annotation.methodName

    if (methodName.isNotBlank()) return methodName
    val exceptionName = orThrow.simpleName.getShortName().replaceFirstChar { it.uppercase() }
    methodName = "orThrow${exceptionName}"

    if (methodName.lowercase().endsWith("exception"))
        methodName = methodName.substring(0, methodName.length - "exception".length)

    return methodName
}

private fun makeFileSpecBuilderFor(klass: KSClassDeclaration): Pair<FileSpec.Builder, ClassName> {
    val packageName = klass.packageName.asString()
    val className = klass.toClassName()

    return FileSpec.builder(packageName, "excify_${className.simpleName}") to className
}

class ExcifyProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {

        val annotatedClasses = resolver.getSymbolsWithAnnotation(ExcifyException::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>().toList()

        val cachedExceptions = resolver.getSymbolsWithAnnotation(ExcifyCachedException::class.qualifiedName!!, true)
            .filterIsInstance<KSPropertyDeclaration>().toList()

        val orThrows = resolver.getSymbolsWithAnnotation(ExcifyOptionalOrThrow::class.qualifiedName!!, true)
            .filterIsInstance<KSPropertyDeclaration>().toList()

        for (klass in annotatedClasses) {
            val annotation = klass.getAnnotationsByType(ExcifyException::class).first()
            makeFile(klass, annotation, cachedExceptions, orThrows).writeTo(codeGenerator, Dependencies(true))
        }

        return emptyList()
    }

    @OptIn(KspExperimental::class)
    private fun makeFile(
        klass: KSClassDeclaration,
        annotation: ExcifyException,
        cachedExceptions: List<KSPropertyDeclaration>,
        orThrows: List<KSPropertyDeclaration>
    ): FileSpec {
        val (fileBuilder, className) = makeFileSpecBuilderFor(klass)
        val companionObject = klass.findCompanionObject()!!.toClassName()

        /**
         * Building get/make method returning pre constructed object
         */
        if (annotation.cacheNoArgs) {
            val noArgsConstructor = klass.getConstructors().firstOrNull { it.parameters.isEmpty() }
                ?: run {
                    logger.error("No args constructor not found")
                    throw Exception("No args constructor not found")
                }

            fileBuilder
                .addProperty(
                    PropertySpec.builder("cachedException", className, listOf(KModifier.PRIVATE))
                        .mutable(false)
                        .initializer("%T()", className)
                        .build()
                )
                .addFunction(
                    FunSpec.builder(annotation.cachedGetName.ifBlank { "get" })
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
        }

        /**
         * making make methods with for all constructors
         */
        fileBuilder
            .apply {
                val targetConstructors = klass.getConstructors()
                    .let { constructors ->
                        if (annotation.cacheNoArgs)
                            constructors.filter { it.parameters.isNotEmpty() }
                        else
                            constructors
                    }

                targetConstructors.forEach { constructor ->
                    addFunction(FunSpec.builder("make").receiver(companionObject).also { funcBuilder ->

                        constructor.parameters.forEach { param ->
                            funcBuilder.addParameter(
                                ParameterSpec.builder(
                                    name = param.name!!.getShortName(),
                                    type = param.type.toTypeName(),
                                    modifiers = param.asModifiers()
                                ).build()
                            )
                        }

                    }.returns(className).let { funcBuilder ->
                        val returnStatement = buildString {
                            append("return %T(")

                            constructor.parameters.forEach { param ->
                                append(param.name!!.getShortName())
                                append(", ") // kotlin don`t care
                            }

                            append(")")
                        }

                        funcBuilder.addStatement(returnStatement, className)
                    }.build())
                }
            }

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
}

class BuilderProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return ExcifyProcessor(environment.codeGenerator, environment.logger)
    }
}