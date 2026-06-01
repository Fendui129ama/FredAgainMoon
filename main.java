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
