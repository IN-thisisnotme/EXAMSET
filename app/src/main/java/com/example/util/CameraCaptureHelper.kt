package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Camera Capture & Document Photography Helper.
 * Enables capturing high-resolution photos of handwritten question notes using the
 * zero-permission Android TakePicture contract with FileProvider.
 *
 * Fully gated by FeatureToggles.ENABLE_CAMERA_NOTES_SCANNER.
 */
object CameraCaptureHelper {

    /**
     * Checks if camera capture capability is enabled and device has an available camera handler.
     */
    fun isCameraAvailable(context: Context): Boolean {
        if (!FeatureToggles.ENABLE_CAMERA_NOTES_SCANNER) return false
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        return intent.resolveActivity(context.packageManager) != null
    }

    /**
     * Creates a temporary image file in the app's cache directory and returns a secure FileProvider Uri.
     * When the camera takes the photo, it will be written directly to this Uri.
     */
    fun createTempPictureUri(context: Context): Uri? {
        if (!FeatureToggles.ENABLE_CAMERA_NOTES_SCANNER) return null
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val storageDir = File(context.cacheDir, "camera_photos")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            val imageFile = File.createTempFile("NOTES_SCAN_${timeStamp}_", ".jpg", storageDir)
            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(context, authority, imageFile)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cleans up temporary photo files stored in the cache directory to keep device storage clean.
     */
    fun cleanupTempFiles(context: Context) {
        if (!FeatureToggles.ENABLE_CAMERA_NOTES_SCANNER) return
        try {
            val storageDir = File(context.cacheDir, "camera_photos")
            if (storageDir.exists()) {
                storageDir.listFiles()?.forEach { file ->
                    if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            // Non-critical cache cleanup
        }
    }
}
