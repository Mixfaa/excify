
@ExcifyException(canBeCached = true)
class SomeException : NoStacktraceThrowable("msg") {
    companion object
}

@ExcifyException
class SomeException2(msg: String) : NoStacktraceThrowable(msg) {
    companion object
}

class NotFoundException(subject: String) : NoStacktraceThrowable("$subject not found") {
    companion object
}

fun main() {
    val ex0 = SomeException.make()
    val ex1 = SomeException2.make("hello")
    val ex2 = SomeException2.make("hello")

    println(ex1 == ex2)

    println("hello world")
}