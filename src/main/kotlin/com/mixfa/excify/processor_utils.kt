package com.mixfa.excify

import com.google.devtools.ksp.KSTypeNotPresentException
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.KModifier


fun KSClassDeclaration.findCompanionObject(): KSClassDeclaration? =
    declarations.filterIsInstance<KSClassDeclaration>().firstOrNull { it.isCompanionObject }

fun KSValueParameter.asModifiers(): Iterable<KModifier> {
    return buildList {
        if (this@asModifiers.isVararg) add(KModifier.VARARG)
        if (this@asModifiers.isCrossInline) add(KModifier.CROSSINLINE)
    }
}

@OptIn(KspExperimental::class)
fun ExcifyOptionalOrThrow.findType(): Any = try {
    this.type
} catch (ex: KSTypeNotPresentException) {
    ex.ksType
}


class AutoLoggingException(msg: String, logger: KSPLogger) : Exception(msg) {
    init {
        logger.error(msg)
    }
}

fun KSClassDeclaration.findCompanionObjectOrThrow(logger: KSPLogger): KSClassDeclaration {
    return findCompanionObject() ?: throw AutoLoggingException("$this don`t have companion object", logger)
}

fun resolveGetMethodName(cachedException: KSPropertyDeclaration, annotation: ExcifyCachedException): String {
    var methodName = annotation.methodName

    if (methodName.isNotBlank()) return methodName
    methodName = cachedException.simpleName.getShortName()

    if (methodName.lowercase().endsWith("exception")) methodName =
        methodName.substring(0, methodName.length - "exception".length)

    return methodName
}

fun resolveOrThrowMethodName(orThrow: KSPropertyDeclaration, annotation: ExcifyOptionalOrThrow): String {
    var methodName = annotation.methodName

    if (methodName.isNotBlank()) return methodName
    val exceptionName = orThrow.simpleName.getShortName().replaceFirstChar { it.uppercase() }
    methodName = "orThrow${exceptionName}"

    if (methodName.lowercase().endsWith("exception")) methodName =
        methodName.substring(0, methodName.length - "exception".length)

    return methodName
}

fun KSClassDeclaration.excifyFilename(): String {
    return "excify_${this.simpleName.getShortName()}"
}