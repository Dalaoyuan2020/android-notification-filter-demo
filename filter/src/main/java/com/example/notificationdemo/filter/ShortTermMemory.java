package com.example.notificationdemo.filter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bounded, device-independent decaying Beta memory. No notification body or credentials stored. */
public final class ShortTermMemory {
    public static final int MAX_ENTRIES = 128;
    public static final int MAX_EVENTS_PER_ENTRY = 256;
    private static final int VERSION = 1;
    private static final int MAX_SERIALIZED_CHARS = 4 * 1024 * 1024;
    private final LinkedHashMap<String, State> entries = new LinkedHashMap<>();

    /** Every value is calculated at snapshotAt; actual feedback times remain separate. */
    public static final class Entry {
        public final String key, pkg, packageName, prefix, label, behaviorText;
        public final double alpha, beta, pShort, n, rawN, effectiveCount, change, windowStartProbability;
        public final long lastUpdatedAt, lastObservedAt, snapshotAt, totalObservations;
        public final int fastClicks, slowClicks, dismissals, ignores;
        public final boolean windowComplete;

        private Entry(String key, String pkg, String prefix, String label, double alpha, double beta,
                      long lastUpdatedAt, long lastObservedAt, long snapshotAt, long totalObservations,
                      int fastClicks, int slowClicks, int dismissals, int ignores, boolean windowComplete,
                      double before, double halfLifeMinutes) {
            this.key = key; this.pkg = pkg; this.packageName = pkg; this.prefix = prefix; this.label = label;
            this.alpha = alpha; this.beta = beta; this.pShort = AttentionMath.shortProbability(alpha, beta);
            this.n = AttentionMath.rawN(alpha, beta); this.rawN = n; this.effectiveCount = Math.max(0, n);
            this.lastUpdatedAt = lastUpdatedAt; this.lastObservedAt = lastObservedAt; this.snapshotAt = snapshotAt;
            this.totalObservations = totalObservations;
            this.fastClicks = fastClicks; this.slowClicks = slowClicks; this.dismissals = dismissals; this.ignores = ignores;
            this.windowComplete = windowComplete; this.windowStartProbability = before;
            // Missing window history cannot substantiate a largest-change claim.
            this.change = windowComplete ? Math.abs(pShort - before) : 0;
            this.behaviorText = displayName() + "：过去 " + minutes(halfLifeMinutes) + " 分钟"
                    + (windowComplete ? "" : "内已保留的记录（记录不完整）")
                    + "，点开快 " + fastClicks + " 次、点开慢 " + slowClicks + " 次、划掉 " + dismissals
                    + " 次、放着不管 " + ignores + " 次。";
        }

        public String displayName() { return prefix.isEmpty() ? label : label + " " + prefix; }
        public int windowCount() { return fastClicks + slowClicks + dismissals + ignores; }
    }

    public ShortTermMemory() { }

    /** A recognized title prefix always gets its own entry, even if it has never been observed. */
    public synchronized Entry get(String pkg, String title, String appName, long now, double halfLifeMinutes) {
        validateTime(now, halfLifeMinutes);
        String safePkg = bounded(pkg, 240);
        String prefix = prefixForTitle(title);
        String key = key(safePkg, prefix);
        State state = entries.get(key);
        if (state == null) return prior(safePkg, prefix, appName, now, halfLifeMinutes);
        return snapshot(state, now, halfLifeMinutes);
    }

    /** One observation updates the package aggregate and, when present, its isolated title prefix. */
    public synchronized void observe(String pkg, String title, String appName, AttentionMath.Observation observation,
                                     long now, double halfLifeMinutes) {
        validateTime(now, halfLifeMinutes);
        if (observation == null) return;
        String safePkg = bounded(pkg, 240);
        if (safePkg.isEmpty()) return;
        String safeLabel = bounded(appName, 160);
        if (safeLabel.isEmpty()) safeLabel = safePkg;
        update(safePkg, "", safeLabel, observation, now, halfLifeMinutes);
        String prefix = prefixForTitle(title);
        if (!prefix.isEmpty()) update(safePkg, prefix, safeLabel, observation, now, halfLifeMinutes);
    }

    public synchronized List<Entry> snapshots(long now, double halfLifeMinutes) {
        validateTime(now, halfLifeMinutes);
        List<Entry> result = new ArrayList<>();
        for (State state : entries.values()) result.add(snapshot(state, now, halfLifeMinutes));
        return Collections.unmodifiableList(result);
    }

    /** At most two complete-window changes, with prefix/package duplicates excluded. */
    public synchronized List<Entry> recentChanges(long now, double halfLifeMinutes, int limit) {
        List<Entry> candidates = new ArrayList<>();
        for (Entry entry : snapshots(now, halfLifeMinutes)) {
            if (entry.windowComplete && entry.windowCount() > 0 && entry.change > 0) candidates.add(entry);
        }
        candidates.sort(Comparator.comparingDouble((Entry entry) -> entry.change).reversed()
                .thenComparing(Comparator.comparingLong((Entry entry) -> entry.lastObservedAt).reversed())
                .thenComparingInt(entry -> entry.prefix.isEmpty() ? 1 : 0)
                .thenComparing(entry -> entry.key));
        List<Entry> result = new ArrayList<>();
        int boundedLimit = Math.max(0, Math.min(2, limit));
        for (Entry candidate : candidates) {
            boolean duplicate = false;
            for (Entry selected : result) {
                if (candidate.pkg.equals(selected.pkg) && (candidate.prefix.isEmpty() || selected.prefix.isEmpty())) duplicate = true;
            }
            if (!duplicate && result.size() < boundedLimit) result.add(candidate);
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized int size() { return entries.size(); }
    public synchronized void clear() { entries.clear(); }

    /** Versioned pure-Java transport for the Android store; bounded on read and write. */
    public synchronized String serialize() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(VERSION); output.writeInt(entries.size());
                for (State state : entries.values()) {
                    output.writeUTF(state.pkg); output.writeUTF(state.prefix); output.writeUTF(state.label);
                    output.writeDouble(state.alpha); output.writeDouble(state.beta);
                    output.writeLong(state.updatedAt); output.writeLong(state.observedAt);
                    output.writeLong(state.total); output.writeLong(state.discardedThrough);
                    output.writeInt(state.events.size());
                    for (Event event : state.events) {
                        output.writeLong(event.at); output.writeByte(event.kind.ordinal()); output.writeDouble(event.before);
                    }
                }
            }
            return "STM1:" + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException impossible) { throw new IllegalStateException("Unable to serialize short-term memory", impossible); }
    }

    /** Malformed, untrusted, or future-format data is discarded as a whole; no partial model. */
    public static ShortTermMemory deserialize(String serialized) {
        ShortTermMemory memory = new ShortTermMemory();
        if (serialized == null || !serialized.startsWith("STM1:") || serialized.length() > MAX_SERIALIZED_CHARS) return memory;
        try {
            byte[] decoded = Base64.getDecoder().decode(serialized.substring(5));
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(decoded))) {
                if (input.readInt() != VERSION) return new ShortTermMemory();
                int count = input.readInt();
                if (count < 0 || count > MAX_ENTRIES) throw new IOException("Entry limit");
                for (int i = 0; i < count; i++) {
                    String pkg = input.readUTF(), prefix = input.readUTF(), label = input.readUTF();
                    if (pkg.isEmpty() || pkg.length() > 240 || prefix.length() > 82 || label.length() > 160
                            || (!prefix.isEmpty() && !prefix.equals(prefixForTitle(prefix)) )) throw new IOException("Invalid identity");
                    State state = new State(pkg, prefix, label, 0);
                    state.alpha = input.readDouble(); state.beta = input.readDouble();
                    AttentionMath.rawN(state.alpha, state.beta);
                    state.updatedAt = input.readLong(); state.observedAt = input.readLong();
                    state.total = input.readLong(); state.discardedThrough = input.readLong();
                    if (state.updatedAt < 0 || state.observedAt < 0 || state.total < 1 || state.discardedThrough < -1) throw new IOException("Invalid metadata");
                    int events = input.readInt();
                    if (events < 0 || events > MAX_EVENTS_PER_ENTRY || events > state.total) throw new IOException("Event limit");
                    for (int j = 0; j < events; j++) {
                        long at = input.readLong(); int kind = input.readUnsignedByte(); double before = input.readDouble();
                        if (at < 0 || kind >= AttentionMath.Kind.values().length || !Double.isFinite(before) || before < 0 || before > 1) throw new IOException("Invalid event");
                        state.events.add(new Event(at, AttentionMath.Kind.values()[kind], before));
                    }
                    if (memory.entries.put(key(pkg, prefix), state) != null) throw new IOException("Duplicate identity");
                }
                if (input.read() != -1) throw new IOException("Trailing data");
            }
            return memory;
        } catch (IOException | IllegalArgumentException invalid) { return new ShortTermMemory(); }
    }

    public static String prefixForTitle(String title) {
        String value = title == null ? "" : title.trim();
        if (!value.startsWith("【")) return "";
        int end = value.indexOf('】', 1);
        if (end <= 1 || end > 81) return "";
        String inner = value.substring(1, end).trim();
        if (inner.isEmpty() || inner.indexOf('【') >= 0 || inner.indexOf('\n') >= 0 || inner.indexOf('\r') >= 0) return "";
        return "【" + inner + "】";
    }

    private void update(String pkg, String prefix, String label, AttentionMath.Observation observation,
                        long now, double halfLifeMinutes) {
        String identity = key(pkg, prefix);
        State state = entries.remove(identity);
        if (state == null) state = new State(pkg, prefix, label, now);
        double factor = AttentionMath.decayFactor(Math.max(0, now - state.updatedAt), halfLifeMinutes);
        state.alpha *= factor; state.beta *= factor;
        double before = AttentionMath.shortProbability(state.alpha, state.beta);
        state.alpha += observation.y; state.beta += 1.0 - observation.y;
        state.updatedAt = now; state.observedAt = now; state.label = label;
        if (state.total < Long.MAX_VALUE) state.total++;
        state.events.add(new Event(now, observation.kind, before));
        if (state.events.size() > MAX_EVENTS_PER_ENTRY) {
            Event dropped = state.events.remove(0);
            state.discardedThrough = Math.max(state.discardedThrough, dropped.at);
        }
        entries.put(identity, state);
        while (entries.size() > MAX_ENTRIES) {
            Iterator<Map.Entry<String, State>> oldest = entries.entrySet().iterator();
            oldest.next(); oldest.remove();
        }
    }

    private Entry snapshot(State state, long now, double halfLifeMinutes) {
        double factor = AttentionMath.decayFactor(Math.max(0, now - state.updatedAt), halfLifeMinutes);
        double alpha = state.alpha * factor, beta = state.beta * factor;
        double before = AttentionMath.shortProbability(alpha, beta);
        double start = now - AttentionMath.halfLifeMillis(halfLifeMinutes);
        boolean found = false;
        int fast = 0, slow = 0, dismiss = 0, ignore = 0;
        for (Event event : state.events) {
            if (event.at < start || event.at > now) continue;
            if (!found) { before = event.before; found = true; }
            switch (event.kind) {
                case FAST_CLICK: fast++; break;
                case SLOW_CLICK: slow++; break;
                case DISMISS: dismiss++; break;
                case IGNORE: ignore++; break;
                default: break;
            }
        }
        boolean complete = state.discardedThrough < 0 || state.discardedThrough < start;
        return new Entry(key(state.pkg, state.prefix), state.pkg, state.prefix, state.label, alpha, beta,
                state.updatedAt, state.observedAt, now, state.total, fast, slow, dismiss, ignore, complete, before, halfLifeMinutes);
    }

    private static Entry prior(String pkg, String prefix, String appName, long now, double halfLifeMinutes) {
        String label = bounded(appName, 160);
        if (label.isEmpty()) label = pkg;
        return new Entry(key(pkg, prefix), pkg, prefix, label, 1, 1, now, 0, now, 0, 0, 0, 0, 0, true, 0.5, halfLifeMinutes);
    }
    private static String key(String pkg, String prefix) { return prefix.isEmpty() ? "p:" + pkg : "t:" + pkg + "\n" + prefix; }
    private static String bounded(String value, int maximum) {
        String result = value == null ? "" : value.trim();
        if (result.length() <= maximum) return result;
        int end = maximum;
        if (Character.isHighSurrogate(result.charAt(end - 1))) end--;
        return result.substring(0, end);
    }
    private static String minutes(double value) {
        return value == Math.rint(value) ? String.format(Locale.ROOT, "%.0f", value) : String.format(Locale.ROOT, "%.2f", value);
    }
    private static void validateTime(long now, double halfLifeMinutes) {
        if (now < 0) throw new IllegalArgumentException("now must be nonnegative");
        AttentionMath.halfLifeMillis(halfLifeMinutes);
    }
    private static final class State {
        final String pkg, prefix;
        String label;
        double alpha = 1, beta = 1;
        long updatedAt, observedAt, total, discardedThrough = -1;
        final List<Event> events = new ArrayList<>();
        State(String pkg, String prefix, String label, long now) {
            this.pkg = pkg; this.prefix = prefix; this.label = label; updatedAt = now;
        }
    }
    private static final class Event {
        final long at; final AttentionMath.Kind kind; final double before;
        Event(long at, AttentionMath.Kind kind, double before) { this.at = at; this.kind = kind; this.before = before; }
    }
}
