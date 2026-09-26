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
            LoadedProfile official = loadProfile(prefs, "official", ModelConfig.presetTypeSafe());
            LoadedProfile relay = loadProfile(prefs, "relay", ModelConfig.emptyRelay());
            LoadedProfile bocha = loadProfile(prefs, "bocha", ModelConfig.presetBocha());
            String error = official.error || relay.error || bocha.error ? STORAGE_ERROR : "";
            double threshold = 0.5;
            try {
                threshold = Double.parseDouble(prefs.getString("threshold", "0.5"));
                if (!SystemOneProtocol.validThreshold(threshold)) throw new IllegalArgumentException();
            } catch (RuntimeException failure) {
                threshold = 0.5;
                error = "保留阈值无效，模型发送已停用；请重新填写并保存。";
            }
            boolean legacy = hasProfile(prefs, "official") || hasProfile(prefs, "relay");
            return new ModelConfig(mode, error.isEmpty() && prefs.getBoolean("remote_enabled", false),
                    official.profile, relay.profile, bocha.profile, threshold,
                    prefs.getBoolean("compare_official", true), prefs.getBoolean("compare_relay", legacy),
                    prefs.getBoolean("compare_bocha", !legacy), prefs.getLong("revision", 0), error);
        } catch (RuntimeException failure) {
            return new ModelConfig(mode, false, null, null, -1,
                    "模型设置无法读取，已停用模型发送；请重新填写并保存。");
        }
    }

    /** A successful save also turns automatic removal off. Blank inactive profiles are allowed. */
    public static synchronized ModelConfig save(Context context, ModelConfig.Mode mode, boolean remoteEnabled,
                                                ModelConfig.Profile official, ModelConfig.Profile relay)
            throws IOException {
        ModelConfig current = load(context);
        return save(context, mode, remoteEnabled, official, relay, current.bocha,
                current.threshold, true, true, false);
    }

    public static synchronized ModelConfig save(Context context, ModelConfig.Mode mode, boolean remoteEnabled,
                                                ModelConfig.Profile official, ModelConfig.Profile relay,
                                                ModelConfig.Profile bocha, double threshold,
                                                boolean compareOfficial, boolean compareRelay, boolean compareBocha)
            throws IOException {
        if (mode == null) mode = ModelConfig.Mode.KEYWORDS;
        if (official == null) official = ModelConfig.emptyOfficial();
        if (relay == null) relay = ModelConfig.emptyRelay();
        if (bocha == null) bocha = ModelConfig.presetBocha();
        if (!SystemOneProtocol.validThreshold(threshold)) throw new IOException("保留阈值必须为0到1之间的有限数字。");
        validate(official, false);
        validate(relay, false);
        validate(bocha, false);
        if (remoteEnabled && mode != ModelConfig.Mode.KEYWORDS) {
            if (mode == ModelConfig.Mode.COMPARE && !compareOfficial && !compareRelay && !compareBocha) {
                throw new IOException("对照模式至少选择一路接口。");
            }
            if (mode == ModelConfig.Mode.OFFICIAL || (mode == ModelConfig.Mode.COMPARE && compareOfficial)) validate(official, true);
            if (mode == ModelConfig.Mode.RELAY || (mode == ModelConfig.Mode.COMPARE && compareRelay)) validate(relay, true);
            if (mode == ModelConfig.Mode.BOCHA || (mode == ModelConfig.Mode.COMPARE && compareBocha)) validate(bocha, true);
        }
        String officialEncrypted;
        String relayEncrypted;
        String bochaEncrypted;
        try {
            officialEncrypted = encrypt(official.apiKey, "official");
            relayEncrypted = encrypt(relay.apiKey, "relay");
            bochaEncrypted = encrypt(bocha.apiKey, "bocha");
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
                .putString("threshold", Double.toString(threshold))
                .putBoolean("compare_official", compareOfficial).putBoolean("compare_relay", compareRelay)
                .putBoolean("compare_bocha", compareBocha)
                .putString("official_label", official.label).putString("official_url", official.baseUrl)
                .putString("official_model", official.model).putString("official_key_cipher", officialEncrypted)
                .putString("official_protocol", official.protocol.name())
                .putString("relay_label", relay.label).putString("relay_url", relay.baseUrl)
                .putString("relay_model", relay.model).putString("relay_key_cipher", relayEncrypted)
                .putString("relay_protocol", relay.protocol.name())
                .putString("bocha_label", bocha.label).putString("bocha_url", bocha.baseUrl)
                .putString("bocha_model", bocha.model).putString("bocha_key_cipher", bochaEncrypted)
                .putString("bocha_protocol", bocha.protocol.name()).commit();
        context.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(context.getPackageName()));
        if (!saved) throw new IOException("模型设置写入失败；自动清除已关闭，请重试保存。");
        return new ModelConfig(mode, enabled, official, relay, bocha, threshold,
                compareOfficial, compareRelay, compareBocha, revision, "");
    }

    private static boolean hasProfile(SharedPreferences prefs, String slot) {
        return prefs.contains(slot + "_url") || prefs.contains(slot + "_model")
                || prefs.contains(slot + "_label") || prefs.contains(slot + "_key_cipher");
    }

    private static LoadedProfile loadProfile(SharedPreferences prefs, String slot, ModelConfig.Profile preset) {
        boolean error = false;
        String secret = "";
        try { secret = decrypt(prefs.getString(slot + "_key_cipher", ""), slot); }
        catch (GeneralSecurityException | IOException | RuntimeException failure) { error = true; }
        boolean existing = hasProfile(prefs, slot);
        ModelConfig.Protocol protocol = existing ? ModelConfig.Protocol.CHAT_COMPLETIONS : preset.protocol;
        try { protocol = ModelConfig.Protocol.valueOf(prefs.getString(slot + "_protocol", protocol.name())); }
        catch (RuntimeException failure) { error = true; }
        ModelConfig.Profile profile = new ModelConfig.Profile(prefs.getString(slot + "_label", preset.label),
                prefs.getString(slot + "_url", existing ? "" : preset.baseUrl),
                prefs.getString(slot + "_model", existing ? "" : preset.model), secret, protocol);
        return new LoadedProfile(profile, error);
    }

    private static final class LoadedProfile {
        final ModelConfig.Profile profile;
        final boolean error;
        LoadedProfile(ModelConfig.Profile profile, boolean error) { this.profile = profile; this.error = error; }
    }

    /** Same device key, separately bound authenticated slot; never includes raw crypto failures. */
    static synchronized String encryptSecret(String secret, String slot) throws IOException {
        try {
            String value = secret == null ? "" : secret;
            if (value.getBytes(StandardCharsets.UTF_8).length > 8192) throw new IOException("Credential too long");
            return encrypt(value, slot);
        }
        catch (GeneralSecurityException | IOException | RuntimeException failure) {
            throw new IOException("本机密钥加密失败，未保存凭据。");
        }
    }

    static synchronized String decryptSecret(String value, String slot) throws IOException {
        try { return decrypt(value, slot); }
        catch (GeneralSecurityException | IOException | RuntimeException failure) {
            throw new IOException("本机凭据无法解密，请重新填写。");
        }
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
