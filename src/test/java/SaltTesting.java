import java.lang.reflect.Field;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hash passwords for storage, and test passwords against password tokens.
 *
 * Instances of this class can be used concurrently by multiple threads.
 *
 * @author erickson
 * @see <a href="http://stackoverflow.com/a/2861125/3474">StackOverflow</a>
 */
public final class SaltTesting
{
    private void clearString(String clear) throws NoSuchFieldException, IllegalAccessException {
        Field field = String.class.getDeclaredField("value");
        field.setAccessible(true);
        char[] value = (char[]) field.get(clear);
        for(int i = 0; i < value.length; i++){
            value[i] = 0;
        }

        System.out.println(Arrays.toString(value));
        System.out.println(clear);
    }

    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
        SaltTesting salt = new SaltTesting();
        salt.clearString("Clear me");
        Scanner input = new Scanner(System.in);

        String plainPass = input.nextLine();

        char[] password = plainPass.toCharArray();
        char[] notPassword = "DerpDude".toCharArray();

        String hash = salt.hash(password);

        System.out.println("Password: '" + plainPass + "`");

        System.out.println("Hash: " + hash);

        System.out.println(salt.authenticate(password, hash));

        System.out.println(salt.authenticate(notPassword, hash));

        for(int i = 0; i < password.length; i++){
            password[i] = 0;
        }

        for(int i = 0; i < notPassword.length; i++){
            notPassword[i] = 0;
        }
    }

    /**
     * Each token produced by this class uses this identifier as a prefix.
     */
    public static final String ID = "$31$";

    /**
     * The minimum recommended cost, used by default
     */
    public static final int DEFAULT_COST = 16;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA1";

    private static final int SIZE = 128;

    private static final Pattern layout = Pattern.compile("\\$31\\$(\\d\\d?)\\$(.{43})");

    private final SecureRandom random;

    private final int cost;

    public SaltTesting()
    {
        this(DEFAULT_COST);
    }

    /**
     * Create a password manager with a specified cost
     *
     * @param cost the exponential computational cost of hashing a password, 0 to 30
     */
    public SaltTesting(int cost)
    {
        iterations(cost); /* Validate cost */
        this.cost = cost;
        this.random = new SecureRandom();
    }

    private static int iterations(int cost)
    {
        if ((cost < 0) || (cost > 30))
            throw new IllegalArgumentException("cost: " + cost);
        return 1 << cost;
    }

    /**
     * Hash a password for storage.
     *
     * @return a secure authentication token to be stored for later authentication
     */
    public String hash(char[] password)
    {
        byte[] salt = new byte[SIZE / 8];
        random.nextBytes(salt);
        byte[] dk = pbkdf2(password, salt, 1 << cost);
        byte[] hash = new byte[salt.length + dk.length];
        System.arraycopy(salt, 0, hash, 0, salt.length);
        System.arraycopy(dk, 0, hash, salt.length, dk.length);
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return ID + cost + '$' + enc.encodeToString(hash);
    }

    /**
     * Authenticate with a password and a stored password token.
     *
     * @return true if the password and token match
     */
    public boolean authenticate(char[] password, String token)
    {
        Matcher m = layout.matcher(token);
        if (!m.matches())
            throw new IllegalArgumentException("Invalid token format");
        int iterations = iterations(Integer.parseInt(m.group(1)));
        byte[] hash = Base64.getUrlDecoder().decode(m.group(2));
        byte[] salt = Arrays.copyOfRange(hash, 0, SIZE / 8);
        byte[] check = pbkdf2(password, salt, iterations);
        int zero = 0;
        for (int idx = 0; idx < check.length; ++idx)
            zero |= hash[salt.length + idx] ^ check[idx];
        return zero == 0;
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations)
    {
        KeySpec spec = new PBEKeySpec(password, salt, iterations, SIZE);
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance(ALGORITHM);
            return f.generateSecret(spec).getEncoded();
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Missing algorithm: " + ALGORITHM, ex);
        }
        catch (InvalidKeySpecException ex) {
            throw new IllegalStateException("Invalid SecretKeyFactory", ex);
        }
    }

    /**
     * Hash a password in an immutable {@code String}.
     *
     * <p>Passwords should be stored in a {@code char[]} so that it can be filled
     * with zeros after use instead of lingering on the heap and elsewhere.
     *
     * @deprecated Use {@link #hash(char[])} instead
     */
    @Deprecated
    public String hash(String password)
    {
        return hash(password.toCharArray());
    }

    /**
     * Authenticate with a password in an immutable {@code String} and a stored
     * password token.
     *
     * @deprecated Use {@link #authenticate(char[],String)} instead.
     * @see #hash(String)
     */
    @Deprecated
    public boolean authenticate(String password, String token)
    {
        return authenticate(password.toCharArray(), token);
    }

}