import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

data class User(val name: String)

@ExcifyException(cacheNoArgs = true)
class SomeException : FastThrowable("SomeException") {
    companion object
}

@ExcifyException
class SomeException2(msg: String) : FastThrowable(msg) {
    companion object
}

@ExcifyCachedException
val userNotFoundException = SomeException2("user not found")

@ExcifyCachedException
val storeNotFoundException = SomeException2("store not found")

fun findUser(id: String) = Excify.wrap<User> {
    if (id.contains('1'))
        return@wrap SomeException2.userNotFound()

    User("mixfa")
}.orThrow()




fun main() {

    val newMapper = ObjectMapper()
        .registerKotlinModule()
        .registerExcifyModule()


    SomeException.get()


    println(newMapper.writeValueAsString(SomeException2.userNotFound()))
    println(newMapper.writeValueAsString(User("mixfa mixfa mixfa")))
    println(newMapper.writeValueAsString(SomeException2.userNotFound()))
    println(newMapper.writeValueAsString(SomeException2.storeNotFound()))

    println(findUser("user"))
    println(findUser("user1"))

}