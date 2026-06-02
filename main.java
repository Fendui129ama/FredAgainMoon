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
