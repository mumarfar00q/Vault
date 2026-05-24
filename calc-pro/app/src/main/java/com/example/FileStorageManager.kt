package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object FileStorageManager {

    fun getVaultDir(context: Context, subDirName: String): File {
        val dir = File(context.filesDir, "vault/$subDirName")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun clearTempDirectory(context: Context) {
        try {
            val tempDir = File(context.filesDir, "vault/temp")
            if (tempDir.exists()) {
                tempDir.listFiles()?.forEach { file ->
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getSecretKey(): SecretKey {
        val bytes = VaultDatabase.getPassphraseBytes() ?: "vault_fallback_key_1234_default_pins".toByteArray().copyOf(32)
        val keyBytes = if (bytes.size >= 32) bytes.copyOf(32) else {
            val padded = ByteArray(32)
            System.arraycopy(bytes, 0, padded, 0, bytes.size)
            padded
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encryptBytes(dataBytes: ByteArray, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val ciphertext = cipher.doFinal(dataBytes)
        
        val result = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(ciphertext, 0, result, iv.size, ciphertext.size)
        return result
    }

    fun decryptBytes(encryptedBytes: ByteArray, secretKey: SecretKey): ByteArray {
        if (encryptedBytes.size < 12) {
            throw IllegalArgumentException("Encrypted content too short")
        }
        val iv = ByteArray(12)
        System.arraycopy(encryptedBytes, 0, iv, 0, 12)
        
        val ciphertextLength = encryptedBytes.size - 12
        val ciphertext = ByteArray(ciphertextLength)
        System.arraycopy(encryptedBytes, 12, ciphertext, 0, ciphertextLength)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
    }

    suspend fun importFile(context: Context, sourcePath: String, type: String): String {
        return withContext(Dispatchers.IO) {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                throw FileNotFoundException("Source file not found at $sourcePath")
            }
            
            val uuid = UUID.randomUUID().toString()
            val extension = sourceFile.extension
            val destFileName = if (extension.isNotEmpty()) "$uuid.$extension" else uuid
            
            val subDirName = when (type.uppercase()) {
                "PHOTO" -> "photos"
                "VIDEO" -> "videos"
                "AUDIO" -> "audio"
                else -> "documents"
            }
            val destDir = getVaultDir(context, subDirName)
            val destFile = File(destDir, destFileName)
            
            val fileBytes = sourceFile.readBytes()
            val encryptedBytes = encryptBytes(fileBytes, getSecretKey())
            destFile.writeBytes(encryptedBytes)
            
            try {
                generateThumbnail(context, sourceFile, uuid, type)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            try {
                sourceFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            destFile.absolutePath
        }
    }

    suspend fun readFileDecrypted(storedPath: String): ByteArray {
        return withContext(Dispatchers.IO) {
            val file = File(storedPath)
            if (!file.exists()) {
                throw FileNotFoundException("Encrypted file not found at $storedPath")
            }
            val encryptedBytes = file.readBytes()
            decryptBytes(encryptedBytes, getSecretKey())
        }
    }

    suspend fun exportFileTemp(context: Context, storedPath: String): String {
        return withContext(Dispatchers.IO) {
            val encFile = File(storedPath)
            if (!encFile.exists()) {
                throw FileNotFoundException("Encrypted file not found at $storedPath")
            }
            
            val decryptedBytes = readFileDecrypted(encFile.absolutePath)
            val ext = encFile.extension
            val tempFileName = "temp_${UUID.randomUUID()}" + (if (ext.isNotEmpty()) ".$ext" else "")
            val tempDir = getVaultDir(context, "temp")
            val tempFile = File(tempDir, tempFileName)
            
            tempFile.writeBytes(decryptedBytes)
            
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                delay(60000)
                try {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            tempFile.absolutePath
        }
    }

    suspend fun deleteFile(context: Context, storedPath: String) {
        withContext(Dispatchers.IO) {
            val srcFile = File(storedPath)
            if (srcFile.exists()) {
                val recycleBinDir = getVaultDir(context, "recycle_bin")
                val destFile = File(recycleBinDir, srcFile.name)
                srcFile.renameTo(destFile)
            }
        }
    }

    suspend fun restoreFile(context: Context, storedPath: String) {
        withContext(Dispatchers.IO) {
            val fileInRecycleBin = File(getVaultDir(context, "recycle_bin"), File(storedPath).name)
            val destFile = File(storedPath)
            if (fileInRecycleBin.exists()) {
                fileInRecycleBin.renameTo(destFile)
            }
        }
    }

    suspend fun permanentDelete(context: Context, storedPath: String) {
        withContext(Dispatchers.IO) {
            val file = File(storedPath)
            if (file.exists()) {
                file.delete()
            }
            val fileInRecycleBin = File(getVaultDir(context, "recycle_bin"), File(storedPath).name)
            if (fileInRecycleBin.exists()) {
                fileInRecycleBin.delete()
            }
            try {
                val uuid = File(storedPath).nameWithoutExtension
                val thumbFile = File(getThumbnailPath(context, uuid))
                if (thumbFile.exists()) {
                    thumbFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getThumbnailPath(context: Context, uuid: String): String {
        val thumbnailsDir = getVaultDir(context, "thumbnails")
        return File(thumbnailsDir, "$uuid.jpg").absolutePath
    }

    private fun generateThumbnail(context: Context, sourceFile: File, uuid: String, type: String) {
        val thumbnailsDir = getVaultDir(context, "thumbnails")
        val thumbFile = File(thumbnailsDir, "$uuid.jpg")
        
        when (type.uppercase()) {
            "PHOTO" -> {
                try {
                    val originalBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
                    if (originalBitmap != null) {
                        val thumbBitmap = Bitmap.createScaledBitmap(originalBitmap, 256, 256, true)
                        FileOutputStream(thumbFile).use { out ->
                            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            "VIDEO" -> {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(sourceFile.absolutePath)
                    val originalBitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    retriever.release()
                    if (originalBitmap != null) {
                        val thumbBitmap = Bitmap.createScaledBitmap(originalBitmap, 256, 256, true)
                        FileOutputStream(thumbFile).use { out ->
                            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            "AUDIO", "DOCUMENT" -> {
                try {
                    val fallbackBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(fallbackBitmap)
                    val paint = Paint()
                    paint.color = if (type.uppercase() == "AUDIO") Color.DKGRAY else Color.LTGRAY
                    canvas.drawRect(0f, 0f, 256f, 256f, paint)
                    FileOutputStream(thumbFile).use { out ->
                        fallbackBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
