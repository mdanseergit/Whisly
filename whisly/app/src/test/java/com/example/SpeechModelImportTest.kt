package com.example

import com.example.ai.stt.ManifestResult
import com.example.ai.stt.SttManifestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Import is the only way a speech model enters this app, so the validator is the security boundary.
 * These tests pin the rules that stop a corrupt or hostile package from being installed.
 */
class SpeechModelImportTest {

    private fun manifest(
        extraFiles: String = "",
        decoder: String = """"decoder": "decoder.onnx",""",
        engine: String = "whisper",
        license: String = """"license": "MIT",""",
        attribution: String = """"attribution": "OpenAI / k2-fsa",""",
        includeLicenseFile: Boolean = true
    ): String {
        val licenseEntry = if (includeLicenseFile) {
            """,{"path": "LICENSE", "sha256": "${"a".repeat(64)}", "size": 100}"""
        } else {
            ""
        }
        return """
        {
          "schemaVersion": 1,
          "packId": "stt_test_pack",
          "engine": "$engine",
          "languages": ["ta"],
          "encoder": "encoder.onnx",
          $decoder
          "tokens": "tokens.txt",
          $license
          $attribution
          "files": [
            {"path": "encoder.onnx", "sha256": "${"b".repeat(64)}", "size": 1000},
            {"path": "decoder.onnx", "sha256": "${"c".repeat(64)}", "size": 500},
            {"path": "tokens.txt", "sha256": "${"d".repeat(64)}", "size": 10}
            $licenseEntry
            $extraFiles
          ]
        }
        """.trimIndent()
    }

    @Test
    fun `valid whisper manifest is accepted`() {
        val result = SttManifestParser.parse(manifest())
        assertTrue(result is ManifestResult.Valid)
        val parsed = (result as ManifestResult.Valid).manifest
        assertEquals("stt_test_pack", parsed.packId)
        assertEquals("whisper", parsed.engine)
        assertEquals(listOf("ta"), parsed.languages)
        assertEquals(4, parsed.files.size)
    }

    @Test
    fun `package without a LICENSE file is rejected`() {
        val result = SttManifestParser.parse(manifest(includeLicenseFile = false))
        assertTrue(result is ManifestResult.Invalid)
        assertTrue((result as ManifestResult.Invalid).reason.contains("LICENSE"))
    }

    @Test
    fun `path traversal in a declared file is rejected`() {
        val result = SttManifestParser.parse(
            manifest(extraFiles = """ ,{"path": "../evil.onnx", "sha256": "${"e".repeat(64)}", "size": 10}""")
        )
        assertTrue(result is ManifestResult.Invalid)
        assertTrue((result as ManifestResult.Invalid).reason.contains("unsafe"))
    }

    @Test
    fun `malformed checksum is rejected`() {
        val result = SttManifestParser.parse(
            manifest(extraFiles = """ ,{"path": "x.onnx", "sha256": "not-a-hash", "size": 10}""")
        )
        assertTrue(result is ManifestResult.Invalid)
        assertTrue((result as ManifestResult.Invalid).reason.contains("sha256"))
    }

    @Test
    fun `unknown engine is rejected`() {
        val result = SttManifestParser.parse(manifest(engine = "cloud_upload"))
        assertTrue(result is ManifestResult.Invalid)
        assertTrue((result as ManifestResult.Invalid).reason.contains("engine"))
    }

    @Test
    fun `missing license field is rejected`() {
        val result = SttManifestParser.parse(manifest(license = """"license": "","""))
        assertTrue(result is ManifestResult.Invalid)
    }

    @Test
    fun `whisper tiny multilingual manifest parses and matches the English pack id`() {
        val json = javaClass.classLoader!!
            .getResourceAsStream("whisper-manifest.json")!!
            .bufferedReader().readText()
        val result = SttManifestParser.parse(json)
        assertTrue("result was $result", result is ManifestResult.Valid)
        val m = (result as ManifestResult.Valid).manifest
        assertEquals("stt_whisper_tiny_multilingual", m.packId)
        assertEquals("whisper", m.engine)
        assertTrue(m.languages.contains("en"))
        assertTrue(m.languages.contains("ta"))
        assertEquals(4, m.files.size)
        // Every declared file must have a 64-hex sha256 and positive size.
        m.files.forEach { f ->
            assertTrue(f.sha256.matches(Regex("^[0-9a-f]{64}$")))
            assertTrue(f.size > 0)
        }
    }

    @Test
    fun `duplicate file entries are rejected`() {
        val result = SttManifestParser.parse(
            manifest(extraFiles = """ ,{"path": "tokens.txt", "sha256": "${"f".repeat(64)}", "size": 10}""")
        )
        assertTrue(result is ManifestResult.Invalid)
        assertTrue((result as ManifestResult.Invalid).reason.contains("duplicate"))
    }
}
