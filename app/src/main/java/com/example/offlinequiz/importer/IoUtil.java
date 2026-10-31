package com.example.offlinequiz.importer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Small IO helpers shared by the parsers. */
final class IoUtil {

    private IoUtil() {
    }

    static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(8192, in.available()));
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * Decodes text that may be UTF-8, GBK or GB18030 (banks exported from Excel on a
     * Chinese Windows machine are usually GBK). A UTF-8 BOM is stripped.
     */
    static String decode(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xEF
                && (bytes[1] & 0xff) == 0xBB && (bytes[2] & 0xff) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xFF && (bytes[1] & 0xff) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        String utf8 = tryDecode(bytes, StandardCharsets.UTF_8);
        if (utf8 != null) {
            return utf8;
        }
        String gbk = tryDecode(bytes, Charset.forName("GBK"));
        if (gbk != null) {
            return gbk;
        }
        String gb18030 = tryDecode(bytes, Charset.forName("GB18030"));
        return gb18030 != null ? gb18030 : new String(bytes, StandardCharsets.UTF_8);
    }

    private static String tryDecode(byte[] bytes, Charset charset) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
