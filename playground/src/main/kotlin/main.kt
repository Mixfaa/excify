@ExcifyException(cacheNoArgs = true)
class SomeException : LightweightThrowable("SomeException") {
    companion object
}

@ExcifyException
class SomeException2(msg: String) : LightweightThrowable(msg) {
    companion object
}

@ExcifyCached
val userNotFoundException = SomeException2("user not found")

fun main() {
    SomeException2.userNotFound()

    println("hello world")
}