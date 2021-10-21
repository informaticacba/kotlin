external interface A {
    @JsName("__name")
    val name: String

    @JsName("bar")
    fun foo(): String
}

external class B: A {
    override val name: String
    override fun foo(): String
}

fun box(): String {
    val c = js("{ __name: 'Frodo', bar: function() { return 'Frodo' } }")

    val a: A = c
    val b: B = c

    assertEquals(a.name, "Frodo")
    assertEquals(a.asDynamic().__name, "Frodo")

    assertEquals(b.name, "Frodo")
    assertEquals(b.asDynamic().__name, "Frodo")

    assertEquals(a.foo(), "Frodo")
    assertEquals(a.asDynamic().bar(), "Frodo")

    assertEquals(b.foo(), "Frodo")
    assertEquals(b.asDynamic().bar(), "Frodo")

    return "OK"
}