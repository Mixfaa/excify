package com.mixfa.excify
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import kotlin.reflect.KClass

/**
 * fast throwable class (no stack trace)
 */
open class FastThrowable(msg: String) : Throwable(msg, null, true, false)

class UnknownFastThrowable(val value: Any) : FastThrowable(value.toString())

/**
 * Annotations
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ExcifyException(
    val cacheNoArgs: Boolean = true,
    val cachedGetName: String = "get"
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class ExcifyCachedException(
    val methodName: String = ""
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class ExcifyOptionalOrThrow(
    val type: KClass<*>,
    val methodName:String = ""
)

/**
 * Arrow`s Either
 */
typealias EitherError<T> = Either<FastThrowable, T>

fun <T> EitherError<T>.orThrow(): T = when (this) {
    is Either.Left -> throw this.value
    is Either.Right -> this.value
}

/**
 * excify scopes
 */
object Excify {
    inline fun <reified TargetType> rethrow(block: () -> Any): TargetType = when (val returnedValue = block()) {
        is TargetType -> returnedValue
        is FastThrowable -> throw returnedValue
        else -> throw UnknownFastThrowable(returnedValue)
    }

    inline fun <reified TargetType> wrap(block: () -> Any): EitherError<TargetType> =
        when (val returnedValue = block()) {
            is TargetType -> returnedValue.right()
            is FastThrowable -> returnedValue.left()
            else -> UnknownFastThrowable(returnedValue).left()
        }
}

/**
 * Fasterxml
 */
class FastThrowableSerializer : StdSerializer<FastThrowable>(FastThrowable::class.java) {
    override fun serialize(value: FastThrowable, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()

        val localizedMessage = value.localizedMessage
        if (localizedMessage != null)
            gen.writeStringField("message", localizedMessage)

        gen.writeEndObject()
    }
}

fun ObjectMapper.registerExcifyModule() =
    this.registerModule(SimpleModule().addSerializer(FastThrowableSerializer()))