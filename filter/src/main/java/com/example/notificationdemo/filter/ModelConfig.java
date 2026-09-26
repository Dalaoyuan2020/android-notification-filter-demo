package com.example.notificationdemo.filter;

/** An immutable snapshot; callers must reject asynchronous results after revision changes. */
public final class ModelConfig {
    public enum Mode { KEYWORDS, OFFICIAL, RELAY, COMPARE, BOCHA }
    public enum Protocol { JEV_SYSTEMONE, CHAT_COMPLETIONS }
    public static final String TEAM_1052_BASE_URL = "https://10521052.xyz/jev";
    private static final String[] TEAM_1052_MODELS = {
            "local-systemone-ft", "local-systemone-v1", "typesafe-jev", "bocha-jev"
    };

    public static final class Profile {
        public final String label;
        public final String baseUrl;
        public final String model;
        public final String apiKey;
        public final Protocol protocol;

        /** New profiles always use Jev; the explicit protocol constructor retains low-level compatibility. */
        public Profile(String label, String baseUrl, String model, String apiKey) {
            this(label, baseUrl, model, apiKey, Protocol.JEV_SYSTEMONE);
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
    public final String migrationNotice;
    public final boolean needsReview;

    public ModelConfig(Mode mode, boolean remoteEnabled, Profile official, Profile relay,
                       long revision, String storageError) {
        this(mode, remoteEnabled, official, relay, presetBocha(), 0.5, true, true, false,
                revision, storageError);
    }

    public ModelConfig(Mode mode, boolean remoteEnabled, Profile official, Profile relay,
                       Profile bocha, double threshold, boolean compareOfficial,
                       boolean compareRelay, boolean compareBocha, long revision, String storageError) {
        this(mode, remoteEnabled, official, relay, bocha, threshold, compareOfficial, compareRelay,
                compareBocha, revision, storageError, "");
    }

    public ModelConfig(Mode mode, boolean remoteEnabled, Profile official, Profile relay,
                       Profile bocha, double threshold, boolean compareOfficial,
                       boolean compareRelay, boolean compareBocha, long revision, String storageError,
                       String migrationNotice) {
        this.mode = mode == null ? Mode.KEYWORDS : mode;
        this.migrationNotice = safe(migrationNotice);
        this.needsReview = !this.migrationNotice.isEmpty();
        this.remoteEnabled = remoteEnabled && !needsReview;
        this.official = official == null ? defaultOfficial() : official;
        this.relay = relay == null ? defaultRelay() : relay;
        this.bocha = bocha == null ? defaultBocha() : bocha;
        this.threshold = threshold;
        this.compareOfficial = compareOfficial;
        this.compareRelay = compareRelay;
        this.compareBocha = compareBocha;
        this.revision = revision;
        this.storageError = safe(storageError);
    }

    public static Profile emptyOfficial() { return defaultOfficial(); }
    /** Existing slot names remain stable; the visible order is official, bocha, relay. */
    public static Profile defaultOfficial() { return presetTeam1052("local-systemone-ft"); }
    public static Profile defaultBocha() { return presetTeam1052("local-systemone-v1"); }
    public static Profile defaultRelay() { return presetTeam1052("typesafe-jev"); }
    public static String[] team1052Models() { return TEAM_1052_MODELS.clone(); }
    public static Profile presetTeam1052() { return presetTeam1052(TEAM_1052_MODELS[0]); }
    public static Profile presetTeam1052(String model) {
        for (String allowed : TEAM_1052_MODELS) {
            if (allowed.equals(model)) {
                return new Profile("团队中转 1052（推荐）", TEAM_1052_BASE_URL, model, "", Protocol.JEV_SYSTEMONE);
            }
        }
        throw new IllegalArgumentException("Unsupported 1052 model");
    }
    /** Credential sharing is tied to the exact HTTPS host, never lookalike domains or arbitrary ports. */
    public static boolean isTeam1052(Profile profile) {
        if (profile == null) return false;
        try {
            java.net.URI uri = new java.net.URI(profile.baseUrl);
            return "https".equalsIgnoreCase(uri.getScheme()) && "10521052.xyz".equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getRawUserInfo() == null;
        } catch (java.net.URISyntaxException invalid) { return false; }
    }
    /** Migration changes only the protocol. Callers must obtain review before enabling remote processing. */
    public static Profile migrateToJev(Profile profile) {
        if (profile == null || profile.protocol == Protocol.JEV_SYSTEMONE) return profile;
        return new Profile(profile.label, profile.baseUrl, profile.model, profile.apiKey, Protocol.JEV_SYSTEMONE);
    }
    public static Profile presetTypeSafe() {
        return new Profile("TypeSafe 官方", "https://api.typesafe.ai", "jev-latest", "", Protocol.JEV_SYSTEMONE);
    }
    public static Profile presetBocha() {
        return new Profile("博查 Jev", "https://tokendance.space/gateway/typesafe", "bocha-jev-v1", "", Protocol.JEV_SYSTEMONE);
    }
    public static Profile emptyRelay() { return new Profile("自建 / 中转接口", "", "", "", Protocol.JEV_SYSTEMONE); }
    private static String safe(String value) { return value == null ? "" : value; }
}
