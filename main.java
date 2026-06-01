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
