package com.example.notificationdemo.filter;

/** An immutable snapshot; callers must reject asynchronous results after revision changes. */
public final class ModelConfig {
    public enum Mode { KEYWORDS, OFFICIAL, RELAY, COMPARE, BOCHA }
    public enum Protocol { JEV_SYSTEMONE, CHAT_COMPLETIONS }

    public static final class Profile {
        public final String label;
        public final String baseUrl;
        public final String model;
        public final String apiKey;
        public final Protocol protocol;

        /** Legacy constructor: existing integrations and stored profiles remain Chat compatible. */
        public Profile(String label, String baseUrl, String model, String apiKey) {
            this(label, baseUrl, model, apiKey, Protocol.CHAT_COMPLETIONS);
        }

        public Profile(String label, String baseUrl, String model, String apiKey, Protocol protocol) {
            this.label = safe(label).trim();
            this.baseUrl = safe(baseUrl).trim();
            this.model = safe(model).trim();
            this.apiKey = safe(apiKey).trim();
            this.protocol = protocol == null ? Protocol.JEV_SYSTEMONE : protocol;
        }
        // Deliberately no toString(): the profile contains a credential.
    }

    public final Mode mode;
    public final boolean remoteEnabled;
    public final Profile official;
    public final Profile relay;
    public final Profile bocha;
    public final double threshold;
    public final boolean compareOfficial;
    public final boolean compareRelay;
    public final boolean compareBocha;
    public final long revision;
    public final String storageError;

    public ModelConfig(Mode mode, boolean remoteEnabled, Profile official, Profile relay,
                       long revision, String storageError) {
        this(mode, remoteEnabled, official, relay, presetBocha(), 0.5, true, true, false,
                revision, storageError);
    }

    public ModelConfig(Mode mode, boolean remoteEnabled, Profile official, Profile relay,
                       Profile bocha, double threshold, boolean compareOfficial,
                       boolean compareRelay, boolean compareBocha, long revision, String storageError) {
        this.mode = mode == null ? Mode.KEYWORDS : mode;
        this.remoteEnabled = remoteEnabled;
        this.official = official == null ? emptyOfficial() : official;
        this.relay = relay == null ? emptyRelay() : relay;
        this.bocha = bocha == null ? presetBocha() : bocha;
        this.threshold = threshold;
        this.compareOfficial = compareOfficial;
        this.compareRelay = compareRelay;
        this.compareBocha = compareBocha;
        this.revision = revision;
        this.storageError = safe(storageError);
    }

    public static Profile emptyOfficial() { return presetTypeSafe(); }
    public static Profile presetTypeSafe() {
        return new Profile("TypeSafe 官方", "https://api.typesafe.ai", "jev-latest", "", Protocol.JEV_SYSTEMONE);
    }
    public static Profile presetBocha() {
        return new Profile("博查 Jev", "https://jev.bocha.cn", "bocha-jev-v1", "", Protocol.JEV_SYSTEMONE);
    }
    public static Profile emptyRelay() { return new Profile("自建 / 中转接口", "", "", "", Protocol.JEV_SYSTEMONE); }
    private static String safe(String value) { return value == null ? "" : value; }
}
