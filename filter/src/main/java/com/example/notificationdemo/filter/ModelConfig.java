package com.example.notificationdemo.filter;

/** An immutable snapshot; callers must reject asynchronous results after revision changes. */
public final class ModelConfig {
    public enum Mode { KEYWORDS, OFFICIAL, RELAY, COMPARE }

    public static final class Profile {
        public final String label;
        public final String baseUrl;
        public final String model;
        public final String apiKey;

        public Profile(String label, String baseUrl, String model, String apiKey) {
            this.label = safe(label).trim();
            this.baseUrl = safe(baseUrl).trim();
            this.model = safe(model).trim();
            this.apiKey = safe(apiKey).trim();
        }
        // Deliberately no toString(): the profile contains a credential.
    }

    public final Mode mode;
    public final boolean remoteEnabled;
    public final Profile official;
    public final Profile relay;
    public final long revision;
    public final String storageError;

    public ModelConfig(Mode mode, boolean remoteEnabled, Profile official, Profile relay,
                       long revision, String storageError) {
        this.mode = mode == null ? Mode.KEYWORDS : mode;
        this.remoteEnabled = remoteEnabled;
        this.official = official == null ? emptyOfficial() : official;
        this.relay = relay == null ? emptyRelay() : relay;
        this.revision = revision;
        this.storageError = safe(storageError);
    }

    public static Profile emptyOfficial() { return new Profile("官方接口", "", "", ""); }
    public static Profile emptyRelay() { return new Profile("中转接口", "", "", ""); }
    private static String safe(String value) { return value == null ? "" : value; }
}
