## Excify 
#### (beta)
Tool to make exceptions Faster / more flexible (in future) / less word expensive

### Use cases:
* Automatically generate .exceptionName() methods for companion object
* generate .get() method for cached exception
* generate .orThrowUserNotFound() methods for Optional<User> and User? receivers (for example)
* More use cases in future!

### Lets look at some code
Example 1
```kotlin
// exception class
/**
 * FastThrowable - Throwable without stacktrace writing
 */

class NotFoundException(subject: String) : FastThrowable("$subject not found!") {
    companion object
}

@ExcifyCachedException
val userNotFoundException = NotFoundException("User")

/* 
It will generate NotFoundException.userNotFound() method, which will return userNotFoundException object
 */
```
Example 2
```kotlin
@ExcifyCachedException
@ExcifyOptionalOrThrow(type = Product::class, methodName = "orThrow")
val productNotFound = NotFoundException("Product")

/*
It will generate 
1) NotFoundException.productNotFound()
2) Optional<Product>.orThrow() method, which will throw productNotFound or return Product 
3) Product?.orThrow() method, with same as Optional<Product> logic
 */

```

