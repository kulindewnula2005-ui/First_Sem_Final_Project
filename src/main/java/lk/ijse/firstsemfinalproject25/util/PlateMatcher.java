package lk.ijse.firstsemfinalproject25.util;

import lk.ijse.firstsemfinalproject25.dto.BikeDTO;
import lk.ijse.firstsemfinalproject25.model.BikeModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns raw OCR text into plate candidates and finds the registered bikes they could belong to. */
public final class PlateMatcher {

    /** A registered bike that fits what was read. distance 0 = exact, 1-2 = a few characters differ. */
    public record Match(BikeDTO bike, String plate, int distance) { }

    private static final Pattern MODERN =
            Pattern.compile("^(WP|CP|SP|NP|EP|NW|NC|UP|SG)?[A-Z]{2,3}[0-9]{4}$");
    private static final Pattern OLD = Pattern.compile("^[0-9]{6,7}$");

    private PlateMatcher() { }

    /* ======================= text -> candidates ======================= */
    /** Plate-like strings found in the OCR text, best looking first. */
    public static List<String> candidates(String ocrText) {
        List<String> tokens = new ArrayList<>();
        if (ocrText != null) {
            for (String line : ocrText.split("\\R")) {
                for (String token : line.toUpperCase().split("[^A-Z0-9]+")) {
                    if (!token.isEmpty()) {
                        tokens.add(token);
                    }
                }
            }
        }

        // up to three neighbouring pieces glued together ("WP", "CAB", "1234" -> WPCAB1234)
        Set<String> found = new LinkedHashSet<>();
        for (int i = 0; i < tokens.size(); i++) {
            StringBuilder joined = new StringBuilder();
            for (int len = 1; len <= 3 && i + len <= tokens.size(); len++) {
                joined.append(tokens.get(i + len - 1));
                String candidate = joined.toString();
                if (plausible(candidate)) {
                    found.add(candidate);
                }
            }
        }

        List<String> list = new ArrayList<>(found);
        list.sort((a, b) -> {
            int byQuality = Integer.compare(quality(b), quality(a));
            return byQuality != 0 ? byQuality : Integer.compare(b.length(), a.length());
        });
        return list;
    }

    private static boolean plausible(String candidate) {
        if (candidate.length() < 5 || candidate.length() > 10) {
            return false;
        }
        String f = fix(candidate);
        if (!f.matches("[A-Z0-9]*[0-9]{4}")) {
            return false;
        }
        boolean hasLetter = f.chars().anyMatch(Character::isLetter);
        return hasLetter || (f.length() >= 6 && f.length() <= 7);
    }

    /** 3 = looks like a current plate, 2 = old style, 1 = something else. */
    public static int quality(String candidate) {
        String f = fix(candidate);
        if (MODERN.matcher(f).matches()) {
            return 3;
        }
        if (OLD.matcher(f).matches()) {
            return 2;
        }
        return 1;
    }

    /* ======================= look-alike characters ======================= */
    /**
     * Plates are letters followed by four digits, so a letter among the last four characters is
     * really a digit (O->0, I->1, B->8 ...) and a digit among the leading letters is really a letter.
     */
    public static String fix(String plate) {
        String p = plate == null ? "" : plate.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (p.length() < 5) {
            return p;
        }
        String head = p.substring(0, p.length() - 4);
        String tail = p.substring(p.length() - 4);

        int letters = 0;
        int digits = 0;
        for (char c : head.toCharArray()) {
            if (Character.isDigit(c)) {
                digits++;
            } else {
                letters++;
            }
        }
        boolean fixHead = letters > digits;   // old plates like 19-1234 keep their digits

        StringBuilder out = new StringBuilder();
        for (char c : head.toCharArray()) {
            out.append(fixHead && Character.isDigit(c) ? toLetter(c) : c);
        }
        for (char c : tail.toCharArray()) {
            out.append(toDigit(c));
        }
        return out.toString();
    }

    private static char toDigit(char c) {
        return switch (c) {
            case 'O', 'Q', 'D' -> '0';
            case 'I', 'L' -> '1';
            case 'Z' -> '2';
            case 'S' -> '5';
            case 'G' -> '6';
            case 'B' -> '8';
            default -> c;
        };
    }

    private static char toLetter(char c) {
        return switch (c) {
            case '0' -> 'O';
            case '1' -> 'I';
            case '2' -> 'Z';
            case '5' -> 'S';
            case '6' -> 'G';
            case '8' -> 'B';
            default -> c;
        };
    }

    /** WPCAB1234 -> "WP CAB-1234". */
    public static String pretty(String candidate) {
        String f = fix(candidate);
        Matcher m = Pattern.compile("^(WP|CP|SP|NP|EP|NW|NC|UP|SG)?([A-Z]{2,3})([0-9]{4})$").matcher(f);
        if (m.matches()) {
            return (m.group(1) == null ? "" : m.group(1) + " ") + m.group(2) + "-" + m.group(3);
        }
        if (OLD.matcher(f).matches()) {
            return f.substring(0, f.length() - 4) + "-" + f.substring(f.length() - 4);
        }
        return f;
    }

    /* ======================= candidates -> registered bikes ======================= */
    /** Registered bikes that fit any candidate, closest first (at most 5). */
    public static List<Match> rank(List<String> candidates, List<BikeDTO> bikes) {
        Map<String, Match> bestPerBike = new HashMap<>();
        for (String candidate : candidates) {
            String fc = fix(candidate);
            for (BikeDTO bike : bikes) {
                String registered = fix(BikeModel.normalizeReg(bike.getRegNumber()));
                int d = distance(fc, registered);
                if (d < 0) {
                    continue;
                }
                Match old = bestPerBike.get(bike.getBikeId());
                if (old == null || d < old.distance()) {
                    bestPerBike.put(bike.getBikeId(), new Match(bike, candidate, d));
                }
            }
        }

        List<Match> list = new ArrayList<>(bestPerBike.values());
        list.sort((a, b) -> {
            int byDistance = Integer.compare(a.distance(), b.distance());
            return byDistance != 0 ? byDistance : a.bike().getRegNumber().compareTo(b.bike().getRegNumber());
        });
        return list.size() > 5 ? new ArrayList<>(list.subList(0, 5)) : list;
    }

    /** -1 when the two plates are too different. */
    private static int distance(String read, String registered) {
        if (read.equals(registered)) {
            return 0;
        }
        // read without the province letters (or the register has none)
        if (read.length() >= 6 && registered.endsWith(read)) {
            return 0;
        }
        if (registered.length() >= 6 && read.endsWith(registered)) {
            return 0;
        }
        int d = levenshtein(read, registered);
        int limit = Math.max(read.length(), registered.length()) >= 8 ? 2 : 1;
        return d <= limit ? d : -1;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.length()];
    }
}