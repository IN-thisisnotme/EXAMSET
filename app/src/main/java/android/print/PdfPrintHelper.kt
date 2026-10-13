package android.print

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Helper located in package `android.print` to allow access to package-private constructors
 * of `PrintDocumentAdapter.LayoutResultCallback` and `PrintDocumentAdapter.WriteResultCallback`.
 */
object PdfPrintHelper {

    fun printAdapterToFile(
        adapter: PrintDocumentAdapter,
        attributes: PrintAttributes,
        destinationFile: File,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val fileDescriptor: ParcelFileDescriptor
        try {
            fileDescriptor = ParcelFileDescriptor.open(
                destinationFile,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE
            )
        } catch (e: Exception) {
            onError("Unable to create file: ${e.message}")
            return
        }

        adapter.onLayout(
            null,
            attributes,
            CancellationSignal(),
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    adapter.onWrite(
                        arrayOf(PageRange.ALL_PAGES),
                        fileDescriptor,
                        CancellationSignal(),
                        object : PrintDocumentAdapter.WriteResultCallback() {
                            override fun onWriteFinished(pages: Array<out PageRange>?) {
                                try {
                                    fileDescriptor.close()
                                } catch (_: Exception) {}
                                onSuccess()
                            }

                            override fun onWriteFailed(error: CharSequence?) {
                                try {
                                    fileDescriptor.close()
                                } catch (_: Exception) {}
                                onError(error?.toString() ?: "Failed to write PDF")
                            }

                            override fun onWriteCancelled() {
                                try {
                                    fileDescriptor.close()
                                } catch (_: Exception) {}
                                onError("PDF rendering was cancelled")
                            }
                        }
                    )
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    try {
                        fileDescriptor.close()
                    } catch (_: Exception) {}
                    onError(error?.toString() ?: "Layout calculation failed")
                }

                override fun onLayoutCancelled() {
                    try {
                        fileDescriptor.close()
                    } catch (_: Exception) {}
                    onError("Layout calculation was cancelled")
                }
            },
            Bundle()
        )
    }
}
