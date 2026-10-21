package org.nongchanpin.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Passwords {
    private static final int ITERATIONS = 600000;
    private Passwords() {}
    public static String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }
    public static boolean matches(String password, String encoded) {
        try {
            String[] parts = encoded.split("\\$");
            int iterations = Integer.parseInt(parts[1]);
            if (parts.length != 4 || !"pbkdf2".equals(parts[0]) || iterations != ITERATIONS) return false;
            return MessageDigest.isEqual(derive(password, Base64.getDecoder().decode(parts[2]), iterations),
                    Base64.getDecoder().decode(parts[3]));
        } catch (RuntimeException exception) { return false; }
    }
    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (java.security.GeneralSecurityException exception) { throw new IllegalStateException("Password hashing unavailable"); }
        finally { spec.clearPassword(); }
    }
}
