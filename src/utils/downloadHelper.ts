/**
 * Cross-browser safe Blob download helper.
 * Defers URL.revokeObjectURL so Firefox and Safari/WebKit finish reading the object URL asynchronously.
 */
export function downloadBlobSafely(blob: Blob, fileName: string): void {
  if (typeof document === 'undefined' || typeof URL === 'undefined') return;
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.rel = 'noopener';
  link.style.display = 'none';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  setTimeout(() => {
    try {
      URL.revokeObjectURL(url);
    } catch {
      // Ignore revoke errors
    }
  }, 2500);
}

export function downloadTextSafely(
  text: string,
  fileName: string,
  mimeType: string = 'application/json;charset=utf-8'
): void {
  downloadBlobSafely(new Blob([text], { type: mimeType }), fileName);
}

export function downloadBytesSafely(
  bytes: Uint8Array,
  fileName: string,
  mimeType: string = 'application/zip'
): void {
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  downloadBlobSafely(new Blob([copy.buffer], { type: mimeType }), fileName);
}
