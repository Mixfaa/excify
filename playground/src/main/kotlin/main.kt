@ExcifyException(cacheNoArgs = true)
class SomeException : LightweightThrowable("SomeException") {
    companion object
}

@ExcifyException
class SomeException2(msg: String) : LightweightThrowable(msg) {
    companion object
}

fun SomeException2.Companion.userNotFound() = SomeException2("user")

class NotFoundException(subject: String) : LightweightThrowable("$subject not found") {
    companion object
}

fun main() {

    SomeException.make()
    val ex0 = SomeException.make()
    val ex1 = SomeException2.userNotFound()
    val ex2 = SomeException2.userNotFound()

    println(ex1 == ex2)

    println("hello world")
}