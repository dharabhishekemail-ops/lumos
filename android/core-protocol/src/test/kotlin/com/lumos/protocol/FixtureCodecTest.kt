package com.lumos.protocol
import java.io.File
import kotlin.test.*
class FixtureCodecTest {
 private fun fixture(path:String): String { val root = System.getProperty("lumos.fixtures.root") ?: "shared/fixtures"; return File(root, path).readText() }
 @Test fun loadsHelloFixture(){ assertTrue(fixture("protocol/capabilities_request_v1.json").contains("\"messageType\":\"HELLO\"")) }
 @Test fun truncatedFixtureFails(){ assertFails { ProtocolJson.instance.parseToJsonElement(fixture("protocol/truncated_negative.json")) } }
}
