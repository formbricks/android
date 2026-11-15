package com.formbricks.android.extensions

/**
 * Swift like guard statement.
 * To achieve that, on null the statement must return an empty T object
 */
public inline fun <reified T> T?.guard(block: T?.() -> Unit): T {
    this?.let {
        return it
    } ?: run {
        block()
    }

    return T::class.java.getDeclaredConstructor().newInstance()
}

public inline fun String?.guardEmpty(block: String?.() -> Unit): String {
    if (isNullOrBlank()) {
        block()
    } else {
        return this
    }

    return ""
}

public inline fun <T : Any> guardLet(vararg elements: T?, closure: () -> Nothing): List<T> =
    if (elements.all {
            it != null
        }
    ) {
        elements.filterNotNull()
    } else {
        closure()
    }
