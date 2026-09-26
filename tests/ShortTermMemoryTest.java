import com.example.notificationdemo.filter.AttentionMath;
import com.example.notificationdemo.filter.ShortTermMemory;
import com.example.notificationdemo.filter.ShortTermMemory.Entry;

import java.util.List;

/** Plain Java mathematical/feedback/persistence checks. No Android or test library required. */
public final class ShortTermMemoryTest {
    private static int count;
    private static final String PKG = "com.example.notificationdemo.sender";
    private static final long NOW = 1_800_000;
    private static final double HALF_LIFE = 30;

    public static void main(String[] arguments) {
        mappings();
        priorAndDecay();
        repeatedDismissalAndFusion();
        prefixIsolation();
        windowsAndBounds();
        persistence();
        numericBoundaries();
        System.out.println("PASS: " + count + " short-term attention-memory checks");
    }

    private static void mappings() {
        near("quick click y", 1, AttentionMath.observationForRemoval(1, 0).y);
        near("60 seconds inclusive click y", 1, AttentionMath.observationForRemoval(1, 60_000).y);
        near("past 60 seconds slow click y", 0.7, AttentionMath.observationForRemoval(1, 60_001).y);
        near("swipe y", 0.05, AttentionMath.observationForRemoval(2, 0).y);
        near("ignored y", 0.2, AttentionMath.ignoredObservation().y);
        yes("ignore threshold is 30 minutes", AttentionMath.IGNORE_AFTER_MS == 1_800_000);
        yes("negative click timing gives no observation", AttentionMath.observationForRemoval(1, -1) == null);
        for (int reason : new int[]{0, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, AttentionMath.REASON_IGNORE}) {
            yes("removal reason " + reason + " does not train", AttentionMath.observationForRemoval(reason, 10_000) == null);
        }
    }

    private static void priorAndDecay() {
        ShortTermMemory memory = new ShortTermMemory();
        Entry prior = memory.get(PKG, "普通消息", "发送器", NOW, HALF_LIFE);
        near("prior alpha", 1, prior.alpha); near("prior beta", 1, prior.beta);
        near("prior p", 0.5, prior.pShort); near("prior n", 0, prior.n);
        yes("query does not create memory", memory.size() == 0);
        for (double p : new double[]{0, 0.001, 0.123456789, 0.5, 0.999, 1}) {
            exact("no observation is exact baseline " + p, p, AttentionMath.fuse(p, prior.alpha, prior.beta, 1));
        }
        memory.observe(PKG, "普通消息", "发送器", AttentionMath.observationForRemoval(1, 10), NOW, HALF_LIFE);
        Entry initial = memory.get(PKG, "", "", NOW, HALF_LIFE);
        near("click alpha update", 2, initial.alpha); near("click beta update", 1, initial.beta);
        Entry half = memory.get(PKG, "", "", NOW + 1_800_000, HALF_LIFE);
        near("whole alpha halves", 1, half.alpha); near("whole beta halves", 0.5, half.beta);
        near("decay preserves ratio", 2.0 / 3, half.pShort);
        near("raw n may be negative", -0.5, half.n);
        near("effective evidence never negative", 0, half.effectiveCount);
        exact("negative raw n does not reverse weight", 0.987654321, AttentionMath.fuse(0.987654321, half.alpha, half.beta, 1));
        memory.observe(PKG, "", "发送器", AttentionMath.observationForRemoval(2, 20), NOW + 1_800_000, HALF_LIFE);
        Entry updated = memory.get(PKG, "", "", NOW + 1_800_000, HALF_LIFE);
        near("decay occurs before adding y", 1.05, updated.alpha);
        near("decay occurs before adding 1-y", 1.45, updated.beta);
        near("posterior probability after decay/update", 1.05 / 2.5, updated.pShort);
        near("n after decay/update", 0.5, updated.n);
        yes("snapshots preserve actual observation time", updated.lastObservedAt == NOW + 1_800_000 && initial.lastObservedAt == NOW);
    }

    private static void repeatedDismissalAndFusion() {
        ShortTermMemory memory = new ShortTermMemory();
        for (int i = 0; i < 5; i++) memory.observe(PKG, "【淘宝】优惠", "发送器", AttentionMath.observationForRemoval(2, 1), NOW, HALF_LIFE);
        Entry entry = memory.get(PKG, "【淘宝】新优惠", "发送器", NOW, HALF_LIFE);
        near("five swipes alpha", 1.25, entry.alpha); near("five swipes beta", 5.75, entry.beta);
        near("five swipes p", 1.25 / 7, entry.pShort); near("five swipes n", 5, entry.n);
        double expected = 1 / (1 + Math.exp(-(Math.log(0.6 / 0.4) + (5.0 / 8) * Math.log(1.25 / 5.75))));
        near("fusion follows specified logit formula", expected, AttentionMath.fuse(0.6, entry.alpha, entry.beta, 1));
        yes("five swipes reduce original score", AttentionMath.fuse(0.6, entry.alpha, entry.beta, 1) < 0.6);
        exact("zero weight is exact baseline", 0.999, AttentionMath.fuse(0.999, entry.alpha, entry.beta, 0));
        near("window actual swipe count", 5, entry.dismissals);
        yes("text avoids fabricated consecutive claim", entry.behaviorText.contains("划掉 5 次") && !entry.behaviorText.contains("连续"));
        List<Entry> changes = memory.recentChanges(NOW, HALF_LIFE, 2);
        yes("equivalent aggregate/prefix are not counted twice", changes.size() == 1 && changes.get(0).prefix.equals("【淘宝】"));
    }

    private static void prefixIsolation() {
        ShortTermMemory memory = new ShortTermMemory();
        for (int i = 0; i < 5; i++) memory.observe(PKG, "【淘宝】优惠", "发送器", AttentionMath.observationForRemoval(2, 1), NOW, HALF_LIFE);
        Entry bank = memory.get(PKG, "【银行】到账", "发送器", NOW, HALF_LIFE);
        near("unseen prefix does not inherit package alpha", 1, bank.alpha);
        near("unseen prefix does not inherit package beta", 1, bank.beta);
        exact("unseen bank score remains exact baseline", 0.81234, AttentionMath.fuse(0.81234, bank.alpha, bank.beta, 1));
        yes("package aggregate also receives each feedback", memory.get(PKG, "无前缀", "发送器", NOW, HALF_LIFE).totalObservations == 5);
        memory.observe(PKG, "【银行】到账", "发送器", AttentionMath.observationForRemoval(1, 1), NOW, HALF_LIFE);
        near("observed bank gets its own positive posterior", 2.0 / 3, memory.get(PKG, "【银行】交易", "", NOW, HALF_LIFE).pShort);
        near("bank observation leaves Taobao posterior unchanged", 1.25 / 7, memory.get(PKG, "【淘宝】优惠", "", NOW, HALF_LIFE).pShort);
        near("same prefix in another package is isolated", 0.5, memory.get("other.pkg", "【淘宝】优惠", "其他", NOW, HALF_LIFE).pShort);
        yes("prefix only recognized at title start", ShortTermMemory.prefixForTitle("文字【银行】").isEmpty());
        yes("prefix trims external and internal whitespace", ShortTermMemory.prefixForTitle("  【 银行 】到账").equals("【银行】"));
        yes("empty prefix not recognized", ShortTermMemory.prefixForTitle("【 】消息").isEmpty());
        yes("unterminated prefix not recognized", ShortTermMemory.prefixForTitle("【银行消息").isEmpty());
        yes("snapshot list immutable", immutable(memory.snapshots(NOW, HALF_LIFE)));
    }

    private static void windowsAndBounds() {
        ShortTermMemory memory = new ShortTermMemory();
        memory.observe("app.one", "", "应用一", AttentionMath.observationForRemoval(1, 0), 0, HALF_LIFE);
        memory.observe("app.one", "", "应用一", AttentionMath.observationForRemoval(1, 60_001), NOW, HALF_LIFE);
        memory.observe("app.one", "", "应用一", AttentionMath.ignoredObservation(), NOW + 1, HALF_LIFE);
        Entry window = memory.get("app.one", "", "", NOW + 1, HALF_LIFE);
        yes("rolling window excludes feedback older than half-life", window.fastClicks == 0 && window.slowClicks == 1 && window.ignores == 1);
        Entry boundary = memory.get("app.one", "", "", NOW, HALF_LIFE);
        yes("window includes exact start, excludes future feedback", boundary.fastClicks == 1 && boundary.slowClicks == 1 && boundary.ignores == 0);
        Entry expired = memory.get("app.one", "", "", NOW * 3, HALF_LIFE);
        yes("no invented recent count after window expires", expired.windowCount() == 0 && expired.change == 0);
        ShortTermMemory bounded = new ShortTermMemory();
        for (int i = 0; i < ShortTermMemory.MAX_EVENTS_PER_ENTRY + 3; i++) bounded.observe("app", "", "应用", AttentionMath.observationForRemoval(2, 0), NOW, HALF_LIFE);
        Entry incomplete = bounded.get("app", "", "", NOW, HALF_LIFE);
        yes("event history bounded and honestly marked incomplete", incomplete.dismissals == ShortTermMemory.MAX_EVENTS_PER_ENTRY
                && !incomplete.windowComplete && incomplete.behaviorText.contains("记录不完整"));
        yes("incomplete evidence not claimed as largest full-window change", bounded.recentChanges(NOW, HALF_LIFE, 2).isEmpty());
        for (int i = 0; i < ShortTermMemory.MAX_ENTRIES + 5; i++) bounded.observe("app." + i, "", "应用", AttentionMath.observationForRemoval(2, 0), NOW + i, HALF_LIFE);
        yes("entry count bounded", bounded.size() == ShortTermMemory.MAX_ENTRIES);
        yes("oldest-observed entry evicted", bounded.get("app", "", "", NOW + 1000, HALF_LIFE).totalObservations == 0);
        yes("recent changes at most two even with huge limit", bounded.recentChanges(NOW + 1000, HALF_LIFE, 100).size() <= 2);
        yes("zero limit produces no recent summary", bounded.recentChanges(NOW + 1000, HALF_LIFE, 0).isEmpty());
    }

    private static void persistence() {
        ShortTermMemory memory = new ShortTermMemory();
        memory.observe(PKG, "【淘宝】优惠正文不得持久化", "发送器", AttentionMath.observationForRemoval(2, 1), NOW, HALF_LIFE);
        memory.observe(PKG, "【银行】到账正文不得持久化", "发送器", AttentionMath.observationForRemoval(1, 70_000), NOW + 500, HALF_LIFE);
        ShortTermMemory restored = ShortTermMemory.deserialize(memory.serialize());
        yes("serialization restores aggregate plus both prefixes", restored.size() == 3);
        Entry expected = memory.get(PKG, "【淘宝】新消息", "", NOW + 1000, HALF_LIFE);
        Entry actual = restored.get(PKG, "【淘宝】新消息", "", NOW + 1000, HALF_LIFE);
        exact("posterior alpha round-trips", expected.alpha, actual.alpha);
        exact("posterior beta round-trips", expected.beta, actual.beta);
        yes("window history and metadata round-trip", expected.behaviorText.equals(actual.behaviorText) && expected.lastObservedAt == actual.lastObservedAt);
        String decoded = new String(java.util.Base64.getDecoder().decode(memory.serialize().substring(5)), java.nio.charset.StandardCharsets.UTF_8);
        yes("notification body/title suffix never stored", !decoded.contains("正文不得持久化"));
        yes("corrupted serialized data resets memory", ShortTermMemory.deserialize("STM1:garbage!").size() == 0);
        yes("unknown format resets memory", ShortTermMemory.deserialize("STM2:" + memory.serialize()).size() == 0);
        yes("truncated state resets memory", ShortTermMemory.deserialize(memory.serialize().substring(0, 25)).size() == 0);
        memory.clear(); yes("explicit clear drops all memory", memory.size() == 0);
    }

    private static void numericBoundaries() {
        near("lower logit clamp", Math.log(0.01 / 0.99), AttentionMath.logit(0));
        near("upper logit clamp", Math.log(0.99 / 0.01), AttentionMath.logit(1));
        near("sigmoid stable at large negative", 0, AttentionMath.sigmoid(-1000));
        near("sigmoid stable at large positive", 1, AttentionMath.sigmoid(1000));
        near("empty underflow posterior probability", 0.5, AttentionMath.shortProbability(0, 0));
        exact("underflow posterior leaves original unchanged", 0.006, AttentionMath.fuse(0.006, 0, 0, 1));
        near("negative elapsed does not grow posterior", 1, AttentionMath.decayFactor(-1, HALF_LIFE));
        rejects("zero half-life rejected", () -> AttentionMath.decayFactor(100, 0));
        rejects("NaN half-life rejected", () -> AttentionMath.decayFactor(100, Double.NaN));
        rejects("negative probability rejected", () -> AttentionMath.fuse(-0.1, 1, 1, 1));
        rejects("negative alpha rejected", () -> AttentionMath.fuse(0.5, -1, 1, 1));
        rejects("negative strength rejected", () -> AttentionMath.fuse(0.5, 1, 1, -1));
        rejects("NaN probability rejected", () -> AttentionMath.logit(Double.NaN));
    }

    private static boolean immutable(List<Entry> list) {
        try { list.clear(); return false; } catch (UnsupportedOperationException expected) { return true; }
    }
    private static void yes(String label, boolean condition) { if (!condition) throw new AssertionError(label); count++; }
    private static void near(String label, double expected, double actual) { yes(label + ": expected=" + expected + " actual=" + actual, Math.abs(expected - actual) <= 1e-11); }
    private static void exact(String label, double expected, double actual) { yes(label, Double.doubleToLongBits(expected) == Double.doubleToLongBits(actual)); }
    private static void rejects(String label, Runnable action) {
        try { action.run(); throw new AssertionError(label); } catch (IllegalArgumentException expected) { count++; }
    }
}
