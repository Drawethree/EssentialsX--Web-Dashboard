package dev.drawethree.essdash.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Self-contained TOTP (RFC 6238) implementation — no external dependency, so nothing extra to
 * shade or relocate. Uses the standard 30-second step, 6 digits, HMAC-SHA1, compatible with
 * Google Authenticator, Authy, 1Password, etc.
 */
public final class TotpService {

    private static final int DIGITS = 6;
    private static final long TIME_STEP_SECONDS = 30;
    /** Accept codes from the adjacent windows to tolerate clock skew. */
    private static final int WINDOW = 1;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpService() {}

    /** Generate a fresh 160-bit secret, Base32-encoded (no padding) for QR/manual entry. */
    public static String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** Verify a user-entered 6-digit code against the secret, tolerating ±1 time step. */
    public static boolean verify(String secret, String code) {
        if (secret == null || code == null) return false;
        String trimmed = code.trim().replaceAll("\\s", "");
        if (!trimmed.matches("\\d{" + DIGITS + "}")) return false;
        byte[] key;
        try {
            key = base32Decode(secret);
        } catch (IllegalArgumentException e) {
            return false;
        }
        long currentWindow = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            if (constantTimeEquals(trimmed, generateCode(key, currentWindow + offset))) {
                return true;
            }
        }
        return false;
    }

    /** Build the otpauth:// URI that authenticator apps consume from a QR code. */
    public static String otpAuthUri(String issuer, String account, String secret) {
        String encIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String encAccount = URLEncoder.encode(account, StandardCharsets.UTF_8);
        return "otpauth://totp/" + encIssuer + ":" + encAccount
                + "?secret=" + secret
                + "&issuer=" + encIssuer
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + TIME_STEP_SECONDS;
    }

    /** Generate a batch of human-friendly single-use recovery codes (e.g. "a1b2-c3d4"). */
    public static List<String> generateRecoveryCodes(int count) {
        List<String> codes = new ArrayList<>(count);
        String alphabet = "abcdefghijkmnpqrstuvwxyz23456789"; // no ambiguous chars
        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder(9);
            for (int c = 0; c < 8; c++) {
                if (c == 4) sb.append('-');
                sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    private static String generateCode(byte[] key, long window) {
        byte[] data = new byte[8];
        long value = window;
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }

    // ── Base32 (RFC 4648, no padding) ──────────────────────────────────────────────

    static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                sb.append(BASE32_ALPHABET.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    static byte[] base32Decode(String input) {
        String cleaned = input.trim().replaceAll("[=\\s]", "").toUpperCase();
        if (cleaned.isEmpty()) throw new IllegalArgumentException("empty secret");
        int buffer = 0, bitsLeft = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char c : cleaned.toCharArray()) {
            int val = BASE32_ALPHABET.indexOf(c);
            if (val < 0) throw new IllegalArgumentException("invalid base32 char: " + c);
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out.write((buffer >> bitsLeft) & 0xFF);
            }
        }
        return out.toByteArray();
    }
}
