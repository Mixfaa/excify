import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

private fun KSClassDeclaration.findCompanionObject(): KSClassDeclaration? =
    declarations.filterIsInstance<KSClassDeclaration>().firstOrNull { it.isCompanionObject }

private fun KSValueParameter.asModifiers(): Iterable<KModifier> {
    return buildList {
        if (this@asModifiers.isVararg) add(KModifier.VARARG)
        if (this@asModifiers.isCrossInline) add(KModifier.CROSSINLINE)
    }
}

@OptIn(KspExperimental::class)
private fun resolveMethodName(cachedException: KSPropertyDeclaration): String {
    var methodName = cachedException.getAnnotationsByType(ExcifyCachedException::class).first().methodName

    if (methodName.isNotBlank()) return methodName
    methodName = cachedException.simpleName.getShortName()

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
            .filterIsInstance<KSClassDeclaration>().toSet()

        val cachedExceptions = resolver.getSymbolsWithAnnotation(ExcifyCachedException::class.qualifiedName!!, true)
            .filterIsInstance<KSPropertyDeclaration>().toSet()

        for (klass in annotatedClasses) {
            val annotation = klass.getAnnotationsByType(ExcifyException::class).first()
            makeFile(klass, annotation, cachedExceptions).writeTo(codeGenerator, Dependencies(true))
        }

        return emptyList()
    }

    private fun makeFile(
        klass: KSClassDeclaration,
        annotation: ExcifyException,
        cachedExceptions: Set<KSPropertyDeclaration>
    ): FileSpec {
        val (fileBuilder, className) = makeFileSpecBuilderFor(klass)
        val companionObject = klass.findCompanionObject()!!.toClassName()

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
                    FunSpec.builder(annotation.cachedGetName)
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

        cachedExceptions
            .filter { it.type.toString() == klass.simpleName.getShortName() }
            .forEach { cachedException ->
                val methodName = resolveMethodName(cachedException)

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
                    .addImport(cachedException.packageName.asString(), cachedException.qualifiedName!!.asString())
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