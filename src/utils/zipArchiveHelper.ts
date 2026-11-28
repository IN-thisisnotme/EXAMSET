/**
 * Pure TypeScript, zero-dependency ZIP archive reader & writer for Android <-> Web (.examset.zip) parity.
 * Supports Store (0) and Deflate-raw (8 via CompressionStream/DecompressionStream) plus Central Directory parsing
 * so Data Descriptors produced by Android java.util.zip.ZipOutputStream decode reliably in all browsers & Node.
 */

export const MAX_ZIP_ENTRY_BYTES = 32 * 1024 * 1024;
export const MAX_ZIP_TOTAL_BYTES = 80 * 1024 * 1024;

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) {
      c = (c & 1) !== 0 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[i] = c >>> 0;
  }
  return table;
})();

export function crc32(bytes: Uint8Array): number {
  let c = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) {
    c = CRC_TABLE[(c ^ bytes[i]) & 0xff] ^ (c >>> 8);
  }
  return (c ^ 0xffffffff) >>> 0;
}

export function isZipBytes(bytes: Uint8Array): boolean {
  return bytes.length >= 4 && bytes[0] === 0x50 && bytes[1] === 0x4b && bytes[2] === 0x03 && bytes[3] === 0x04;
}

async function runTransformStream(
  input: Uint8Array,
  stream: TransformStream<Uint8Array, Uint8Array>,
  maxOutputBytes?: number
): Promise<Uint8Array> {
  const writer = stream.writable.getWriter();
  const reader = stream.readable.getReader();

  const writePromise = (async () => {
    const chunkSize = 64 * 1024;
    for (let offset = 0; offset < input.length; offset += chunkSize) {
      await writer.write(input.subarray(offset, Math.min(input.length, offset + chunkSize)));
    }
    await writer.close();
  })();

  const chunks: Uint8Array[] = [];
  let totalLen = 0;
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    if (value) {
      totalLen += value.byteLength;
      if (maxOutputBytes && totalLen > maxOutputBytes) {
        await reader.cancel().catch(() => {});
        throw new Error(`ZIP entry exceeds safe uncompressed limit (${Math.round(maxOutputBytes / (1024 * 1024))} MB).`);
      }
      chunks.push(value);
    }
  }
  await writePromise;

  const out = new Uint8Array(totalLen);
  let pos = 0;
  for (const chunk of chunks) {
    out.set(chunk, pos);
    pos += chunk.byteLength;
  }
  return out;
}

async function deflateRaw(input: Uint8Array): Promise<{ data: Uint8Array; method: number }> {
  if (typeof CompressionStream !== 'undefined') {
    try {
      const cs = new CompressionStream('deflate-raw') as unknown as TransformStream<Uint8Array, Uint8Array>;
      const compressed = await runTransformStream(input, cs);
      return { data: compressed, method: 8 };
    } catch {
      // Fallback to ZIP Store (method 0) if deflate-raw is unsupported
    }
  }
  return { data: input, method: 0 };
}

async function inflateRaw(input: Uint8Array, maxBytes: number): Promise<Uint8Array> {
  if (typeof DecompressionStream === 'undefined') {
    throw new Error('This browser lacks native Deflate decompression; use .examset.json or upgrade your browser.');
  }
  const ds = new DecompressionStream('deflate-raw') as unknown as TransformStream<Uint8Array, Uint8Array>;
  return runTransformStream(input, ds, maxBytes);
}

function readUint16LE(bytes: Uint8Array, offset: number): number {
  return bytes[offset] | (bytes[offset + 1] << 8);
}

function readUint32LE(bytes: Uint8Array, offset: number): number {
  return (
    (bytes[offset] |
      (bytes[offset + 1] << 8) |
      (bytes[offset + 2] << 16) |
      (bytes[offset + 3] << 24)) >>>
    0
  );
}

function writeUint16LE(view: DataView, offset: number, val: number): void {
  view.setUint16(offset, val & 0xffff, true);
}

function writeUint32LE(view: DataView, offset: number, val: number): void {
  view.setUint32(offset, val >>> 0, true);
}

function findEocdOffset(bytes: Uint8Array): number {
  const minOffset = Math.max(0, bytes.length - 65_557);
  for (let i = bytes.length - 22; i >= minOffset; i--) {
    if (
      bytes[i] === 0x50 &&
      bytes[i + 1] === 0x4b &&
      bytes[i + 2] === 0x05 &&
      bytes[i + 3] === 0x06
    ) {
      return i;
    }
  }
  return -1;
}

export async function readZipEntries(bytes: Uint8Array): Promise<Map<string, Uint8Array>> {
  if (bytes.byteLength > MAX_ZIP_TOTAL_BYTES) {
    throw new Error(`Archive exceeds maximum safe size (${Math.round(MAX_ZIP_TOTAL_BYTES / (1024 * 1024))} MB).`);
  }
  if (!isZipBytes(bytes)) {
    throw new Error('Not a valid ZIP archive.');
  }

  const eocdOffset = findEocdOffset(bytes);
  if (eocdOffset < 0) {
    throw new Error('ZIP End-of-Central-Directory header not found.');
  }

  const totalEntries = readUint16LE(bytes, eocdOffset + 10);
  const cdSize = readUint32LE(bytes, eocdOffset + 12);
  const cdOffset = readUint32LE(bytes, eocdOffset + 16);
  if (cdOffset + cdSize > eocdOffset) {
    throw new Error('ZIP Central Directory bounds are corrupted.');
  }

  const decoder = new TextDecoder('utf-8');
  const entries = new Map<string, Uint8Array>();
  let cursor = cdOffset;
  let totalUncompressed = 0;

  for (let i = 0; i < totalEntries; i++) {
    if (cursor + 46 > bytes.length || readUint32LE(bytes, cursor) !== 0x02014b50) {
      throw new Error('Corrupted ZIP Central Directory entry header.');
    }

    const method = readUint16LE(bytes, cursor + 10);
    const expectedCrc = readUint32LE(bytes, cursor + 16);
    const compressedSize = readUint32LE(bytes, cursor + 20);
    const uncompressedSize = readUint32LE(bytes, cursor + 24);
    const nameLen = readUint16LE(bytes, cursor + 28);
    const extraLen = readUint16LE(bytes, cursor + 30);
    const commentLen = readUint16LE(bytes, cursor + 32);
    const localHeaderOffset = readUint32LE(bytes, cursor + 42);

    const entryName = decoder.decode(bytes.subarray(cursor + 46, cursor + 46 + nameLen));
    cursor += 46 + nameLen + extraLen + commentLen;

    if (entryName.endsWith('/')) continue;
    if (uncompressedSize > MAX_ZIP_ENTRY_BYTES) {
      throw new Error(`Entry "${entryName}" exceeds 32 MB limit.`);
    }
    totalUncompressed += uncompressedSize;
    if (totalUncompressed > MAX_ZIP_TOTAL_BYTES) {
      throw new Error('Total uncompressed ZIP contents exceed safe 80 MB budget.');
    }

    if (
      localHeaderOffset + 30 > bytes.length ||
      readUint32LE(bytes, localHeaderOffset) !== 0x04034b50
    ) {
      throw new Error(`Invalid ZIP local file header for "${entryName}".`);
    }

    const lfhNameLen = readUint16LE(bytes, localHeaderOffset + 26);
    const lfhExtraLen = readUint16LE(bytes, localHeaderOffset + 28);
    const dataStart = localHeaderOffset + 30 + lfhNameLen + lfhExtraLen;
    const dataEnd = dataStart + compressedSize;
    if (dataEnd > bytes.length) {
      throw new Error(`Truncated ZIP entry payload for "${entryName}".`);
    }

    const compressedSlice = bytes.subarray(dataStart, dataEnd);
    let plain: Uint8Array;
    if (method === 0) {
      plain = new Uint8Array(compressedSlice);
    } else if (method === 8) {
      plain = await inflateRaw(compressedSlice, MAX_ZIP_ENTRY_BYTES);
    } else {
      throw new Error(`Unsupported ZIP compression method (${method}) on "${entryName}".`);
    }

    const actualCrc = crc32(plain);
    if (expectedCrc !== 0 && actualCrc !== expectedCrc) {
      throw new Error(`CRC-32 mismatch inside ZIP entry "${entryName}".`);
    }
    entries.set(entryName, plain);
  }

  return entries;
}

export async function readZipTextEntries(bytes: Uint8Array): Promise<Map<string, string>> {
  const rawMap = await readZipEntries(bytes);
  const decoder = new TextDecoder('utf-8');
  const out = new Map<string, string>();
  rawMap.forEach((val, key) => {
    out.set(key, decoder.decode(val));
  });
  return out;
}

export async function buildZipArchive(
  entries: { name: string; data: Uint8Array | string }[]
): Promise<Uint8Array> {
  const encoder = new TextEncoder();
  const now = new Date();
  const dosTime =
    ((now.getHours() & 0x1f) << 11) |
    ((now.getMinutes() & 0x3f) << 5) |
    ((now.getSeconds() >> 1) & 0x1f);
  const dosDate =
    (((Math.max(1980, now.getFullYear()) - 1980) & 0x7f) << 9) |
    (((now.getMonth() + 1) & 0x0f) << 5) |
    (now.getDate() & 0x1f);

  interface PreparedEntry {
    nameBytes: Uint8Array;
    plainLen: number;
    compressed: Uint8Array;
    method: number;
    crc: number;
    offset: number;
  }

  const prepared: PreparedEntry[] = [];
  let localAreaSize = 0;
  let cdAreaSize = 0;

  for (const item of entries) {
    const plain = typeof item.data === 'string' ? encoder.encode(item.data) : item.data;
    const nameBytes = encoder.encode(item.name);
    const { data: compressed, method } = await deflateRaw(plain);
    const crc = crc32(plain);
    const offset = localAreaSize;

    prepared.push({
      nameBytes,
      plainLen: plain.byteLength,
      compressed,
      method,
      crc,
      offset,
    });

    localAreaSize += 30 + nameBytes.byteLength + compressed.byteLength;
    cdAreaSize += 46 + nameBytes.byteLength;
  }

  const totalSize = localAreaSize + cdAreaSize + 22;
  const out = new Uint8Array(totalSize);
  const view = new DataView(out.buffer);
  let pos = 0;

  // Local file headers + data
  for (const entry of prepared) {
    writeUint32LE(view, pos, 0x04034b50);
    writeUint16LE(view, pos + 4, 20); // version needed
    writeUint16LE(view, pos + 6, 0x0800); // UTF-8 filename flag
    writeUint16LE(view, pos + 8, entry.method);
    writeUint16LE(view, pos + 10, dosTime);
    writeUint16LE(view, pos + 12, dosDate);
    writeUint32LE(view, pos + 14, entry.crc);
    writeUint32LE(view, pos + 18, entry.compressed.byteLength);
    writeUint32LE(view, pos + 22, entry.plainLen);
    writeUint16LE(view, pos + 26, entry.nameBytes.byteLength);
    writeUint16LE(view, pos + 28, 0);
    out.set(entry.nameBytes, pos + 30);
    pos += 30 + entry.nameBytes.byteLength;
    out.set(entry.compressed, pos);
    pos += entry.compressed.byteLength;
  }

  // Central Directory records
  const cdStart = pos;
  for (const entry of prepared) {
    writeUint32LE(view, pos, 0x02014b50);
    writeUint16LE(view, pos + 4, 20); // made by
    writeUint16LE(view, pos + 6, 20); // needed
    writeUint16LE(view, pos + 8, 0x0800); // UTF-8
    writeUint16LE(view, pos + 10, entry.method);
    writeUint16LE(view, pos + 12, dosTime);
    writeUint16LE(view, pos + 14, dosDate);
    writeUint32LE(view, pos + 16, entry.crc);
    writeUint32LE(view, pos + 20, entry.compressed.byteLength);
    writeUint32LE(view, pos + 24, entry.plainLen);
    writeUint16LE(view, pos + 28, entry.nameBytes.byteLength);
    writeUint16LE(view, pos + 30, 0);
    writeUint16LE(view, pos + 32, 0);
    writeUint16LE(view, pos + 34, 0);
    writeUint16LE(view, pos + 36, 0);
    writeUint32LE(view, pos + 38, 0);
    writeUint32LE(view, pos + 42, entry.offset);
    out.set(entry.nameBytes, pos + 46);
    pos += 46 + entry.nameBytes.byteLength;
  }

  // EOCD
  writeUint32LE(view, pos, 0x06054b50);
  writeUint16LE(view, pos + 4, 0);
  writeUint16LE(view, pos + 6, 0);
  writeUint16LE(view, pos + 8, prepared.length);
  writeUint16LE(view, pos + 10, prepared.length);
  writeUint32LE(view, pos + 12, cdAreaSize);
  writeUint32LE(view, pos + 16, cdStart);
  writeUint16LE(view, pos + 20, 0);

  return out;
}
