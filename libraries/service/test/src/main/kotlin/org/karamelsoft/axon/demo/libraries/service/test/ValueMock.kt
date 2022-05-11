package org.karamelsoft.axon.demo.libraries.service.test

import io.mockk.every
import io.mockk.mockk

inline fun <reified T: Any> anyVal(): T {
    val mock = mockk<T>()

    every { mock.toString() } returns "mockk(${T::class.simpleName})"
    every { mock.equals(any()) } returns true

    return mock
}
