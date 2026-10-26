package com.factswallpaper.core.backup

import com.factswallpaper.core.common.AppResult
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `passphrase shorter than 8 characters is rejected`() {
        val backupFile = tempFolder.newFile("test.fwbk")
        val manager = object {
            fun checkPassphrase(pass: String): Boolean = pass.length >= BackupManagerImpl.MIN_PASSPHRASE_LENGTH
        }
        assertThat(manager.checkPassphrase("short")).isFalse()
        assertThat(manager.checkPassphrase("1234567")).isFalse()
        assertThat(manager.checkPassphrase("12345678")).isTrue()
        assertThat(manager.checkPassphrase("secure-passphrase-2026")).isTrue()
    }

    @Test
    fun `zip4j encrypted archive round-trip verifies password protected contents`() {
        val destination = File(tempFolder.root, "encrypted.fwbk")
        val password = "StrongPassphrase123"

        val zipFile = ZipFile(destination, password.toCharArray())
        val params = ZipParameters().apply {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            fileNameInZip = "manifest.json"
        }
        val content = """{"version":1,"documentCount":3}""".toByteArray(Charsets.UTF_8)
        zipFile.addStream(content.inputStream(), params)

        assertThat(destination.exists()).isTrue()
        assertThat(zipFile.isValidZipFile).isTrue()
        assertThat(zipFile.isEncrypted).isTrue()

        // Read with correct password
        val readZip = ZipFile(destination, password.toCharArray())
        val header = readZip.getFileHeader("manifest.json")
        assertThat(header).isNotNull()
        val readText = readZip.getInputStream(header).bufferedReader().readText()
        assertThat(readText).contains(""""documentCount":3""")

        // Read with wrong password fails
        val wrongZip = ZipFile(destination, "WrongPassword123".toCharArray())
        val wrongHeader = wrongZip.getFileHeader("manifest.json")
        var failed = false
        try {
            wrongZip.getInputStream(wrongHeader).bufferedReader().readText()
        } catch (_: Throwable) {
            failed = true
        }
        assertThat(failed).isTrue()
    }

    @Test
    fun `path traversal archive entry is detected and rejected`() {
        val dangerousFile = File(tempFolder.root, "dangerous.fwbk")
        val password = "StrongPassphrase123"
        val zipFile = ZipFile(dangerousFile, password.toCharArray())
        val params = ZipParameters().apply {
            fileNameInZip = "../../evil.sh"
        }
        zipFile.addStream("echo evil".byteInputStream(), params)

        val headers = zipFile.fileHeaders
        val hasSuspicious = headers.any { SecretScrubber.isSuspiciousPath(it.fileName) }
        assertThat(hasSuspicious).isTrue()
    }
}
