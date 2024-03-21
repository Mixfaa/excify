import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

fun KSClassDeclaration.findCompanionObject(): KSClassDeclaration? =
    declarations.filterIsInstance<KSClassDeclaration>().firstOrNull { it.isCompanionObject }

fun KSValueParameter.asModifiers(): Iterable<KModifier> {
    return buildList {
        if (this@asModifiers.isVararg) add(KModifier.VARARG)
        if (this@asModifiers.isCrossInline) add(KModifier.CROSSINLINE)
    }
}

class ExcifyProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotatedClasses = resolver.getSymbolsWithAnnotation(ExcifyException::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>().toSet()


        for (klass in annotatedClasses) {
            run {
                if (klass.getAnnotationsByType(ExcifyException::class).first().cacheNoArgs)
                    makeCached(klass)
                else
                    makeSimple(klass)
            }.writeTo(codeGenerator, Dependencies(true))
        }

        return emptyList()
    }

    fun makeCached(klass: KSClassDeclaration): FileSpec {
        val noArgsConstructor = klass.getConstructors().firstOrNull { it.parameters.isEmpty() }
            ?: run {
                logger.error("No args constructor not found")
                throw Exception("No args constructor not found")
            }

        val packageName = klass.packageName.asString()

        val className = klass.toClassName()
        val companionObject = klass.findCompanionObject()!!.toClassName()

        return FileSpec.builder(packageName, "excify_${className.simpleName}")
            .addProperty(
                PropertySpec.builder(
                    name = "cachedException",
                    type = Throwable::class,
                    modifiers = listOf(KModifier.PRIVATE)
                )
                    .mutable(false)
                    .initializer("%T() as Throwable", className)
                    .build()
            )
            .addFunction(
                FunSpec.builder("make")
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
                    .returns(Throwable::class).let { builder ->
                        val returnStatement = "return cachedException"
                        builder.addStatement(returnStatement)
                    }.build()
            )
            .build()
    }

    fun makeSimple(klass: KSClassDeclaration): FileSpec {
        val packageName = klass.packageName.asString()

        val companionObject = klass.findCompanionObject()!!.toClassName()

        return FileSpec.builder(packageName, "excify_${klass.toClassName().simpleName}").apply {


            klass.getConstructors().forEach { constructor ->
                addFunction(FunSpec.builder("make").receiver(companionObject).let { funcBuilder ->

                    constructor.parameters.forEach { param ->
                        funcBuilder.addParameter(
                            ParameterSpec.builder(
                                name = param.name!!.getShortName(),
                                type = param.type.toTypeName(),
                                modifiers = param.asModifiers()
                            ).build()
                        )
                    }

                    funcBuilder
                }.returns(Throwable::class).let { builder ->
                    val returnStatement = buildString {
                        append("return %T(")

                        constructor.parameters.forEach { param ->
                            append(param.name!!.getShortName())
                            append(", ") // kotlin don`t care
                        }

                        append(") as Throwable")
                    }

                    builder.addStatement(returnStatement, klass.toClassName())
                }.build())
            }
        }.build()
    }
}

class BuilderProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return ExcifyProcessor(environment.codeGenerator, environment.logger)
    }
}