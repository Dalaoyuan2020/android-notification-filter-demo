import com.example.notificationdemo.filter.ModelConfig;
import com.example.notificationdemo.filter.StrictJson;
import com.example.notificationdemo.filter.SystemOneProtocol;

import java.util.Arrays;
import java.util.Map;

/** P0-only configuration regression tests, independent of Android and external providers. */
public final class p0_api_config_test {
    private static int checks;

    public static void main(String[] args) {
        ModelConfig.Profile defaults = ModelConfig.presetTeam1052();
        check("team preset has recommended name", defaults.label.contains("1052") && defaults.label.contains("推荐"));
        check("team preset is Jev", defaults.protocol == ModelConfig.Protocol.JEV_SYSTEMONE);
        check("team preset uses documented base", defaults.baseUrl.equals("https://10521052.xyz/jev"));
        check("team preset defaults to final finetune", defaults.model.equals("local-systemone-ft"));
        check("no bundled team key", defaults.apiKey.isEmpty());
        check("four-argument profiles now default to Jev", new ModelConfig.Profile("custom", defaults.baseUrl, "m", "").protocol == ModelConfig.Protocol.JEV_SYSTEMONE);
        check("null protocol defaults to Jev", new ModelConfig.Profile("custom", defaults.baseUrl, "m", "", null).protocol == ModelConfig.Protocol.JEV_SYSTEMONE);
        check("1052 endpoint appends native path exactly", SystemOneProtocol.endpoint(defaults.baseUrl).equals("https://10521052.xyz/jev/v1/systemone"));
        check("1052 complete endpoint is stable", SystemOneProtocol.endpoint("https://10521052.xyz/jev/v1/systemone").equals("https://10521052.xyz/jev/v1/systemone"));
        check("1052 trailing slash is normalized", SystemOneProtocol.endpoint(defaults.baseUrl + "/").equals("https://10521052.xyz/jev/v1/systemone"));
        check("team identity recognizes preset", ModelConfig.isTeam1052(defaults));
        check("lookalike domain never uses team identity", !ModelConfig.isTeam1052(new ModelConfig.Profile("lookalike", "https://10521052.xyz.evil.invalid/jev", "m", "")));

        String[] expected = {"local-systemone-ft", "local-systemone-v1", "typesafe-jev", "bocha-jev"};
        check("dropdown models and order match P0", Arrays.equals(expected, ModelConfig.team1052Models()));
        check("first comparison slot is 1052 finetune", ModelConfig.defaultOfficial().model.equals(expected[0])
                && ModelConfig.isTeam1052(ModelConfig.defaultOfficial()));
        check("second comparison slot is 1052 base", ModelConfig.defaultBocha().model.equals(expected[1])
                && ModelConfig.isTeam1052(ModelConfig.defaultBocha()));
        check("third comparison slot is 1052 TypeSafe", ModelConfig.defaultRelay().model.equals(expected[2])
                && ModelConfig.isTeam1052(ModelConfig.defaultRelay()));
        String[] changed = ModelConfig.team1052Models();
        changed[0] = "synthetic-mutation";
        check("dropdown caller cannot mutate shared defaults", Arrays.equals(expected, ModelConfig.team1052Models()));
        for (String model : expected) {
            ModelConfig.Profile profile = ModelConfig.presetTeam1052(model);
            check(model + " preset retains selected value", profile.model.equals(model));
            check(model + " has same endpoint and Jev protocol", defaults.baseUrl.equals(profile.baseUrl) && profile.protocol == ModelConfig.Protocol.JEV_SYSTEMONE);
            Map<String, Object> body = StrictJson.object(SystemOneProtocol.requestBody(profile.model, "合成应用", "连接测试", "只用于本机模拟测试", ""));
            check(model + " selection is serialized into actual request body", model.equals(body.get("model")));
            check(model + " native request contains state/questions, no messages", body.containsKey("state") && body.containsKey("questions") && !body.containsKey("messages"));
        }
        ModelConfig.Profile legacy = new ModelConfig.Profile("legacy", "https://legacy.invalid/v1", "legacy-model", "synthetic-only", ModelConfig.Protocol.CHAT_COMPLETIONS);
        ModelConfig.Profile migrated = ModelConfig.migrateToJev(legacy);
        check("old Chat configuration migrates to native Jev", migrated.protocol == ModelConfig.Protocol.JEV_SYSTEMONE);
        check("migration preserves endpoint for explicit user review", migrated.baseUrl.equals(legacy.baseUrl));
        check("migration preserves selected model", migrated.model.equals(legacy.model));
        check("migration preserves user key and label", migrated.apiKey.equals(legacy.apiKey) && migrated.label.equals(legacy.label));
        check("migration is idempotent", ModelConfig.migrateToJev(migrated).protocol == ModelConfig.Protocol.JEV_SYSTEMONE
                && ModelConfig.migrateToJev(migrated).baseUrl.equals(migrated.baseUrl));
        check("explicit Chat remains constructible for parser compatibility tests", legacy.protocol == ModelConfig.Protocol.CHAT_COMPLETIONS);
        ModelConfig review = new ModelConfig(ModelConfig.Mode.OFFICIAL, true, migrated, null, null,
                0.5, true, true, true, 7, "", "请检查旧配置");
        check("migration review notice prevents remote processing even when requested", review.needsReview
                && !review.remoteEnabled && !review.migrationNotice.isEmpty());
        ModelConfig.Profile bocha = ModelConfig.presetBocha();
        check("Bocha uses team-provided gateway", bocha.baseUrl.equals("https://tokendance.space/gateway/typesafe"));
        check("Bocha model and Jev protocol retained", bocha.model.equals("bocha-jev-v1") && bocha.protocol == ModelConfig.Protocol.JEV_SYSTEMONE);
        check("Bocha endpoint retains gateway prefix", SystemOneProtocol.endpoint(bocha.baseUrl).equals("https://tokendance.space/gateway/typesafe/v1/systemone"));
        ModelConfig.Profile official = ModelConfig.presetTypeSafe();
        check("direct official preset retained", official.baseUrl.equals("https://api.typesafe.ai") && official.model.equals("jev-latest"));
        check("direct presets never bundle credentials", official.apiKey.isEmpty() && bocha.apiKey.isEmpty());
        System.out.println("PASS: " + checks + " P0 API configuration checks");
    }

    private static void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
        checks++;
    }
}
