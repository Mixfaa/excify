abstract class LightweightThrowable(msg: String) : java.lang.Throwable(msg, null, true, false)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ExcifyException(
    val cacheNoArgs: Boolean = false
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class ExcifyCached(
    val methodName: String = ""
)

//
//interface JsonSerializer {
//    fun serialize(obj: Any): String
//}
//
//@Retention(AnnotationRetention.SOURCE)
//@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
//annotation class ExcifyJsonSerializer
