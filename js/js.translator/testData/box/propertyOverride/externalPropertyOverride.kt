external interface A {
    @JsName("__name")
    val name: String
}

external class B: A {
    override val name: String
}

fun box(): String {
    val c = js("{ __name: 'Frodo' }")

    val a: A = c
    val b: B = c

    assertEquals(a.name, "Frodo")
    assertEquals(a.asDynamic().__name, "Frodo")

    assertEquals(b.name, "Frodo")
    assertEquals(b.asDynamic().__name, "Frodo")

    return "OK"
}