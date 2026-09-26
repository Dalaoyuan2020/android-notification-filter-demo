package com.example.notificationdemo.filter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Device-local model settings. Only API keys are encrypted; neither secrets nor errors are logged. */
public final class ModelStore {
    public static final String ACTION_CHANGED = "com.example.notificationdemo.filter.MODEL_CHANGED";
    public static final String PREFERENCES_NAME = "model_settings_v1";
    private static final String KEY_ALIAS = "notificationdemo.model.api_key.v1";
    private static final String STORAGE_ERROR = "无法解密已保存的密钥，模型发送已停用；请重新填写密钥并保存。";

    private ModelStore() {}

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized long getRevision(Context context) {
        try { return preferences(context).getLong("revision", 0); }
        catch (RuntimeException failure) { return -1; }
    }

    public static synchronized ModelConfig load(Context context) {
        ModelConfig.Mode mode = ModelConfig.Mode.OFFICIAL;
        try {
            SharedPreferences prefs = preferences(context);
            try { mode = ModelConfig.Mode.valueOf(prefs.getString("mode", "KEYWORDS")); }
            catch (IllegalArgumentException failure) {
                return new ModelConfig(ModelConfig.Mode.OFFICIAL, false, null, null,
                        prefs.getLong("revision", 0), "模型策略无法识别，已停用模型发送；请重新选择并保存。");
            }
            String error = "";
            String officialKey = "";
            String relayKey = "";
            try { officialKey = decrypt(prefs.getString("official_key_cipher", ""), "official"); }
            catch (GeneralSecurityException | IOException | RuntimeException failure) { error = STORAGE_ERROR; }
            try { relayKey = decrypt(prefs.getString("relay_key_cipher", ""), "relay"); }
            catch (GeneralSecurityException | IOException | RuntimeException failure) { error = STORAGE_ERROR; }
            ModelConfig.Profile official = new ModelConfig.Profile(prefs.getString("official_label", "官方接口"),
                    prefs.getString("official_url", ""), prefs.getString("official_model", ""), officialKey);
            ModelConfig.Profile relay = new ModelConfig.Profile(prefs.getString("relay_label", "中转接口"),
                    prefs.getString("relay_url", ""), prefs.getString("relay_model", ""), relayKey);
            return new ModelConfig(mode, error.isEmpty() && prefs.getBoolean("remote_enabled", false),
                    official, relay, prefs.getLong("revision", 0), error);
        } catch (RuntimeException failure) {
            return new ModelConfig(mode, false, null, null, -1,
                    "模型设置无法读取，已停用模型发送；请重新填写并保存。");
        }
    }

    /** A successful save also turns automatic removal off. Blank inactive profiles are allowed. */
    public static synchronized ModelConfig save(Context context, ModelConfig.Mode mode, boolean remoteEnabled,
                                                ModelConfig.Profile official, ModelConfig.Profile relay)
            throws IOException {
        if (mode == null) mode = ModelConfig.Mode.KEYWORDS;
        if (official == null) official = ModelConfig.emptyOfficial();
        if (relay == null) relay = ModelConfig.emptyRelay();
        validate(official, false);
        validate(relay, false);
        if (remoteEnabled && mode != ModelConfig.Mode.KEYWORDS) {
            if (mode == ModelConfig.Mode.OFFICIAL || mode == ModelConfig.Mode.COMPARE) validate(official, true);
            if (mode == ModelConfig.Mode.RELAY || mode == ModelConfig.Mode.COMPARE) validate(relay, true);
        }
        String officialEncrypted;
        String relayEncrypted;
        try {
            officialEncrypted = encrypt(official.apiKey, "official");
            relayEncrypted = encrypt(relay.apiKey, "relay");
        } catch (GeneralSecurityException | IOException | RuntimeException failure) {
            throw new IOException("密钥加密失败，设置未保存；请检查设备安全存储。");
        }
        SharedPreferences prefs = preferences(context);
        long revision = Math.max(getRevision(context) + 1, System.currentTimeMillis());
        boolean enabled = remoteEnabled && mode != ModelConfig.Mode.KEYWORDS;
        // Turn removal off before publishing a new model configuration to listeners.
        DemoStore.setAuto(context, false);
        boolean saved = prefs.edit().putString("mode", mode.name()).putBoolean("remote_enabled", enabled)
                .putLong("revision", revision)
                .putString("official_label", official.label).putString("official_url", official.baseUrl)
                .putString("official_model", official.model).putString("official_key_cipher", officialEncrypted)
                .putString("relay_label", relay.label).putString("relay_url", relay.baseUrl)
                .putString("relay_model", relay.model).putString("relay_key_cipher", relayEncrypted).commit();
        context.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(context.getPackageName()));
        if (!saved) throw new IOException("模型设置写入失败；自动清除已关闭，请重试保存。");
        return new ModelConfig(mode, enabled, official, relay, revision, "");
    }

    private static void validate(ModelConfig.Profile profile, boolean required) throws IOException {
        String error = ModelClient.validateProfile(profile, required);
        if (!error.isEmpty()) throw new IOException(error);
    }

    private static SecretKey key(boolean create) throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return (SecretKey) store.getKey(KEY_ALIAS, null);
        if (!create) throw new GeneralSecurityException("Missing device key");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

    private static byte[] aad(String slot) {
        return ("notificationdemo/model-key/" + slot + "/v1").getBytes(StandardCharsets.UTF_8);
    }

    private static String encrypt(String secret, String slot) throws GeneralSecurityException, IOException {
        if (secret.isEmpty()) return "";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(true));
        cipher.updateAAD(aad(slot));
        byte[] encoded = secret.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] encrypted = cipher.doFinal(encoded);
            return "v1:" + Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } finally { java.util.Arrays.fill(encoded, (byte) 0); }
    }

    private static String decrypt(String value, String slot) throws GeneralSecurityException, IOException {
        if (value == null || value.isEmpty()) return "";
        if (value.length() > 15000) throw new GeneralSecurityException("Invalid encrypted length");
        String[] parts = value.split(":", -1);
        if (parts.length != 3 || !"v1".equals(parts[0])) throw new GeneralSecurityException("Invalid format");
        byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[2], Base64.NO_WRAP);
        if (iv.length != 12 || encrypted.length < 16) throw new GeneralSecurityException("Invalid encrypted data");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad(slot));
        byte[] decrypted = cipher.doFinal(encrypted);
        try { return new String(decrypted, StandardCharsets.UTF_8); }
        finally { java.util.Arrays.fill(decrypted, (byte) 0); }
    }
}
