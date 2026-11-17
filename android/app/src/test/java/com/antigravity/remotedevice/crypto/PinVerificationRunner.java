import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;

public class PinVerificationRunner {

    public static void main(String[] args) {
        try {
            System.out.println("--- STARTING PIN CRYPTOGRAPHY VERIFICATION ---");
            SecureRandom random = new SecureRandom();
            int number = 100000 + random.nextInt(900000);
            String rawPin = String.valueOf(number);
            String formattedPin = rawPin.substring(0, 3) + "-" + rawPin.substring(3);

            System.out.println("[TEST 1] Generated PIN: " + formattedPin + " (Length: " + rawPin.length() + ")");
            assert rawPin.length() == 6;

            byte[] salt = new byte[16];
            random.nextBytes(salt);

            // Compute PBKDF2 hash
            PBEKeySpec spec = new PBEKeySpec(rawPin.toCharArray(), salt, 10000, 256);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            String hashHex = bytesToHex(hash);
            System.out.println("[TEST 2] Computed PBKDF2-HMAC-SHA256 Hash: " + hashHex.substring(0, 32) + "...");

            // Verify with correct PIN
            PBEKeySpec verifySpec = new PBEKeySpec(rawPin.toCharArray(), salt, 10000, 256);
            byte[] verifyHash = factory.generateSecret(verifySpec).getEncoded();
            boolean match = bytesToHex(verifyHash).equals(hashHex);
            System.out.println("[TEST 3] Positive Verification Match: " + match);
            if (!match) throw new RuntimeException("Assertion failed: Valid PIN did not match!");

            // Verify with wrong PIN
            String wrongPin = rawPin.equals("123456") ? "654321" : "123456";
            PBEKeySpec wrongSpec = new PBEKeySpec(wrongPin.toCharArray(), salt, 10000, 256);
            byte[] wrongHash = factory.generateSecret(wrongSpec).getEncoded();
            boolean wrongMatch = bytesToHex(wrongHash).equals(hashHex);
            System.out.println("[TEST 4] Negative Verification (Wrong PIN): " + (!wrongMatch ? "REJECTED (PASS)" : "FAIL"));
            if (wrongMatch) throw new RuntimeException("Assertion failed: Wrong PIN should not match!");

            System.out.println("--- ALL PIN CRYPTOGRAPHIC TESTS PASSED SUCCESSFULLY ---");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
