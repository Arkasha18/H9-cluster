package net.adminrunet.h9cluster;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stable support code derived from VIN without persisting or uploading VIN. */
public final class MachineIdentity {
    public static final String SOURCE_VIN = "vin_hash";
    public static final String SOURCE_INSTALLATION = "installation";

    private static final String PREFS = "support_machine_identity";
    private static final String KEY_CODE = "machine_code";
    private static final String KEY_SOURCE = "machine_code_source";
    private static final String KEY_INSTALLATION_SEED = "installation_seed";
    private static final String HASH_SCOPE_VIN = "h9-cluster-vin-id-v1\u0000";
    private static final String HASH_SCOPE_INSTALLATION =
            "h9-cluster-installation-id-v1\u0000";
    private static final String BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final Pattern VIN_PATTERN =
            Pattern.compile("(?<![A-Z0-9])[A-HJ-NPR-Z0-9]{17}(?![A-Z0-9])");
    private static final Pattern CODE_PATTERN =
            Pattern.compile("H9-[0-9A-HJKMNP-TV-Z]{5}-[0-9A-HJKMNP-TV-Z]{5}");
    private static Identity processIdentity;

    private MachineIdentity() {
    }

    public static synchronized Identity current(Context context) {
        if (processIdentity != null && processIdentity.isVinBased()) {
            return processIdentity;
        }
        try {
            SharedPreferences preferences = preferences(context);
            String code = preferences.getString(KEY_CODE, "");
            String source = preferences.getString(KEY_SOURCE, "");
            if (isValidCode(code) && isValidSource(source)) {
                processIdentity = new Identity(code, source);
                return processIdentity;
            }
            processIdentity = createInstallationIdentity(preferences);
            return processIdentity;
        } catch (RuntimeException error) {
            DiagnosticLog.warn(
                    "identity",
                    "identity_storage_unavailable",
                    "error", error.getClass().getSimpleName());
            if (processIdentity == null) {
                byte[] seed = new byte[16];
                new SecureRandom().nextBytes(seed);
                processIdentity = new Identity(
                        deriveCode(
                                HASH_SCOPE_INSTALLATION.getBytes(StandardCharsets.UTF_8),
                                seed),
                        SOURCE_INSTALLATION);
            }
            return processIdentity;
        }
    }

    public static synchronized Identity acceptVin(Context context, String rawValue) {
        String code = deriveVinCode(rawValue);
        if (code == null) {
            return current(context);
        }
        Identity identity = new Identity(code, SOURCE_VIN);
        processIdentity = identity;
        try {
            boolean persisted = preferences(context).edit()
                    .putString(KEY_CODE, code)
                    .putString(KEY_SOURCE, SOURCE_VIN)
                    .remove(KEY_INSTALLATION_SEED)
                    .commit();
            if (!persisted) {
                DiagnosticLog.warn("identity", "vin_identity_persist_failed");
            }
        } catch (RuntimeException error) {
            DiagnosticLog.warn(
                    "identity",
                    "vin_identity_persist_failed",
                    "error", error.getClass().getSimpleName());
        }
        return identity;
    }

    static String extractVin(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.toUpperCase(Locale.US);
        Matcher matcher = VIN_PATTERN.matcher(normalized);
        return matcher.find() ? matcher.group() : null;
    }

    static String deriveVinCode(String rawValue) {
        String vin = extractVin(rawValue);
        if (vin == null) {
            return null;
        }
        return deriveCode(
                HASH_SCOPE_VIN.getBytes(StandardCharsets.UTF_8),
                vin.getBytes(StandardCharsets.US_ASCII));
    }

    static boolean isValidCode(String code) {
        return code != null && CODE_PATTERN.matcher(code).matches();
    }

    private static Identity createInstallationIdentity(SharedPreferences preferences) {
        byte[] seed = null;
        String encoded = preferences.getString(KEY_INSTALLATION_SEED, "");
        if (encoded != null && encoded.length() > 0) {
            try {
                seed = Base64.decode(encoded, Base64.NO_WRAP);
            } catch (IllegalArgumentException ignored) {
                seed = null;
            }
        }
        if (seed == null || seed.length != 16) {
            seed = new byte[16];
            new SecureRandom().nextBytes(seed);
            encoded = Base64.encodeToString(seed, Base64.NO_WRAP);
        }
        String code = deriveCode(
                HASH_SCOPE_INSTALLATION.getBytes(StandardCharsets.UTF_8),
                seed);
        boolean persisted = preferences.edit()
                .putString(KEY_INSTALLATION_SEED, encoded)
                .putString(KEY_CODE, code)
                .putString(KEY_SOURCE, SOURCE_INSTALLATION)
                .commit();
        if (!persisted) {
            DiagnosticLog.warn("identity", "installation_identity_persist_failed");
        }
        return new Identity(code, SOURCE_INSTALLATION);
    }

    private static String deriveCode(byte[] scope, byte[] value) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        digest.update(scope);
        byte[] hash = digest.digest(value);
        StringBuilder encoded = new StringBuilder(10);
        int accumulator = 0;
        int bits = 0;
        for (byte item : hash) {
            accumulator = (accumulator << 8) | (item & 0xff);
            bits += 8;
            while (bits >= 5 && encoded.length() < 10) {
                bits -= 5;
                encoded.append(BASE32.charAt((accumulator >> bits) & 31));
            }
            if (encoded.length() == 10) {
                break;
            }
        }
        return "H9-" + encoded.substring(0, 5) + "-" + encoded.substring(5, 10);
    }

    private static boolean isValidSource(String source) {
        return SOURCE_VIN.equals(source) || SOURCE_INSTALLATION.equals(source);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static final class Identity {
        public final String code;
        public final String source;

        Identity(String code, String source) {
            this.code = code;
            this.source = source;
        }

        public boolean isVinBased() {
            return SOURCE_VIN.equals(source);
        }
    }
}
