data class User(val name: String)

@ExcifyException(cacheNoArgs = true)
class SomeException : LightweightThrowable("SomeException") {
    companion object
}

@ExcifyException
class SomeException2(msg: String) : LightweightThrowable(msg) {
    companion object
}

@ExcifyCachedException
val userNotFoundException = SomeException2("user not found")

@ExcifyCachedException
val storeNotFoundException = SomeException2("store not found")

fun findUser(id: String) = Excify.wrap<User> {
    if (id.contains('1'))
        return@wrap SomeException2.userNotFound()

    return@wrap "I dont know"
}


fun main() {
    println(findUser("user"))
    println(findUser("user1"))
}