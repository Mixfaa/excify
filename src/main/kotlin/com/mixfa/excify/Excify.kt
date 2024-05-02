package com.mixfa.excify

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import kotlin.reflect.KClass

/**
 * fast throwable class (no stack trace)
 */
open class FastException(message: String, cause: Throwable?) : Throwable(message, cause, true, false) {
    constructor(message: String) : this(message, null)
    constructor(cause: Throwable) : this(cause.message ?: "", cause)
}
/**
 * Annotations
 */

/**
 * Generates .userNameNotFound for annotated properties
 * Generates .get for annotated classes
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
annotation class ExcifyCachedException(
    val methodName: String = ""
)

/**
 * Generates .orThrow for Optional<T> receiver or nullable receiver
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class ExcifyOptionalOrThrow(
    val type: KClass<*>,
    val methodName: String = "",
    val makeForNullable: Boolean = true
)

/**
 * Fasterxml
 */
class FastThrowableSerializer : StdSerializer<FastException>(FastException::class.java) {
    override fun serialize(value: FastException, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()

        val localizedMessage = value.localizedMessage
        if (localizedMessage != null)
            gen.writeStringField("message", localizedMessage)

        gen.writeEndObject()
    }
}

fun ObjectMapper.registerExcifyModule() =
    this.registerModule(SimpleModule().addSerializer(FastThrowableSerializer()))