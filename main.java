import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * FredAgainMoon — lunar-cycle generative lyric and hook atelier.
 * Composes verse/chorus arcs, stem lanes, and royalty splits against
 * constructor-wired studio addresses. Single-file runtime; no external lyric CSV.
 */

// ======================== Enums ========================

enum PitchClass {
    C(0, "C", 60),
    Cs(1, "C#", 61),
    D(2, "D", 62),
    Ds(3, "D#", 63),
    E(4, "E", 64),
    F(5, "F", 65),
    Fs(6, "F#", 66),
    G(7, "G", 67),
    Gs(8, "G#", 68),
    A(9, "A", 69),
    As(10, "A#", 70),
    B(11, "B", 71);

    private final int degree;
    private final String label;
    private final int midiRoot;

    PitchClass(int degree, String label, int midiRoot) {
        this.degree = degree;
        this.label = label;
        this.midiRoot = midiRoot;
    }

    public int getDegree() { return degree; }
    public String getLabel() { return label; }
    public int getMidiRoot() { return midiRoot; }

    public static PitchClass fromDegree(int d) {
        for (PitchClass p : values()) if (p.degree == d % 12) return p;
        throw new FamComposeException("FAM_PITCH", "Unknown pitch degree " + d);
    }
}

enum ScalePalette {
    IONIAN(0, "Ionian", new int[]{0, 2, 4, 5, 7, 9, 11}),
    DORIAN(1, "Dorian", new int[]{0, 2, 3, 5, 7, 9, 10}),
    MIXOLYDIAN(2, "Mixolydian", new int[]{0, 2, 4, 5, 7, 9, 10}),
    AEOLIAN(3, "Aeolian", new int[]{0, 2, 3, 5, 7, 8, 10}),
    LYDIAN(4, "Lydian", new int[]{0, 2, 4, 6, 7, 9, 11}),
    PHRYGIAN(5, "Phrygian", new int[]{0, 1, 3, 5, 7, 8, 10}),
    PENTATONIC_MAJOR(6, "Pent Maj", new int[]{0, 2, 4, 7, 9}),
    PENTATONIC_MINOR(7, "Pent Min", new int[]{0, 3, 5, 7, 10});

    private final int id;
    private final String display;
    private final int[] intervals;

    ScalePalette(int id, String display, int[] intervals) {
        this.id = id;
        this.display = display;
        this.intervals = intervals;
    }

    public int getId() { return id; }
    public String getDisplay() { return display; }
    public int[] getIntervals() { return Arrays.copyOf(intervals, intervals.length); }

    public int degreeCount() { return intervals.length; }
}

enum LyricMood {
    EUPHORIC(1.12, "Euphoric"),
    MELANCHOLY(0.88, "Melancholy"),
    NOCTURNAL(0.94, "Nocturnal"),
    ANTICIPATION(1.06, "Anticipation"),
    REFLECTIVE(0.97, "Reflective"),
    CHAOTIC(1.18, "Chaotic");

    private final double hookBoost;
    private final String tag;

    LyricMood(double hookBoost, String tag) {
        this.hookBoost = hookBoost;
        this.tag = tag;
    }

    public double getHookBoost() { return hookBoost; }
    public String getTag() { return tag; }

    public static LyricMood random() {
        return values()[ThreadLocalRandom.current().nextInt(values().length)];
    }
}

enum SongSection {
    INTRO(0),
    VERSE(1),
    PRECHORUS(2),
    CHORUS(3),
    BRIDGE(4),
    BREAKDOWN(5),
    OUTRO(6);

    private final int code;
    SongSection(int code) { this.code = code; }
    public int getCode() { return code; }
}

enum StemLane {
    VOCAL_LEAD(0, "Vocal Lead"),
    VOCAL_STACK(1, "Vocal Stack"),
    DRUMS(2, "Drums"),
    BASS(3, "Bass"),
    SYNTH_PAD(4, "Synth Pad"),
    ARP(5, "Arp"),
    FX_RISER(6, "FX Riser"),
    AMBIENCE(7, "Ambience");

    private final int laneId;
    private final String label;

    StemLane(int laneId, String label) {
        this.laneId = laneId;
        this.label = label;
    }

    public int getLaneId() { return laneId; }
    public String getLabel() { return label; }
}

enum StudioPhase {
    IDLE(0),
    LYRIC_LOCK(1),
    MELODY_SKETCH(2),
    ARRANGE(3),
    MIX_PASS(4),
    MASTER(5),
    COOLDOWN(6);

    private final int code;
    StudioPhase(int code) { this.code = code; }
    public int getCode() { return code; }
}

enum HarmonyVerdict {
    DISSONANT(0),
    TENSION(1),
    RESOLVED(2),
    HOOK(3),
    CLICHE(4);

    private final int code;
    HarmonyVerdict(int code) { this.code = code; }
    public int getCode() { return code; }
}

enum HookBetKind {
    DOUBLE_HOOK(0, "Double Hook", 14),
    BRIDGE_LIFT(1, "Bridge Lift", 22),
    MOON_DROP(2, "Moon Drop", 160);

    private final int id;
    private final String label;
    private final int royaltyMultiple;

    HookBetKind(int id, String label, int royaltyMultiple) {
        this.id = id;
        this.label = label;
        this.royaltyMultiple = royaltyMultiple;
    }

    public int getId() { return id; }
    public String getLabel() { return label; }
    public int getRoyaltyMultiple() { return royaltyMultiple; }
}

enum ReleaseRail {
    MAINNET(1, 1, "Ethereum Main"),
    BASE(8453, 6, "Base L2"),
    ARBITRUM(42161, 18, "Arbitrum One"),
    OPTIMISM(10, 12, "Optimism"),
    POLYGON(137, 30, "Polygon PoS");

    private final int chainId;
    private final int confirmBlocks;
    private final String label;

    ReleaseRail(int chainId, int confirmBlocks, String label) {
        this.chainId = chainId;
        this.confirmBlocks = confirmBlocks;
        this.label = label;
    }

    public int getChainId() { return chainId; }
    public int getConfirmBlocks() { return confirmBlocks; }
    public String getLabel() { return label; }

    public static ReleaseRail byId(int id) {
        for (ReleaseRail r : values()) if (r.chainId == id) return r;
        return MAINNET;
    }
}

enum WriterArchetype {
    BEDROOM_SCRIBE(0, 0, "Bedroom Scribe", 1.00),
    LOOP_CRAFTER(1, 140, "Loop Crafter", 1.05),
    HOOK_SMITH(2, 320, "Hook Smith", 1.09),
    STUDIO_ORACLE(3, 620, "Studio Oracle", 1.13),
    MOON_CONDUCTOR(4, 1100, "Moon Conductor", 1.17),
    AGAIN_ARCHITECT(5, 1800, "Again Architect", 1.21);

    private final int id;
    private final int xpGate;
    private final String title;
    private final double royaltyBoost;

    WriterArchetype(int id, int xpGate, String title, double royaltyBoost) {
        this.id = id;
        this.xpGate = xpGate;
        this.title = title;
        this.royaltyBoost = royaltyBoost;
    }

    public int getId() { return id; }
    public int getXpGate() { return xpGate; }
    public String getTitle() { return title; }
    public double getRoyaltyBoost() { return royaltyBoost; }

    public static WriterArchetype forXp(int xp) {
        WriterArchetype best = BEDROOM_SCRIBE;
        for (WriterArchetype a : values()) if (xp >= a.xpGate) best = a;
        return best;
    }
}

// ======================== Constants ========================

final class MoonStudioConfig {
    private MoonStudioConfig() {}

    static final String ADDRESS_STUDIO = "0xc37AaE7c771Dc1Ec0DDbeEAfeE252D8bEa9d542f";
    static final String ADDRESS_ROYALTY_POOL = "0xDcF1fD2dc0df3544E3e774A9Ef6B9E8aDe85bc56";
    static final String ADDRESS_ORACLE = "0xbCce6B185b6DDAE4d109BeCcCdA2FF0fE2E6A8b3";
    static final String ADDRESS_COLLAB_DESK = "0xAddd42343cbA0eaF7BE71addCBB7bDD0b4aBcB36";
    static final String ADDRESS_PUBLISHER = "0xeEF8ac4Ab74dE4910fF6D9ae6932Efd672596f0C";
    static final String ADDRESS_STEM_VAULT = "0x2cFA22415C86E9A20c31Afa8052e2b1D8bA980aa";
    static final String ADDRESS_PAUSE_GUARD = "0xa2a773d0d1bEAbEfBB04CB3cCD28Af2A87dEC58C";
    static final String ADDRESS_REMIX_LAB = "0xe24eEe4eC6bEfBAbcfdBEa5bDCb5D8F7Bab4bCA9";
    static final String ADDRESS_VOCAL_BOOTH = "0xcA9ABc37d744bC114A0eAeFd8a7c96f2EC9f1384";
    static final String ADDRESS_MASTER_ROOM = "0xFB5F4BaDFFE3Fe3daBEecBf2173E084F93f1E9f2";
    static final String ADDRESS_REFERRAL = "0x9D2Daa6B53da5ffA9B5045D3aB94e5cB1f0Af227";

    static final String DOMAIN_SEPARATOR = "0xB668CD2Fb5D21B3BEFaC77BE5A8564c6Fb29eC5e0Eff49d3a55773fC8dBe5088";
    static final String CHAIN_SALT = "0xDB96D3cB5BCfe00CECd6AECCFd1C2Dc0d6617Ce61E709FC40eF0cD9D1F13CBA0";

    static final int BPS_DENOM = 10_000;
    static final int PUBLISHER_CUT_BPS = 1250;
    static final int COLLAB_CAP_BPS = 3800;
    static final int HOOK_BONUS_BPS = 16_500;
    static final int STANDARD_ROYALTY_BPS = 20_000;
    static final int MIN_BPM = 72;
    static final int MAX_BPM = 178;
    static final int DEFAULT_TIME_SIG_NUM = 4;
    static final int DEFAULT_TIME_SIG_DEN = 4;
    static final int MAX_BARS_PER_SECTION = 32;
    static final int MIN_BARS_PER_SECTION = 4;
    static final BigDecimal MIN_STAKE_ETH = new BigDecimal("0.001");
    static final BigDecimal MAX_STAKE_ETH = new BigDecimal("18");
    static final BigDecimal MIN_HOOK_STAKE_ETH = new BigDecimal("0.0004");
    static final int MAX_TRACKS_PER_SESSION = 2_800;
    static final int LEADERBOARD_CAP = 144;
    static final int HISTORY_CAP = 720;
    static final int MAX_SYLLABLES_PER_LINE = 18;
    static final int MIN_SYLLABLES_PER_LINE = 4;
}

// ======================== Exceptions ========================

final class FamComposeException extends RuntimeException {
    private final String famCode;

    FamComposeException(String famCode, String detail) {
        super(detail);
        this.famCode = famCode;
    }

    public String getFamCode() { return famCode; }
}

final class FamStakeException extends RuntimeException {
    private final String stakeCode;

    FamStakeException(String stakeCode, String detail) {
        super(detail);
        this.stakeCode = stakeCode;
    }

    public String getStakeCode() { return stakeCode; }
}

final class FamPauseException extends RuntimeException {
    FamPauseException(String detail) { super(detail); }
}

// ======================== Events ========================

interface FamStudioListener {
    void onTrackOpened(long trackId, String writerId);
    void onLyricLine(long trackId, SongSection section, String line);
    void onMelodyNote(long trackId, int midi, PitchClass pitch);
    void onVerdict(long trackId, HarmonyVerdict verdict, BigDecimal royaltyEth);
    void onRoyaltyMove(String lane, BigDecimal amountEth, String targetAddr);
    void onPhaseShift(StudioPhase phase);
}

final class FamStudioEventBus {
    private final List<FamStudioListener> listeners = new ArrayList<>();

    void subscribe(FamStudioListener listener) {
        if (listener != null) listeners.add(listener);
    }

    void emitTrack(long trackId, String writerId) {
        for (FamStudioListener l : listeners) l.onTrackOpened(trackId, writerId);
    }

    void emitLyric(long trackId, SongSection section, String line) {
        for (FamStudioListener l : listeners) l.onLyricLine(trackId, section, line);
    }

    void emitNote(long trackId, int midi, PitchClass pitch) {
        for (FamStudioListener l : listeners) l.onMelodyNote(trackId, midi, pitch);
    }

    void emitVerdict(long trackId, HarmonyVerdict verdict, BigDecimal delta) {
        for (FamStudioListener l : listeners) l.onVerdict(trackId, verdict, delta);
    }

    void emitRoyalty(String lane, BigDecimal amount, String target) {
        for (FamStudioListener l : listeners) l.onRoyaltyMove(lane, amount, target);
    }

    void emitPhase(StudioPhase phase) {
        for (FamStudioListener l : listeners) l.onPhaseShift(phase);
    }
}

// ======================== Lyric & melody models ========================

final class LyricLine {
    private final String text;
    private final int syllables;
    private final SongSection section;
    private final int barIndex;

    LyricLine(String text, int syllables, SongSection section, int barIndex) {
        this.text = text;
        this.syllables = syllables;
        this.section = section;
        this.barIndex = barIndex;
    }

    public String getText() { return text; }
    public int getSyllables() { return syllables; }
    public SongSection getSection() { return section; }
    public int getBarIndex() { return barIndex; }
}

final class MelodyNote {
    private final int midi;
    private final PitchClass pitch;
    private final int durationTicks;
    private final int barIndex;

    MelodyNote(int midi, PitchClass pitch, int durationTicks, int barIndex) {
        this.midi = midi;
        this.pitch = pitch;
        this.durationTicks = durationTicks;
        this.barIndex = barIndex;
    }

    public int getMidi() { return midi; }
    public PitchClass getPitch() { return pitch; }
    public int getDurationTicks() { return durationTicks; }
    public int getBarIndex() { return barIndex; }
}

final class VerseBlock {
    private final List<LyricLine> lines = new ArrayList<>();
    private final List<MelodyNote> notes = new ArrayList<>();
    private final SongSection section;
    private int barCount;

    VerseBlock(SongSection section) {
        this.section = section;
    }

    void addLine(LyricLine line) { lines.add(line); }
    void addNote(MelodyNote note) { notes.add(note); }
    void setBarCount(int barCount) { this.barCount = barCount; }

    public SongSection getSection() { return section; }
    public List<LyricLine> getLines() { return Collections.unmodifiableList(lines); }
    public List<MelodyNote> getNotes() { return Collections.unmodifiableList(notes); }
    public int getBarCount() { return barCount; }

    int totalSyllables() {
        return lines.stream().mapToInt(LyricLine::getSyllables).sum();
    }

    double averageMidi() {
        if (notes.isEmpty()) return 60;
        return notes.stream().mapToInt(MelodyNote::getMidi).average().orElse(60);
    }
}

final class SongSketch {
    private final List<VerseBlock> blocks = new ArrayList<>();
    private int bpm = 120;
    private PitchClass tonic = PitchClass.A;
    private ScalePalette scale = ScalePalette.IONIAN;
    private LyricMood mood = LyricMood.NOCTURNAL;

    void addBlock(VerseBlock block) { blocks.add(block); }
    public List<VerseBlock> getBlocks() { return Collections.unmodifiableList(blocks); }
    public int getBpm() { return bpm; }
    public void setBpm(int bpm) { this.bpm = bpm; }
    public PitchClass getTonic() { return tonic; }
    public void setTonic(PitchClass tonic) { this.tonic = tonic; }
    public ScalePalette getScale() { return scale; }
    public void setScale(ScalePalette scale) { this.scale = scale; }
    public LyricMood getMood() { return mood; }
    public void setMood(LyricMood mood) { this.mood = mood; }

    int hookDensity() {
        long chorusLines = blocks.stream()
                .filter(b -> b.getSection() == SongSection.CHORUS)
                .mapToInt(b -> b.getLines().size())
                .sum();
        return (int) chorusLines;
    }
}

final class LyricSeedBank {
    private final SecureRandom rng;
    private final String[] openers;
    private final String[] bridges;
    private final String[] closers;

    LyricSeedBank(SecureRandom rng) {
        this.rng = rng;
        this.openers = new String[] {
                "Moonlight writes the", "Again we trace the", "Silicon hearts in",
                "Tape hiss carries", "Neon verbs on", "Pulse metronome in",
                "Ghost choir hums a", "Satellite delay on", "Soft limiter kisses"
        };
        this.bridges = new String[] {
                "chorus lane tonight", "hook that never lands", "verse we overwrite",
                "bridge of borrowed time", "drop before the dawn", "stem that multiplies",
                "line the model sang", "prompt in minor key", "loop that finds you"
        };
        this.closers = new String[] {
                "again.", "on repeat.", "in 7/8 time.", "till sunrise.", "for export.",
                "to mainnet.", "unmastered.", "with sidechain.", "and moonphase."
        };
    }

    String weaveLine(SongSection section) {
        String o = openers[rng.nextInt(openers.length)];
        String b = bridges[rng.nextInt(bridges.length)];
        String c = closers[rng.nextInt(closers.length)];
        String raw = o + " " + b + " " + c;
        if (section == SongSection.CHORUS) raw = raw.toUpperCase(Locale.ROOT);
        return raw.length() > 96 ? raw.substring(0, 96) : raw;
    }

    int estimateSyllables(String line) {
        String[] parts = line.trim().split("\\s+");
        int sum = 0;
        for (String p : parts) sum += Math.max(1, (int) Math.ceil(p.length() / 2.8));
        return Math.min(MoonStudioConfig.MAX_SYLLABLES_PER_LINE, sum);
    }
}

final class MelodyWeaver {
    private final SecureRandom rng;

    MelodyWeaver(SecureRandom rng) { this.rng = rng; }

    List<MelodyNote> weavePhrase(PitchClass tonic, ScalePalette scale, int bars, int bpm) {
        List<MelodyNote> notes = new ArrayList<>();
        int[] intervals = scale.getIntervals();
        int rootMidi = tonic.getMidiRoot();
        int ticksPerBar = 480 * MoonStudioConfig.DEFAULT_TIME_SIG_NUM;
        for (int bar = 0; bar < bars; bar++) {
            int steps = 4 + rng.nextInt(5);
            for (int s = 0; s < steps; s++) {
                int deg = intervals[rng.nextInt(intervals.length)];
                int midi = rootMidi + deg + (bar % 2 == 0 ? 0 : 12);
                int dur = ticksPerBar / steps;
                notes.add(new MelodyNote(midi, PitchClass.fromDegree(deg), dur, bar));
            }
        }
        return notes;
    }
}

// ======================== Writer profile ========================

final class WriterSeatProfile {
    private final String writerId;
    private final String walletHex;
    private int xp;
    private int hookStreak;
    private int flatStreak;
    private BigDecimal lifetimeEarned = BigDecimal.ZERO;
    private BigDecimal lifetimeSpent = BigDecimal.ZERO;
    private final Deque<String> recentTracks = new ArrayDeque<>();

    WriterSeatProfile(String writerId, String walletHex) {
        this.writerId = writerId;
        this.walletHex = walletHex;
    }

    public String getWriterId() { return writerId; }
    public String getWalletHex() { return walletHex; }
    public int getXp() { return xp; }
    public void addXp(int delta) { xp = Math.max(0, xp + delta); }
    public int getHookStreak() { return hookStreak; }
    public int getFlatStreak() { return flatStreak; }

    void recordOutcome(HarmonyVerdict v, BigDecimal delta) {
        if (delta.signum() > 0) {
            hookStreak++;
            flatStreak = 0;
            lifetimeEarned = lifetimeEarned.add(delta);
        } else if (delta.signum() < 0) {
            flatStreak++;
            hookStreak = 0;
            lifetimeSpent = lifetimeSpent.add(delta.abs());
        }
        if (v == HarmonyVerdict.HOOK) addXp(44);
        else if (v == HarmonyVerdict.RESOLVED) addXp(20);
        else if (v == HarmonyVerdict.TENSION) addXp(6);
        else addXp(2);
    }

    void pushHistory(String snippet) {
        recentTracks.addFirst(snippet);
        while (recentTracks.size() > 36) recentTracks.removeLast();
    }

    public List<String> getRecentTracks() { return new ArrayList<>(recentTracks); }
    public WriterArchetype getArchetype() { return WriterArchetype.forXp(xp); }
    public BigDecimal netEth() { return lifetimeEarned.subtract(lifetimeSpent); }
}

final class FamLeaderboardEntry implements Comparable<FamLeaderboardEntry> {
    final String writerId;
    final BigDecimal netEth;
    final int xp;
    final long updatedEpoch;

    FamLeaderboardEntry(String writerId, BigDecimal netEth, int xp, long updatedEpoch) {
        this.writerId = writerId;
        this.netEth = netEth;
        this.xp = xp;
        this.updatedEpoch = updatedEpoch;
    }

    @Override
    public int compareTo(FamLeaderboardEntry o) {
        int c = o.netEth.compareTo(netEth);
        if (c != 0) return c;
        return Integer.compare(o.xp, xp);
    }
}

final class FamLeaderboard {
    private final PriorityQueue<FamLeaderboardEntry> heap =
            new PriorityQueue<>(Comparator.reverseOrder());

    synchronized void upsert(WriterSeatProfile profile) {
        FamLeaderboardEntry e = new FamLeaderboardEntry(
                profile.getWriterId(),
                profile.netEth(),
                profile.getXp(),
                Instant.now().getEpochSecond());
        heap.offer(e);
        while (heap.size() > MoonStudioConfig.LEADERBOARD_CAP) heap.poll();
    }

    synchronized List<FamLeaderboardEntry> top(int n) {
        return heap.stream().sorted().limit(n).collect(Collectors.toList());
    }
}

// ======================== Royalty ledger ========================

final class FamRoyaltyLedger {
    private BigDecimal studioBalance = BigDecimal.ZERO;
    private BigDecimal publisherAccrued = BigDecimal.ZERO;
    private BigDecimal collabPool = BigDecimal.ZERO;
    private BigDecimal stemPool = BigDecimal.ZERO;
    private final AtomicLong moveSeq = new AtomicLong(0);
    private final List<String> audit = new ArrayList<>();
    private final FamStudioEventBus bus;

    FamRoyaltyLedger(FamStudioEventBus bus) {
        this.bus = bus;
    }

    void creditStudio(BigDecimal eth, String lane) {
        studioBalance = studioBalance.add(eth);
        bus.emitRoyalty(lane, eth, MoonStudioConfig.ADDRESS_STUDIO);
        audit("STUDIO+" + eth + "@" + lane);
    }

    void applyPublisherCut(BigDecimal gross) {
        BigDecimal cut = gross.multiply(BigDecimal.valueOf(MoonStudioConfig.PUBLISHER_CUT_BPS))
                .divide(BigDecimal.valueOf(MoonStudioConfig.BPS_DENOM), 8, RoundingMode.HALF_UP);
        BigDecimal capped = cut.min(gross.multiply(BigDecimal.valueOf(MoonStudioConfig.COLLAB_CAP_BPS))
                .divide(BigDecimal.valueOf(MoonStudioConfig.BPS_DENOM), 8, RoundingMode.HALF_UP));
        publisherAccrued = publisherAccrued.add(capped);
        studioBalance = studioBalance.add(capped);
        bus.emitRoyalty("PUBLISHER", capped, MoonStudioConfig.ADDRESS_PUBLISHER);
        audit("PUBLISHER+" + capped);
    }

    void payWriter(BigDecimal eth) {
        if (studioBalance.compareTo(eth) < 0) {
            collabPool = collabPool.subtract(eth.subtract(studioBalance));
            studioBalance = BigDecimal.ZERO;
        } else {
            studioBalance = studioBalance.subtract(eth);
        }
        audit("PAYOUT-" + eth);
    }

    void hookStakeSink(BigDecimal eth, boolean hit, HookBetKind kind) {
        if (hit) {
            BigDecimal payout = eth.multiply(BigDecimal.valueOf(kind.getRoyaltyMultiple()));
            stemPool = stemPool.subtract(payout);
            payWriter(payout);
            bus.emitRoyalty("HOOK_WIN", payout, MoonStudioConfig.ADDRESS_STEM_VAULT);
        } else {
            stemPool = stemPool.add(eth);
            bus.emitRoyalty("HOOK_LOSS", eth, MoonStudioConfig.ADDRESS_STEM_VAULT);
        }
    }

    private void audit(String line) {
        String ts = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC).format(Instant.now());
        audit.add(moveSeq.incrementAndGet() + "|" + ts + "|" + line);
        if (audit.size() > MoonStudioConfig.HISTORY_CAP) audit.remove(0);
    }

    public BigDecimal getStudioBalance() { return studioBalance; }
    public BigDecimal getPublisherAccrued() { return publisherAccrued; }
    public List<String> getAuditTail(int n) {
        int from = Math.max(0, audit.size() - n);
        return new ArrayList<>(audit.subList(from, audit.size()));
    }
}

// ======================== Hook resolver ========================

final class FamHookResolver {
    boolean resolveDoubleHook(SongSketch sketch) {
        return sketch.hookDensity() >= 3;
    }

    boolean resolveBridgeLift(SongSketch sketch) {
        return sketch.getBlocks().stream()
                .anyMatch(b -> b.getSection() == SongSection.BRIDGE && b.totalSyllables() > 24);
    }

    boolean resolveMoonDrop(SongSketch sketch, HarmonyVerdict verdict) {
        return verdict == HarmonyVerdict.HOOK && sketch.getBpm() >= 128;
    }
}

// ======================== Fairness digest ========================

final class FamCommitReveal {
    private final byte[] seed;
    private final String commitHash;

    FamCommitReveal(SecureRandom rng) {
        seed = new byte[32];
        rng.nextBytes(seed);
        commitHash = sha256Hex(seed);
    }

    public String getCommitHash() { return commitHash; }
    public byte[] getSeed() { return Arrays.copyOf(seed, seed.length); }

    static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(data);
            StringBuilder sb = new StringBuilder("0x");
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new FamComposeException("FAM_HASH", e.getMessage());
        }
    }

    String mixTrack(long trackId, String writerId) {
        String payload = MoonStudioConfig.DOMAIN_SEPARATOR + "|" + trackId + "|" + writerId + "|" + commitHash;
        return sha256Hex(payload.getBytes(StandardCharsets.UTF_8));
    }
}

// ======================== Composition engine ========================

final class FamSongAtelier {
    private final SecureRandom rng;
    private final FamStudioEventBus bus;
    private final FamRoyaltyLedger royalty;
    private final FamHookResolver hookResolver;
    private final LyricSeedBank lyricBank;
    private final MelodyWeaver melodyWeaver;
    private final Map<String, WriterSeatProfile> writers = new ConcurrentHashMap<>();
    private final FamLeaderboard leaderboard = new FamLeaderboard();
    private final AtomicLong trackSeq = new AtomicLong(0);
    private StudioPhase phase = StudioPhase.IDLE;
    private boolean halted;
    private final String studioAddr;
    private final String oracleAddr;
    private final ReleaseRail rail;

    FamSongAtelier(String studioAddr, String oracleAddr, ReleaseRail rail, SecureRandom rng) {
        this.studioAddr = studioAddr;
        this.oracleAddr = oracleAddr;
        this.rail = rail;
        this.rng = rng;
        this.bus = new FamStudioEventBus();
        this.royalty = new FamRoyaltyLedger(bus);
        this.hookResolver = new FamHookResolver();
        this.lyricBank = new LyricSeedBank(rng);
        this.melodyWeaver = new MelodyWeaver(rng);
    }

    public FamStudioEventBus getBus() { return bus; }
    public StudioPhase getPhase() { return phase; }
    public ReleaseRail getRail() { return rail; }

    void setHalted(boolean halted) {
        this.halted = halted;
        if (halted) bus.emitPhase(StudioPhase.COOLDOWN);
    }

    WriterSeatProfile registerWriter(String writerId, String walletHex) {
        validateAddr(walletHex);
        return writers.computeIfAbsent(writerId, id -> new WriterSeatProfile(id, walletHex));
    }

    FamTrackResult composeTrack(String writerId, BigDecimal stakeEth, List<HookBetKind> hooks) {
        ensureActive();
        if (phase != StudioPhase.IDLE && phase != StudioPhase.COOLDOWN) {
            throw new FamComposeException("FAM_PHASE", "Studio busy at phase " + phase);
        }
        validateStake(stakeEth);
        WriterSeatProfile seat = writers.get(writerId);
        if (seat == null) throw new FamComposeException("FAM_WRITER", "Unknown writer " + writerId);

        long trackId = trackSeq.incrementAndGet();
        FamCommitReveal fairness = new FamCommitReveal(rng);
        phase = StudioPhase.LYRIC_LOCK;
        bus.emitPhase(phase);
        bus.emitTrack(trackId, writerId);
        royalty.creditStudio(stakeEth, "ANTE");

        SongSketch sketch = buildSketch(trackId);
        phase = StudioPhase.MELODY_SKETCH;
        bus.emitPhase(phase);

        phase = StudioPhase.ARRANGE;
        bus.emitPhase(phase);
        HarmonyVerdict verdict = scoreHarmony(sketch, seat);
        BigDecimal payout = computeRoyalty(stakeEth, verdict, seat, sketch);
        royalty.applyPublisherCut(stakeEth);
        if (payout.signum() > 0) royalty.payWriter(payout);

        BigDecimal hookTotal = BigDecimal.ZERO;
        for (HookBetKind kind : hooks) {
            BigDecimal hookAmt = stakeEth.multiply(BigDecimal.valueOf(0.12)).max(MoonStudioConfig.MIN_HOOK_STAKE_ETH);
            hookTotal = hookTotal.add(hookAmt);
            royalty.creditStudio(hookAmt, "HOOK_ANTE");
            boolean hit = switch (kind) {
                case DOUBLE_HOOK -> hookResolver.resolveDoubleHook(sketch);
                case BRIDGE_LIFT -> hookResolver.resolveBridgeLift(sketch);
                case MOON_DROP -> hookResolver.resolveMoonDrop(sketch, verdict);
            };
            royalty.hookStakeSink(hookAmt, hit, kind);
        }

        seat.recordOutcome(verdict, payout.subtract(stakeEth).subtract(hookTotal));
        seat.pushHistory(trackId + ":" + verdict.name() + ":" + payout);
        leaderboard.upsert(seat);
        bus.emitVerdict(trackId, verdict, payout);

        phase = StudioPhase.COOLDOWN;
        bus.emitPhase(phase);
        return new FamTrackResult(trackId, verdict, payout, fairness.getCommitHash(), sketch.getBpm(), sketch.getTonic());
    }

    private SongSketch buildSketch(long trackId) {
        SongSketch sketch = new SongSketch();
        sketch.setBpm(MoonStudioConfig.MIN_BPM + rng.nextInt(MoonStudioConfig.MAX_BPM - MoonStudioConfig.MIN_BPM + 1));
        sketch.setTonic(PitchClass.values()[rng.nextInt(PitchClass.values().length)]);
        sketch.setScale(ScalePalette.values()[rng.nextInt(ScalePalette.values().length)]);
        sketch.setMood(LyricMood.random());

        SongSection[] flow = {
                SongSection.INTRO, SongSection.VERSE, SongSection.PRECHORUS,
                SongSection.CHORUS, SongSection.VERSE, SongSection.BRIDGE,
                SongSection.CHORUS, SongSection.OUTRO
        };
        for (SongSection sec : flow) {
            int bars = MoonStudioConfig.MIN_BARS_PER_SECTION + rng.nextInt(
                    MoonStudioConfig.MAX_BARS_PER_SECTION - MoonStudioConfig.MIN_BARS_PER_SECTION + 1);
            VerseBlock block = new VerseBlock(sec);
            block.setBarCount(bars);
            int lineCount = 2 + rng.nextInt(5);
            for (int i = 0; i < lineCount; i++) {
                String line = lyricBank.weaveLine(sec);
                int syll = lyricBank.estimateSyllables(line);
                block.addLine(new LyricLine(line, syll, sec, i));
                bus.emitLyric(trackId, sec, line);
            }
            for (MelodyNote n : melodyWeaver.weavePhrase(sketch.getTonic(), sketch.getScale(), bars, sketch.getBpm())) {
                block.addNote(n);
                bus.emitNote(trackId, n.getMidi(), n.getPitch());
            }
            sketch.addBlock(block);
        }
        return sketch;
    }

    private HarmonyVerdict scoreHarmony(SongSketch sketch, WriterSeatProfile seat) {
        double avgMidi = sketch.getBlocks().stream().mapToDouble(VerseBlock::averageMidi).average().orElse(60);
        int syllTotal = sketch.getBlocks().stream().mapToInt(VerseBlock::totalSyllables).sum();
        double mood = sketch.getMood().getHookBoost();
        double arch = seat.getArchetype().getRoyaltyBoost();
        double score = (avgMidi / 72.0) * 0.35 + (syllTotal / 200.0) * 0.25 + mood * arch * sketch.hookDensity() * 0.08;
        if (score > 2.4) return HarmonyVerdict.HOOK;
        if (score > 1.7) return HarmonyVerdict.RESOLVED;
        if (score > 1.1) return HarmonyVerdict.TENSION;
        if (score < 0.6) return HarmonyVerdict.DISSONANT;
        return HarmonyVerdict.CLICHE;
    }

    private BigDecimal computeRoyalty(BigDecimal stake, HarmonyVerdict verdict, WriterSeatProfile seat, SongSketch sketch) {
        double boost = seat.getArchetype().getRoyaltyBoost() * sketch.getMood().getHookBoost();
        return switch (verdict) {
            case HOOK -> stake.multiply(BigDecimal.valueOf(MoonStudioConfig.HOOK_BONUS_BPS))
                    .divide(BigDecimal.valueOf(MoonStudioConfig.BPS_DENOM), 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(boost));
            case RESOLVED -> stake.multiply(BigDecimal.valueOf(MoonStudioConfig.STANDARD_ROYALTY_BPS))
                    .divide(BigDecimal.valueOf(MoonStudioConfig.BPS_DENOM), 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(boost));
            case TENSION -> stake.multiply(BigDecimal.valueOf(0.55));
            case CLICHE -> stake.multiply(BigDecimal.valueOf(0.35));
            default -> BigDecimal.ZERO;
        };
    }

    private void ensureActive() {
        if (halted) throw new FamPauseException("Studio halted by guard " + MoonStudioConfig.ADDRESS_PAUSE_GUARD);
    }

    private void validateStake(BigDecimal stakeEth) {
        if (stakeEth.compareTo(MoonStudioConfig.MIN_STAKE_ETH) < 0
                || stakeEth.compareTo(MoonStudioConfig.MAX_STAKE_ETH) > 0) {
            throw new FamStakeException("FAM_STAKE", "Stake outside studio limits");
        }
    }

    static void validateAddr(String addr) {
        if (addr == null || !addr.startsWith("0x") || addr.length() != 42) {
            throw new FamComposeException("FAM_ADDR", "Invalid studio address format");
        }
    }

    public FamRoyaltyLedger getRoyalty() { return royalty; }
    public FamLeaderboard getLeaderboard() { return leaderboard; }
    public Map<String, WriterSeatProfile> getWriters() { return Collections.unmodifiableMap(writers); }
}

final class FamTrackResult {
    private final long trackId;
    private final HarmonyVerdict verdict;
    private final BigDecimal royaltyEth;
    private final String fairnessHash;
    private final int bpm;
    private final PitchClass tonic;

    FamTrackResult(long trackId, HarmonyVerdict verdict, BigDecimal royaltyEth, String fairnessHash, int bpm, PitchClass tonic) {
        this.trackId = trackId;
        this.verdict = verdict;
        this.royaltyEth = royaltyEth;
        this.fairnessHash = fairnessHash;
        this.bpm = bpm;
        this.tonic = tonic;
    }

    public long getTrackId() { return trackId; }
    public HarmonyVerdict getVerdict() { return verdict; }
    public BigDecimal getRoyaltyEth() { return royaltyEth; }
    public String getFairnessHash() { return fairnessHash; }
    public int getBpm() { return bpm; }
    public PitchClass getTonic() { return tonic; }
}

// ======================== Showcase bracket ========================

final class FamShowcaseMatch {
    final String writerA;
    final String writerB;
    int scoreA;
    int scoreB;

    FamShowcaseMatch(String writerA, String writerB) {
        this.writerA = writerA;
        this.writerB = writerB;
    }

    String leader() {
        if (scoreA > scoreB) return writerA;
        if (scoreB > scoreA) return writerB;
        return null;
    }
}

final class FamShowcase {
    private final List<FamShowcaseMatch> matches = new ArrayList<>();
    private final String venueAddr;

    FamShowcase(String venueAddr) {
        this.venueAddr = venueAddr;
    }

    void seedWriters(List<String> ids) {
        matches.clear();
        List<String> shuffled = new ArrayList<>(ids);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        for (int i = 0; i + 1 < shuffled.size(); i += 2) {
            matches.add(new FamShowcaseMatch(shuffled.get(i), shuffled.get(i + 1)));
        }
    }

    void recordWin(String writerId) {
        for (FamShowcaseMatch m : matches) {
            if (m.writerA.equals(writerId)) m.scoreA++;
            else if (m.writerB.equals(writerId)) m.scoreB++;
        }
    }

    List<FamShowcaseMatch> getMatches() { return Collections.unmodifiableList(matches); }
    public String getVenueAddr() { return venueAddr; }
}

// ======================== Chain adapter ========================

final class FamChainAdapter {
    private final ReleaseRail rail;
    private final String oracleAddr;
    private int virtualBlock;

    FamChainAdapter(ReleaseRail rail, String oracleAddr) {
        this.rail = rail;
        this.oracleAddr = oracleAddr;
        this.virtualBlock = 19_400_000 + rail.getChainId();
    }

    int confirmStake() {
        virtualBlock += rail.getConfirmBlocks();
        return virtualBlock;
    }

    String encodeReceipt(long trackId, HarmonyVerdict verdict) {
        return "fam:" + rail.getChainId() + ":" + trackId + ":" + verdict.getCode() + ":" + oracleAddr.substring(2, 10);
    }

    public ReleaseRail getRail() { return rail; }
}

// ======================== Analytics ========================

final class FamSessionAnalytics {
    private final Map<String, Integer> verdictCounts = new HashMap<>();
    private final Map<HookBetKind, Integer> hookHits = new EnumMap<>(HookBetKind.class);
    private BigDecimal totalVolume = BigDecimal.ZERO;
    private long tracks;

    void ingest(FamTrackResult result, BigDecimal stake) {
        tracks++;
        totalVolume = totalVolume.add(stake);
        verdictCounts.merge(result.getVerdict().name(), 1, Integer::sum);
    }

    void ingestHook(HookBetKind kind) {
        hookHits.merge(kind, 1, Integer::sum);
    }

    public long getTracks() { return tracks; }
    public BigDecimal getTotalVolume() { return totalVolume; }

    public Map<String, Integer> getVerdictCounts() {
        return Collections.unmodifiableMap(verdictCounts);
    }

    public double hitRate(HarmonyVerdict v) {
        if (tracks == 0) return 0;
        return verdictCounts.getOrDefault(v.name(), 0) / (double) tracks;
    }
}


// ======================== Progression reference ========================
final class FamProgressionMatrix {
    private FamProgressionMatrix() {}

    private static final Map<String, String> MAJOR = new HashMap<>();
    private static final Map<String, String> MINOR = new HashMap<>();
    private static final Map<String, String> MODAL = new HashMap<>();

    static {
        MAJOR.put("Cm1", "I");
        MAJOR.put("Cm2", "I");
        MAJOR.put("Cm3", "I");
        MAJOR.put("Cm4", "IV");
        MAJOR.put("Cm5", "V");
        MAJOR.put("Cm6", "V");
        MAJOR.put("Cm7", "V");
        MAJOR.put("Dm1", "I");
        MAJOR.put("Dm2", "I");
        MAJOR.put("Dm3", "I");
        MAJOR.put("Dm4", "IV");
        MAJOR.put("Dm5", "V");
        MAJOR.put("Dm6", "V");
        MAJOR.put("Dm7", "V");
        MAJOR.put("Em1", "I");
        MAJOR.put("Em2", "I");
        MAJOR.put("Em3", "I");
        MAJOR.put("Em4", "IV");
        MAJOR.put("Em5", "V");
        MAJOR.put("Em6", "V");
        MAJOR.put("Em7", "V");
        MAJOR.put("Fm1", "I");
        MAJOR.put("Fm2", "I");
        MAJOR.put("Fm3", "I");
        MAJOR.put("Fm4", "IV");
        MAJOR.put("Fm5", "V");
        MAJOR.put("Fm6", "V");
        MAJOR.put("Fm7", "V");
        MAJOR.put("Gm1", "I");
        MAJOR.put("Gm2", "I");
        MAJOR.put("Gm3", "I");
        MAJOR.put("Gm4", "IV");
        MAJOR.put("Gm5", "V");
        MAJOR.put("Gm6", "V");
        MAJOR.put("Gm7", "V");
        MAJOR.put("Am1", "I");
        MAJOR.put("Am2", "I");
        MAJOR.put("Am3", "I");
        MAJOR.put("Am4", "IV");
        MAJOR.put("Am5", "V");
        MAJOR.put("Am6", "V");
        MAJOR.put("Am7", "V");
        MAJOR.put("Bm1", "I");
        MAJOR.put("Bm2", "I");
        MAJOR.put("Bm3", "I");
        MAJOR.put("Bm4", "IV");
        MAJOR.put("Bm5", "V");
        MAJOR.put("Bm6", "V");
        MAJOR.put("Bm7", "V");
        MINOR.put("Amv1", "i");
        MINOR.put("Amv2", "i");
        MINOR.put("Amv3", "iv");
        MINOR.put("Amv4", "i");
        MINOR.put("Amv5", "i");
        MINOR.put("Amv6", "VI");
        MINOR.put("Amv7", "VI");
        MINOR.put("Dmv1", "i");
        MINOR.put("Dmv2", "i");
        MINOR.put("Dmv3", "iv");
        MINOR.put("Dmv4", "i");
        MINOR.put("Dmv5", "i");
        MINOR.put("Dmv6", "VI");
        MINOR.put("Dmv7", "VI");
        MINOR.put("Emv1", "i");
        MINOR.put("Emv2", "i");
        MINOR.put("Emv3", "iv");
        MINOR.put("Emv4", "i");
        MINOR.put("Emv5", "i");
        MINOR.put("Emv6", "VI");
        MINOR.put("Emv7", "VI");
        MINOR.put("Gmv1", "i");
        MINOR.put("Gmv2", "i");
        MINOR.put("Gmv3", "iv");
        MINOR.put("Gmv4", "i");
        MINOR.put("Gmv5", "i");
        MINOR.put("Gmv6", "VI");
        MINOR.put("Gmv7", "VI");
        MODAL.put("lyds1", "ii");
        MODAL.put("lyds2", "iii");
        MODAL.put("lyds3", "IV");
        MODAL.put("lyds4", "V");
        MODAL.put("lyds5", "vi");
        MODAL.put("lyds6", "vii");
        MODAL.put("lyds7", "I");
        MODAL.put("lyds8", "ii");
        MODAL.put("dors1", "ii");
        MODAL.put("dors2", "iii");
        MODAL.put("dors3", "IV");
        MODAL.put("dors4", "V");
        MODAL.put("dors5", "vi");
        MODAL.put("dors6", "vii");
        MODAL.put("dors7", "I");
        MODAL.put("dors8", "ii");
        MODAL.put("mixs1", "ii");
        MODAL.put("mixs2", "iii");
        MODAL.put("mixs3", "IV");
        MODAL.put("mixs4", "V");
        MODAL.put("mixs5", "vi");
        MODAL.put("mixs6", "vii");
        MODAL.put("mixs7", "I");
        MODAL.put("mixs8", "ii");
        MODAL.put("phrs1", "ii");
        MODAL.put("phrs2", "iii");
        MODAL.put("phrs3", "IV");
        MODAL.put("phrs4", "V");
        MODAL.put("phrs5", "vi");
        MODAL.put("phrs6", "vii");
        MODAL.put("phrs7", "I");
        MODAL.put("phrs8", "ii");
    }

    static String lookupMajor(String tonic, int step) {
        return MAJOR.getOrDefault(tonic + "m" + step, "I");
    }

    static String lookupMinor(String tonic, int step) {
        return MINOR.getOrDefault(tonic + "v" + step, "i");
    }

    static String lookupModal(String mode, int step) {
        return MODAL.getOrDefault(mode + "s" + step, "I");
    }

    static int matrixSize() { return MAJOR.size() + MINOR.size() + MODAL.size(); }
}

final class FamMotifCatalog {
    static final String MOTIF_0 = "moon_motif_0_" + Integer.toHexString(204811);
    static final String MOTIF_1 = "moon_motif_1_" + Integer.toHexString(213128);
    static final String MOTIF_2 = "moon_motif_2_" + Integer.toHexString(221445);
    static final String MOTIF_3 = "moon_motif_3_" + Integer.toHexString(229762);
    static final String MOTIF_4 = "moon_motif_4_" + Integer.toHexString(238079);
    static final String MOTIF_5 = "moon_motif_5_" + Integer.toHexString(246396);
    static final String MOTIF_6 = "moon_motif_6_" + Integer.toHexString(254713);
    static final String MOTIF_7 = "moon_motif_7_" + Integer.toHexString(263030);
    static final String MOTIF_8 = "moon_motif_8_" + Integer.toHexString(271347);
    static final String MOTIF_9 = "moon_motif_9_" + Integer.toHexString(279664);
    static final String MOTIF_10 = "moon_motif_10_" + Integer.toHexString(287981);
    static final String MOTIF_11 = "moon_motif_11_" + Integer.toHexString(296298);
    static final String MOTIF_12 = "moon_motif_12_" + Integer.toHexString(304615);
    static final String MOTIF_13 = "moon_motif_13_" + Integer.toHexString(312932);
    static final String MOTIF_14 = "moon_motif_14_" + Integer.toHexString(321249);
    static final String MOTIF_15 = "moon_motif_15_" + Integer.toHexString(329566);
    static final String MOTIF_16 = "moon_motif_16_" + Integer.toHexString(337883);
    static final String MOTIF_17 = "moon_motif_17_" + Integer.toHexString(346200);
    static final String MOTIF_18 = "moon_motif_18_" + Integer.toHexString(354517);
    static final String MOTIF_19 = "moon_motif_19_" + Integer.toHexString(362834);
    static final String MOTIF_20 = "moon_motif_20_" + Integer.toHexString(371151);
    static final String MOTIF_21 = "moon_motif_21_" + Integer.toHexString(379468);
    static final String MOTIF_22 = "moon_motif_22_" + Integer.toHexString(387785);
    static final String MOTIF_23 = "moon_motif_23_" + Integer.toHexString(396102);
    static final String MOTIF_24 = "moon_motif_24_" + Integer.toHexString(404419);
    static final String MOTIF_25 = "moon_motif_25_" + Integer.toHexString(412736);
    static final String MOTIF_26 = "moon_motif_26_" + Integer.toHexString(421053);
    static final String MOTIF_27 = "moon_motif_27_" + Integer.toHexString(429370);
    static final String MOTIF_28 = "moon_motif_28_" + Integer.toHexString(437687);
    static final String MOTIF_29 = "moon_motif_29_" + Integer.toHexString(446004);
    static final String MOTIF_30 = "moon_motif_30_" + Integer.toHexString(454321);
    static final String MOTIF_31 = "moon_motif_31_" + Integer.toHexString(462638);
    static final String MOTIF_32 = "moon_motif_32_" + Integer.toHexString(470955);
    static final String MOTIF_33 = "moon_motif_33_" + Integer.toHexString(479272);
    static final String MOTIF_34 = "moon_motif_34_" + Integer.toHexString(487589);
    static final String MOTIF_35 = "moon_motif_35_" + Integer.toHexString(495906);
    static final String MOTIF_36 = "moon_motif_36_" + Integer.toHexString(504223);
    static final String MOTIF_37 = "moon_motif_37_" + Integer.toHexString(512540);
    static final String MOTIF_38 = "moon_motif_38_" + Integer.toHexString(520857);
    static final String MOTIF_39 = "moon_motif_39_" + Integer.toHexString(529174);
    static final String MOTIF_40 = "moon_motif_40_" + Integer.toHexString(537491);
    static final String MOTIF_41 = "moon_motif_41_" + Integer.toHexString(545808);
    static final String MOTIF_42 = "moon_motif_42_" + Integer.toHexString(554125);
    static final String MOTIF_43 = "moon_motif_43_" + Integer.toHexString(562442);
    static final String MOTIF_44 = "moon_motif_44_" + Integer.toHexString(570759);
    static final String MOTIF_45 = "moon_motif_45_" + Integer.toHexString(579076);
    static final String MOTIF_46 = "moon_motif_46_" + Integer.toHexString(587393);
    static final String MOTIF_47 = "moon_motif_47_" + Integer.toHexString(595710);

    static List<String> allMotifs() {
        List<String> t = new ArrayList<>();
        t.add(MOTIF_0);
        t.add(MOTIF_1);
        t.add(MOTIF_2);
        t.add(MOTIF_3);
        t.add(MOTIF_4);
        t.add(MOTIF_5);
        t.add(MOTIF_6);
        t.add(MOTIF_7);
        t.add(MOTIF_8);
        t.add(MOTIF_9);
        t.add(MOTIF_10);
        t.add(MOTIF_11);
        t.add(MOTIF_12);
        t.add(MOTIF_13);
        t.add(MOTIF_14);
        t.add(MOTIF_15);
        t.add(MOTIF_16);
        t.add(MOTIF_17);
        t.add(MOTIF_18);
        t.add(MOTIF_19);
        t.add(MOTIF_20);
        t.add(MOTIF_21);
        t.add(MOTIF_22);
        t.add(MOTIF_23);
        t.add(MOTIF_24);
        t.add(MOTIF_25);
        t.add(MOTIF_26);
        t.add(MOTIF_27);
        t.add(MOTIF_28);
        t.add(MOTIF_29);
        t.add(MOTIF_30);
        t.add(MOTIF_31);
        t.add(MOTIF_32);
        t.add(MOTIF_33);
        t.add(MOTIF_34);
        t.add(MOTIF_35);
        t.add(MOTIF_36);
        t.add(MOTIF_37);
        t.add(MOTIF_38);
        t.add(MOTIF_39);
        t.add(MOTIF_40);
        t.add(MOTIF_41);
        t.add(MOTIF_42);
        t.add(MOTIF_43);
        t.add(MOTIF_44);
        t.add(MOTIF_45);
        t.add(MOTIF_46);
        t.add(MOTIF_47);
        return t;
    }
}

final class FamMonteCarloRunner {
    private final FredAgainMoon studio;
    private final int iterations;

    FamMonteCarloRunner(FredAgainMoon studio, int iterations) {
        this.studio = studio;
        this.iterations = iterations;
    }

    FamSessionAnalytics run(List<String> writers, BigDecimal stake) {
        for (int i = 0; i < iterations; i++) {
            String wid = writers.get(i % writers.size());
            HookBetKind hook = HookBetKind.values()[i % HookBetKind.values().length];
            studio.compose(wid, stake, List.of(hook));
        }
        return studio.analytics();
    }
}

final class FamHookPremiumLane {
    private final BigDecimal premiumRate;

    FamHookPremiumLane() {
        this.premiumRate = BigDecimal.valueOf(MoonStudioConfig.HOOK_BONUS_BPS)
                .divide(BigDecimal.valueOf(MoonStudioConfig.BPS_DENOM), 6, RoundingMode.HALF_UP);
    }

    BigDecimal premium(BigDecimal baseStake) {
        return baseStake.multiply(premiumRate);
    }

    boolean sketchHasHook(SongSketch sketch) {
        return sketch.hookDensity() >= 2;
    }

    BigDecimal resolve(BigDecimal premium, boolean landed) {
        if (!landed) return BigDecimal.ZERO;
        return premium.multiply(BigDecimal.valueOf(2.8));
    }
}

final class FamStemSplitController {
    private final List<StemLane> activeLanes = new ArrayList<>();

    List<StemLane> openSplit(SongSketch sketch) {
        if (sketch.getBlocks().isEmpty()) throw new FamComposeException("FAM_STEM", "Empty sketch");
        if (activeLanes.size() >= StemLane.values().length) {
            throw new FamComposeException("FAM_STEM_CAP", "Max stem lanes reached");
        }
        for (StemLane lane : StemLane.values()) {
            if (!activeLanes.contains(lane)) activeLanes.add(lane);
        }
        return List.copyOf(activeLanes);
    }

    void clear() { activeLanes.clear(); }
    int activeCount() { return activeLanes.size(); }
}

final class FamRailFeeTable {
    static final int FEE_MAINNET_GWEI = 152;
    static final int FEE_BASE_GWEI = 100;
    static final int FEE_ARBITRUM_GWEI = 106;
    static final int FEE_OPTIMISM_GWEI = 105;
    static final int FEE_POLYGON_GWEI = 133;

    static int feeFor(ReleaseRail rail) {
        return switch (rail) {
            case MAINNET -> FEE_MAINNET_GWEI;
            case BASE -> FEE_BASE_GWEI;
            case ARBITRUM -> FEE_ARBITRUM_GWEI;
            case OPTIMISM -> FEE_OPTIMISM_GWEI;
            case POLYGON -> FEE_POLYGON_GWEI;
        };
    }
}

final class FamHistorySerializer {
    static String encodeTrack(FamTrackResult r, String writerId) {
        return writerId + "|" + r.getTrackId() + "|" + r.getVerdict().name() + "|" + r.getRoyaltyEth().toPlainString();
    }

    static Map<String, String> decodeTrack(String line) {
        String[] p = line.split("\\|");
        if (p.length < 4) return Map.of();
        Map<String, String> m = new HashMap<>();
        m.put("writer", p[0]);
        m.put("track", p[1]);
        m.put("verdict", p[2]);
        m.put("royalty", p[3]);
        return m;
    }
}

final class FamStudioVariant1 {
    private final int variantId = 1;
    private final BigDecimal minStake;
    private final BigDecimal maxStake;
    private final int defaultBpm;

    FamStudioVariant1() {
        this.minStake = MoonStudioConfig.MIN_STAKE_ETH.multiply(BigDecimal.valueOf(1.11));
        this.maxStake = MoonStudioConfig.MAX_STAKE_ETH.multiply(BigDecimal.valueOf(0.56));
        this.defaultBpm = MoonStudioConfig.MIN_BPM + 7;
    }

    public int getVariantId() { return variantId; }
    public BigDecimal getMinStake() { return minStake; }
    public BigDecimal getMaxStake() { return maxStake; }
    public int getDefaultBpm() { return defaultBpm; }
    public boolean accepts(BigDecimal stake) {
        return stake.compareTo(minStake) >= 0 && stake.compareTo(maxStake) <= 0;
    }
}

final class FamStudioVariant2 {
    private final int variantId = 2;
    private final BigDecimal minStake;
    private final BigDecimal maxStake;
    private final int defaultBpm;

    FamStudioVariant2() {
        this.minStake = MoonStudioConfig.MIN_STAKE_ETH.multiply(BigDecimal.valueOf(1.22));
        this.maxStake = MoonStudioConfig.MAX_STAKE_ETH.multiply(BigDecimal.valueOf(0.6));
        this.defaultBpm = MoonStudioConfig.MIN_BPM + 14;
    }

    public int getVariantId() { return variantId; }
    public BigDecimal getMinStake() { return minStake; }
    public BigDecimal getMaxStake() { return maxStake; }
    public int getDefaultBpm() { return defaultBpm; }
    public boolean accepts(BigDecimal stake) {
        return stake.compareTo(minStake) >= 0 && stake.compareTo(maxStake) <= 0;
    }
}

final class FamStudioVariant3 {
    private final int variantId = 3;
    private final BigDecimal minStake;
    private final BigDecimal maxStake;
    private final int defaultBpm;

    FamStudioVariant3() {
        this.minStake = MoonStudioConfig.MIN_STAKE_ETH.multiply(BigDecimal.valueOf(1.33));
        this.maxStake = MoonStudioConfig.MAX_STAKE_ETH.multiply(BigDecimal.valueOf(0.64));
        this.defaultBpm = MoonStudioConfig.MIN_BPM + 21;
    }

    public int getVariantId() { return variantId; }
    public BigDecimal getMinStake() { return minStake; }
    public BigDecimal getMaxStake() { return maxStake; }
    public int getDefaultBpm() { return defaultBpm; }
    public boolean accepts(BigDecimal stake) {
        return stake.compareTo(minStake) >= 0 && stake.compareTo(maxStake) <= 0;
    }
}

final class FamStudioVariant4 {
    private final int variantId = 4;
    private final BigDecimal minStake;
    private final BigDecimal maxStake;
    private final int defaultBpm;

    FamStudioVariant4() {
        this.minStake = MoonStudioConfig.MIN_STAKE_ETH.multiply(BigDecimal.valueOf(1.44));
        this.maxStake = MoonStudioConfig.MAX_STAKE_ETH.multiply(BigDecimal.valueOf(0.68));
        this.defaultBpm = MoonStudioConfig.MIN_BPM + 28;
    }

    public int getVariantId() { return variantId; }
    public BigDecimal getMinStake() { return minStake; }
    public BigDecimal getMaxStake() { return maxStake; }
    public int getDefaultBpm() { return defaultBpm; }
    public boolean accepts(BigDecimal stake) {
        return stake.compareTo(minStake) >= 0 && stake.compareTo(maxStake) <= 0;
    }
}

final class FamStudioVariant5 {
    private final int variantId = 5;
    private final BigDecimal minStake;
    private final BigDecimal maxStake;
    private final int defaultBpm;

    FamStudioVariant5() {
        this.minStake = MoonStudioConfig.MIN_STAKE_ETH.multiply(BigDecimal.valueOf(1.55));
        this.maxStake = MoonStudioConfig.MAX_STAKE_ETH.multiply(BigDecimal.valueOf(0.72));
        this.defaultBpm = MoonStudioConfig.MIN_BPM + 35;
    }

    public int getVariantId() { return variantId; }
    public BigDecimal getMinStake() { return minStake; }
    public BigDecimal getMaxStake() { return maxStake; }
    public int getDefaultBpm() { return defaultBpm; }
    public boolean accepts(BigDecimal stake) {
        return stake.compareTo(minStake) >= 0 && stake.compareTo(maxStake) <= 0;
    }
}

final class FamStudioVariant6 {
    private final int variantId = 6;
    private final BigDecimal minStake;
    private final BigDecimal maxStake;
    private final int defaultBpm;

    FamStudioVariant6() {
        this.minStake = MoonStudioConfig.MIN_STAKE_ETH.multiply(BigDecimal.valueOf(1.6600000000000001));
        this.maxStake = MoonStudioConfig.MAX_STAKE_ETH.multiply(BigDecimal.valueOf(0.76));
        this.defaultBpm = MoonStudioConfig.MIN_BPM + 42;
    }

    public int getVariantId() { return variantId; }
    public BigDecimal getMinStake() { return minStake; }
    public BigDecimal getMaxStake() { return maxStake; }
    public int getDefaultBpm() { return defaultBpm; }
    public boolean accepts(BigDecimal stake) {
        return stake.compareTo(minStake) >= 0 && stake.compareTo(maxStake) <= 0;
    }
}

final class FamStudioVariant7 {
    private final int variantId = 7;
    private final BigDecimal minStake;
    private final BigDecimal maxStake;
    private final int defaultBpm;

    FamStudioVariant7() {
        this.minStake = MoonStudioConfig.MIN_STAKE_ETH.multiply(BigDecimal.valueOf(1.77));
        this.maxStake = MoonStudioConfig.MAX_STAKE_ETH.multiply(BigDecimal.valueOf(0.8));
        this.defaultBpm = MoonStudioConfig.MIN_BPM + 49;
    }

    public int getVariantId() { return variantId; }
    public BigDecimal getMinStake() { return minStake; }
    public BigDecimal getMaxStake() { return maxStake; }
    public int getDefaultBpm() { return defaultBpm; }
    public boolean accepts(BigDecimal stake) {
        return stake.compareTo(minStake) >= 0 && stake.compareTo(maxStake) <= 0;
    }
}

final class FamStudioVariant8 {
    private final int variantId = 8;
    private final BigDecimal minStake;
    private final BigDecimal maxStake;
    private final int defaultBpm;

    FamStudioVariant8() {
        this.minStake = MoonStudioConfig.MIN_STAKE_ETH.multiply(BigDecimal.valueOf(1.88));
        this.maxStake = MoonStudioConfig.MAX_STAKE_ETH.multiply(BigDecimal.valueOf(0.8400000000000001));
        this.defaultBpm = MoonStudioConfig.MIN_BPM + 56;
    }

    public int getVariantId() { return variantId; }
    public BigDecimal getMinStake() { return minStake; }
    public BigDecimal getMaxStake() { return maxStake; }
    public int getDefaultBpm() { return defaultBpm; }
    public boolean accepts(BigDecimal stake) {
        return stake.compareTo(minStake) >= 0 && stake.compareTo(maxStake) <= 0;
    }
}

final class FamStudioVariant9 {
    private final int variantId = 9;
    private final BigDecimal minStake;
    private final BigDecimal maxStake;
    private final int defaultBpm;

    FamStudioVariant9() {
        this.minStake = MoonStudioConfig.MIN_STAKE_ETH.multiply(BigDecimal.valueOf(1.99));
        this.maxStake = MoonStudioConfig.MAX_STAKE_ETH.multiply(BigDecimal.valueOf(0.88));
        this.defaultBpm = MoonStudioConfig.MIN_BPM + 63;
    }

    public int getVariantId() { return variantId; }
    public BigDecimal getMinStake() { return minStake; }
    public BigDecimal getMaxStake() { return maxStake; }
    public int getDefaultBpm() { return defaultBpm; }
    public boolean accepts(BigDecimal stake) {
        return stake.compareTo(minStake) >= 0 && stake.compareTo(maxStake) <= 0;
    }
}

final class FamStudioVariant10 {
    private final int variantId = 10;
    private final BigDecimal minStake;
    private final BigDecimal maxStake;
    private final int defaultBpm;

    FamStudioVariant10() {
        this.minStake = MoonStudioConfig.MIN_STAKE_ETH.multiply(BigDecimal.valueOf(2.1));
        this.maxStake = MoonStudioConfig.MAX_STAKE_ETH.multiply(BigDecimal.valueOf(0.92));
        this.defaultBpm = MoonStudioConfig.MIN_BPM + 70;
    }

    public int getVariantId() { return variantId; }
    public BigDecimal getMinStake() { return minStake; }
    public BigDecimal getMaxStake() { return maxStake; }
    public int getDefaultBpm() { return defaultBpm; }
    public boolean accepts(BigDecimal stake) {
        return stake.compareTo(minStake) >= 0 && stake.compareTo(maxStake) <= 0;
    }
}

final class FamLyricTemplateBank {
    private static final Map<String, String> VERSE_TEMPLATES = new HashMap<>();
    private static final Map<String, String> CHORUS_TEMPLATES = new HashMap<>();

    static {
        VERSE_TEMPLATES.put("v0", "verse_seed_0_again_moon");
        VERSE_TEMPLATES.put("v1", "verse_seed_1_again_moon");
        VERSE_TEMPLATES.put("v2", "verse_seed_2_again_moon");
        VERSE_TEMPLATES.put("v3", "verse_seed_3_again_moon");
        VERSE_TEMPLATES.put("v4", "verse_seed_4_again_moon");
        VERSE_TEMPLATES.put("v5", "verse_seed_5_again_moon");
        VERSE_TEMPLATES.put("v6", "verse_seed_6_again_moon");
        VERSE_TEMPLATES.put("v7", "verse_seed_7_again_moon");
        VERSE_TEMPLATES.put("v8", "verse_seed_8_again_moon");
        VERSE_TEMPLATES.put("v9", "verse_seed_9_again_moon");
        VERSE_TEMPLATES.put("v10", "verse_seed_10_again_moon");
        VERSE_TEMPLATES.put("v11", "verse_seed_11_again_moon");
        VERSE_TEMPLATES.put("v12", "verse_seed_12_again_moon");
        VERSE_TEMPLATES.put("v13", "verse_seed_13_again_moon");
        VERSE_TEMPLATES.put("v14", "verse_seed_14_again_moon");
        VERSE_TEMPLATES.put("v15", "verse_seed_15_again_moon");
        VERSE_TEMPLATES.put("v16", "verse_seed_16_again_moon");
        VERSE_TEMPLATES.put("v17", "verse_seed_17_again_moon");
        VERSE_TEMPLATES.put("v18", "verse_seed_18_again_moon");
        VERSE_TEMPLATES.put("v19", "verse_seed_19_again_moon");
        VERSE_TEMPLATES.put("v20", "verse_seed_20_again_moon");
        VERSE_TEMPLATES.put("v21", "verse_seed_21_again_moon");
        VERSE_TEMPLATES.put("v22", "verse_seed_22_again_moon");
        VERSE_TEMPLATES.put("v23", "verse_seed_23_again_moon");
        VERSE_TEMPLATES.put("v24", "verse_seed_24_again_moon");
        VERSE_TEMPLATES.put("v25", "verse_seed_25_again_moon");
        VERSE_TEMPLATES.put("v26", "verse_seed_26_again_moon");
        VERSE_TEMPLATES.put("v27", "verse_seed_27_again_moon");
        VERSE_TEMPLATES.put("v28", "verse_seed_28_again_moon");
        VERSE_TEMPLATES.put("v29", "verse_seed_29_again_moon");
        VERSE_TEMPLATES.put("v30", "verse_seed_30_again_moon");
        VERSE_TEMPLATES.put("v31", "verse_seed_31_again_moon");
        VERSE_TEMPLATES.put("v32", "verse_seed_32_again_moon");
        VERSE_TEMPLATES.put("v33", "verse_seed_33_again_moon");
        VERSE_TEMPLATES.put("v34", "verse_seed_34_again_moon");
        VERSE_TEMPLATES.put("v35", "verse_seed_35_again_moon");
        VERSE_TEMPLATES.put("v36", "verse_seed_36_again_moon");
        VERSE_TEMPLATES.put("v37", "verse_seed_37_again_moon");
        VERSE_TEMPLATES.put("v38", "verse_seed_38_again_moon");
        VERSE_TEMPLATES.put("v39", "verse_seed_39_again_moon");
        VERSE_TEMPLATES.put("v40", "verse_seed_40_again_moon");
        VERSE_TEMPLATES.put("v41", "verse_seed_41_again_moon");
        VERSE_TEMPLATES.put("v42", "verse_seed_42_again_moon");
        VERSE_TEMPLATES.put("v43", "verse_seed_43_again_moon");
        VERSE_TEMPLATES.put("v44", "verse_seed_44_again_moon");
        VERSE_TEMPLATES.put("v45", "verse_seed_45_again_moon");
        VERSE_TEMPLATES.put("v46", "verse_seed_46_again_moon");
        VERSE_TEMPLATES.put("v47", "verse_seed_47_again_moon");
        VERSE_TEMPLATES.put("v48", "verse_seed_48_again_moon");
        VERSE_TEMPLATES.put("v49", "verse_seed_49_again_moon");
        VERSE_TEMPLATES.put("v50", "verse_seed_50_again_moon");
        VERSE_TEMPLATES.put("v51", "verse_seed_51_again_moon");
        VERSE_TEMPLATES.put("v52", "verse_seed_52_again_moon");
        VERSE_TEMPLATES.put("v53", "verse_seed_53_again_moon");
        VERSE_TEMPLATES.put("v54", "verse_seed_54_again_moon");
        VERSE_TEMPLATES.put("v55", "verse_seed_55_again_moon");
        VERSE_TEMPLATES.put("v56", "verse_seed_56_again_moon");
        VERSE_TEMPLATES.put("v57", "verse_seed_57_again_moon");
        VERSE_TEMPLATES.put("v58", "verse_seed_58_again_moon");
        VERSE_TEMPLATES.put("v59", "verse_seed_59_again_moon");
        CHORUS_TEMPLATES.put("c0", "CHORUS_HOOK_0_LUNAR");
        CHORUS_TEMPLATES.put("c1", "CHORUS_HOOK_1_LUNAR");
        CHORUS_TEMPLATES.put("c2", "CHORUS_HOOK_2_LUNAR");
        CHORUS_TEMPLATES.put("c3", "CHORUS_HOOK_3_LUNAR");
        CHORUS_TEMPLATES.put("c4", "CHORUS_HOOK_4_LUNAR");
        CHORUS_TEMPLATES.put("c5", "CHORUS_HOOK_5_LUNAR");
        CHORUS_TEMPLATES.put("c6", "CHORUS_HOOK_6_LUNAR");
        CHORUS_TEMPLATES.put("c7", "CHORUS_HOOK_7_LUNAR");
        CHORUS_TEMPLATES.put("c8", "CHORUS_HOOK_8_LUNAR");
        CHORUS_TEMPLATES.put("c9", "CHORUS_HOOK_9_LUNAR");
        CHORUS_TEMPLATES.put("c10", "CHORUS_HOOK_10_LUNAR");
        CHORUS_TEMPLATES.put("c11", "CHORUS_HOOK_11_LUNAR");
        CHORUS_TEMPLATES.put("c12", "CHORUS_HOOK_12_LUNAR");
        CHORUS_TEMPLATES.put("c13", "CHORUS_HOOK_13_LUNAR");
        CHORUS_TEMPLATES.put("c14", "CHORUS_HOOK_14_LUNAR");
        CHORUS_TEMPLATES.put("c15", "CHORUS_HOOK_15_LUNAR");
        CHORUS_TEMPLATES.put("c16", "CHORUS_HOOK_16_LUNAR");
        CHORUS_TEMPLATES.put("c17", "CHORUS_HOOK_17_LUNAR");
        CHORUS_TEMPLATES.put("c18", "CHORUS_HOOK_18_LUNAR");
        CHORUS_TEMPLATES.put("c19", "CHORUS_HOOK_19_LUNAR");
        CHORUS_TEMPLATES.put("c20", "CHORUS_HOOK_20_LUNAR");
        CHORUS_TEMPLATES.put("c21", "CHORUS_HOOK_21_LUNAR");
        CHORUS_TEMPLATES.put("c22", "CHORUS_HOOK_22_LUNAR");
        CHORUS_TEMPLATES.put("c23", "CHORUS_HOOK_23_LUNAR");
        CHORUS_TEMPLATES.put("c24", "CHORUS_HOOK_24_LUNAR");
        CHORUS_TEMPLATES.put("c25", "CHORUS_HOOK_25_LUNAR");
        CHORUS_TEMPLATES.put("c26", "CHORUS_HOOK_26_LUNAR");
        CHORUS_TEMPLATES.put("c27", "CHORUS_HOOK_27_LUNAR");
        CHORUS_TEMPLATES.put("c28", "CHORUS_HOOK_28_LUNAR");
        CHORUS_TEMPLATES.put("c29", "CHORUS_HOOK_29_LUNAR");
        CHORUS_TEMPLATES.put("c30", "CHORUS_HOOK_30_LUNAR");
        CHORUS_TEMPLATES.put("c31", "CHORUS_HOOK_31_LUNAR");
        CHORUS_TEMPLATES.put("c32", "CHORUS_HOOK_32_LUNAR");
        CHORUS_TEMPLATES.put("c33", "CHORUS_HOOK_33_LUNAR");
        CHORUS_TEMPLATES.put("c34", "CHORUS_HOOK_34_LUNAR");
        CHORUS_TEMPLATES.put("c35", "CHORUS_HOOK_35_LUNAR");
        CHORUS_TEMPLATES.put("c36", "CHORUS_HOOK_36_LUNAR");
        CHORUS_TEMPLATES.put("c37", "CHORUS_HOOK_37_LUNAR");
        CHORUS_TEMPLATES.put("c38", "CHORUS_HOOK_38_LUNAR");
        CHORUS_TEMPLATES.put("c39", "CHORUS_HOOK_39_LUNAR");
    }

    static String verse(int idx) {
        return VERSE_TEMPLATES.getOrDefault("v" + idx, "default_verse");
    }

    static String chorus(int idx) {
        return CHORUS_TEMPLATES.getOrDefault("c" + idx, "DEFAULT_CHORUS");
    }
}

final class FamBpmLadder {
    private static final int[] RUNG = {72, 75, 78, 81, 84, 87, 90, 93, 96, 99, 102, 105, 108, 111, 114, 117, 120, 123, 126, 129, 132, 135, 138, 141, 144, 147, 150, 153, 156, 159, 162, 165, 168, 171, 174, 177};

    static int nearest(int target) {
        int best = RUNG[0];
        int diff = Integer.MAX_VALUE;
        for (int b : RUNG) {
            int d = Math.abs(target - b);
            if (d < diff) { diff = d; best = b; }
        }
        return best;
    }
}

final class FamReplCommands {
    static final String CMD_COMPOSE = "compose";
    static final String CMD_REGISTER = "register";
    static final String CMD_TOP = "top";
    static final String CMD_AUDIT = "audit";
    static final String CMD_HALT = "halt";
    static final String CMD_RESUME = "resume";
    static final String CMD_SHOWCASE = "showcase";
    static final String CMD_MOTIFS = "motifs";

    static boolean dispatch(String raw, FredAgainMoon studio) {
        if (raw == null || raw.isBlank()) return false;
        String[] parts = raw.trim().split("\\s+");
        String op = parts[0].toLowerCase(Locale.ROOT);
        return switch (op) {
            case "compose" -> {
                if (parts.length < 3) yield false;
                BigDecimal w = new BigDecimal(parts[2]);
                FamTrackResult r = studio.compose(parts[1], w);
                System.out.println(r.getVerdict() + " " + r.getRoyaltyEth());
                yield true;
            }
            case "top" -> {
                studio.topWriters(10).forEach(e -> System.out.println(e.writerId + " " + e.netEth));
                yield true;
            }
            case "audit" -> {
                studio.royalty().getAuditTail(12).forEach(System.out::println);
                yield true;
            }
            default -> false;
        };
    }
}

final class FamDegreeTableIONIAN {
    private FamDegreeTableIONIAN() {}
    static final int DEG_0 = 3;
    static final int DEG_1 = 10;
    static final int DEG_2 = 5;
    static final int DEG_3 = 0;
    static final int DEG_4 = 7;
    static final int DEG_5 = 2;
    static final int DEG_6 = 9;
    static final int DEG_7 = 4;
    static final int DEG_8 = 11;
    static final int DEG_9 = 6;
    static final int DEG_10 = 1;
    static final int DEG_11 = 8;
    static int size() { return 12; }
}

final class FamDegreeTableDORIAN {
    private FamDegreeTableDORIAN() {}
    static final int DEG_0 = 10;
    static final int DEG_1 = 5;
    static final int DEG_2 = 0;
    static final int DEG_3 = 7;
    static final int DEG_4 = 2;
    static final int DEG_5 = 9;
    static final int DEG_6 = 4;
    static final int DEG_7 = 11;
    static final int DEG_8 = 6;
    static final int DEG_9 = 1;
    static final int DEG_10 = 8;
    static final int DEG_11 = 3;
    static int size() { return 12; }
}

final class FamDegreeTableMIXOLYDIAN {
    private FamDegreeTableMIXOLYDIAN() {}
    static final int DEG_0 = 3;
    static final int DEG_1 = 10;
    static final int DEG_2 = 5;
    static final int DEG_3 = 0;
    static final int DEG_4 = 7;
    static final int DEG_5 = 2;
    static final int DEG_6 = 9;
    static final int DEG_7 = 4;
    static final int DEG_8 = 11;
    static final int DEG_9 = 6;
    static final int DEG_10 = 1;
    static final int DEG_11 = 8;
    static int size() { return 12; }
}

final class FamDegreeTableAEOLIAN {
    private FamDegreeTableAEOLIAN() {}
    static final int DEG_0 = 2;
    static final int DEG_1 = 9;
    static final int DEG_2 = 4;
    static final int DEG_3 = 11;
    static final int DEG_4 = 6;
    static final int DEG_5 = 1;
    static final int DEG_6 = 8;
    static final int DEG_7 = 3;
    static final int DEG_8 = 10;
    static final int DEG_9 = 5;
    static final int DEG_10 = 0;
    static final int DEG_11 = 7;
    static int size() { return 12; }
