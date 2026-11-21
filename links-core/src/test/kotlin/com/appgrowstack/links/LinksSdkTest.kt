package com.appgrowstack.links

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class LinksSdkTest {
    @Test
    fun versionMatchesGradleProperties() {
        val properties = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "gradle.properties") }
            .first { it.isFile && it.readText().contains(Regex("(?m)^version=")) }
        val version = Regex("(?m)^version=(.+)$").find(properties.readText())!!.groupValues[1].trim()
        assertEquals(version, LinksSdk.VERSION)
        assertEquals("kotlin-app-links", LinksSdk.NAME)
    }
}
