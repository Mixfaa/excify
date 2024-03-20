@ExcifyException
class SomeException : Throwable("msg") {
    companion object
}

@ExcifyException
class SomeException2(msg: String) : Throwable(msg) {
    companion object
}

fun main() {
    SomeException.make()
    SomeException2.make("hello world")

}