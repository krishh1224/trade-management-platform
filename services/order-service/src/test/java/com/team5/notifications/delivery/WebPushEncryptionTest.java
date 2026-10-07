package com.team5.notifications.delivery;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// Proves the web-push library + bcprov-jdk18on produce a message a browser can decrypt. The
// decryption below follows RFC 8291 (aes128gcm) with the JDK only, independently of the library.
// Nothing is sent: preparePost builds the HTTP request without contacting a push service.
class WebPushEncryptionTest {

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    @BeforeAll
    static void bouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    void browserCanDecryptWhatWeSend_andTheRequestIsSignedWithVapid() throws Exception {
        KeyPair vapid = p256KeyPair();
        KeyPair browser = p256KeyPair();
        byte[] authSecret = new byte[16];
        new SecureRandom().nextBytes(authSecret);
        byte[] payload = "{\"title\":\"Artha: Order filled\",\"body\":\"Your BUY order for 1 INFY.NS was filled.\"}"
            .getBytes(StandardCharsets.UTF_8);

        PushService pushService = new PushService(
            B64URL.encodeToString(uncompressed((ECPublicKey) vapid.getPublic())),
            B64URL.encodeToString(fixed32(((ECPrivateKey) vapid.getPrivate()).getS())),
            "mailto:notifications@artha.example");
        Notification message = new Notification(
            "https://fcm.googleapis.com/fcm/send/test-subscription",
            B64URL.encodeToString(uncompressed((ECPublicKey) browser.getPublic())),
            B64URL.encodeToString(authSecret),
            payload,
            60);

        HttpPost request = pushService.preparePost(message, Encoding.AES128GCM);

        assertEquals("aes128gcm", request.getFirstHeader("Content-Encoding").getValue());
        assertEquals("60", request.getFirstHeader("TTL").getValue());
        String authorization = request.getFirstHeader("Authorization").getValue();
        assertTrue(authorization.startsWith("vapid t="), authorization);
        assertTrue(authorization.contains(", k=" + B64URL.encodeToString(uncompressed((ECPublicKey) vapid.getPublic()))));

        byte[] decrypted = decryptAsBrowser(EntityUtils.toByteArray(request.getEntity()), browser, authSecret);
        assertArrayEquals(payload, decrypted);
    }

    // RFC 8291 section 3.4 / RFC 8188, as the receiving browser does it
    private static byte[] decryptAsBrowser(byte[] body, KeyPair browser, byte[] authSecret) throws Exception {
        ByteBuffer header = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        header.get(salt);
        header.getInt(); // record size
        byte[] serverPublic = new byte[header.get() & 0xff];
        header.get(serverPublic);
        byte[] ciphertext = Arrays.copyOfRange(body, header.position(), body.length);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.getPrivate());
        agreement.doPhase(publicKeyFrom(serverPublic), true);
        byte[] ecdhSecret = agreement.generateSecret();

        byte[] browserPublic = uncompressed((ECPublicKey) browser.getPublic());
        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] ikm = hmac(prkKey, concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), browserPublic, serverPublic, new byte[] {1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] record = cipher.doFinal(ciphertext);

        // strip padding: the last non-zero byte is the delimiter (0x02 for the final record)
        int end = record.length - 1;
        while (end >= 0 && record[end] == 0) {
            end--;
        }
        assertEquals(2, record[end], "final-record delimiter");
        return Arrays.copyOf(record, end);
    }

    private static KeyPair p256KeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static PublicKey publicKeyFrom(byte[] uncompressedPoint) throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
        ECPoint point = new ECPoint(
            new BigInteger(1, Arrays.copyOfRange(uncompressedPoint, 1, 33)),
            new BigInteger(1, Arrays.copyOfRange(uncompressedPoint, 33, 65)));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, spec));
    }

    private static byte[] uncompressed(ECPublicKey key) {
        return concat(new byte[] {4}, fixed32(key.getW().getAffineX()), fixed32(key.getW().getAffineY()));
    }

    private static byte[] fixed32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - copy, out, 32 - copy, copy);
        return out;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
