import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ksp.writeTo

val KSClassDeclaration.companionObject: KSClassDeclaration?
    get() = declarations.filterIsInstance<KSClassDeclaration>().firstOrNull { it.isCompanionObject }

val KSClassDeclaration.isSealed
    get() = modifiers.contains(Modifier.SEALED)

val KSClassDeclaration.isDataClass
    get() = classKind == ClassKind.CLASS && modifiers.contains(Modifier.DATA)

val KSClassDeclaration.isValue
    get() = modifiers.contains(Modifier.VALUE)

fun KSValueParameter.asModifiers(): Iterable<KModifier> {
    return buildList {
        if (this@asModifiers.isVararg)
            add(KModifier.VARARG)
        if (this@asModifiers.isCrossInline)
            add(KModifier.CROSSINLINE)
    }
}

class ExcifyProcessor(
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotatedClasses = resolver.getSymbolsWithAnnotation(ExcifyException::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .toSet()


        for (klass in annotatedClasses) {
            makeFile(klass)
                .writeTo(codeGenerator, Dependencies(true))
        }

        return emptyList()
    }

    fun makeFile(klass: KSClassDeclaration): FileSpec {
        val packageName = klass.packageName.asString()

        val companionObject = klass.companionObject!!.javaClass.kotlin

        return FileSpec.builder(packageName, "excify_gen_exceptions")
            .apply {
                klass.getConstructors().forEach { constructor ->
                    addFunction(
                        FunSpec.builder("make")
                            .receiver(companionObject)
                            .let { funcBuilder ->

                                constructor.parameters.forEach { param ->
                                    funcBuilder.addParameter(
                                        ParameterSpec.builder(
                                            name = param.name!!.asString(),
                                            type = param.type.javaClass.kotlin,
                                            modifiers = param.asModifiers()
                                        ).build()
                                    )
                                }

                                funcBuilder
                            }
                            .returns(Throwable::class)
                            .addStatement("return %T(%P) as Throwable")
                            .build()
                    )
                }
            }
            .build()
    }
}

class BuilderProcessorProvider : SymbolProcessorProvider {
    override fun create(
        env: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return ExcifyProcessor(env.codeGenerator)
    }
}