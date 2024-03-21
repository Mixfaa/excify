abstract class LightweightThrowable(msg: String) : java.lang.Throwable(msg, null, true, false)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ExcifyException(
    val cacheNoArgs: Boolean = false
)

