import arrow.core.Either
import arrow.core.left
import arrow.core.right

open class LightweightThrowable(msg: String) : java.lang.Throwable(msg, null, true, false) {
    inline fun asThrowable() = this as Throwable
}

class UnknownLWThrowable(val value: Any) : LightweightThrowable(value.toString())

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ExcifyException(
    val cacheNoArgs: Boolean = false
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class ExcifyCachedException(
    val methodName: String = ""
)

typealias EitherError<T> = Either<LightweightThrowable, T>

fun <T> EitherError<T>.orThrow() : T = when (this) {
    is Either.Left -> throw this.value.asThrowable()
    is Either.Right -> this.value
}

object Excify {
    inline fun <reified TargetType> rethrow(block: () -> Any): TargetType = when (val returnedValue = block()) {
        is TargetType -> returnedValue
        is LightweightThrowable -> throw returnedValue.asThrowable()
        else -> throw UnknownLWThrowable(returnedValue).asThrowable()
    }

    inline fun <reified TargetType> wrap(block: () -> Any): EitherError<TargetType> = when (val returnedValue = block()) {
        is TargetType -> returnedValue.right()
        is LightweightThrowable -> returnedValue.left()
        else -> UnknownLWThrowable(returnedValue).left()
    }
}