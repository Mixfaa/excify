@ExcifyException
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
    SomeException.make()
    SomeException2.make("hello world")
}