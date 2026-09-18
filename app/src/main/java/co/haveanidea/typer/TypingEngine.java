package co.haveanidea.typer;

import java.util.*;

/** Deterministic, bounded English baseline; deliberately conservative about automatic edits. */
public final class TypingEngine {
    private final LinkedHashSet<String> words = new LinkedHashSet<>();
    private static final Map<String,String> CORRECTIONS = new HashMap<>();
    static {
        String[][] pairs={{"teh","the"},{"adn","and"},{"thsi","this"},{"taht","that"},{"woudl","would"},{"coudl","could"},{"recieve","receive"},{"becuase","because"},{"dont","don't"},{"cant","can't"},{"im","I'm"},{"wierd","weird"}};
        for(String[] pair:pairs) CORRECTIONS.put(pair[0],pair[1]);
    }
    public TypingEngine(Collection<String> vocabulary) { words.addAll(vocabulary); }
    public void add(String word) { if (word.matches("[a-zA-Z']{2,40}")) words.add(word.toLowerCase(Locale.ROOT)); }
    public String correction(String word) {
        String result = CORRECTIONS.get(word.toLowerCase(Locale.ROOT));
        return result == null ? word : matchCase(word, result);
    }
    public List<String> suggestions(String input) {
        if (input.isEmpty()) return Arrays.asList("I", "the", "thanks");
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        found.add(input);
        String fixed = correction(input);
        if (!fixed.equals(input)) found.add(fixed);
        for (String w : words) {
            if (w.startsWith(lower) && !w.equals(lower)) found.add(matchCase(input,w));
            if (found.size() >= 3) return found;
        }
        for (String w : words) {
            if (lower.length() >= 3 && distance(lower,w) == 1 && !found.contains(matchCase(input,w))) found.add(matchCase(input,w));
            if (found.size() >= 3) break;
        }
        return found;
    }
    public static String matchCase(String source, String target) {
        if (source.equals(source.toUpperCase(Locale.ROOT)) && source.length() > 1) return target.toUpperCase(Locale.ROOT);
        if (!source.isEmpty() && Character.isUpperCase(source.charAt(0))) return Character.toUpperCase(target.charAt(0))+target.substring(1);
        return target;
    }
    public static int distance(String a, String b) {
        int[][] d = new int[a.length()+1][b.length()+1];
        for (int i=0;i<=a.length();i++) d[i][0]=i;
        for (int j=0;j<=b.length();j++) d[0][j]=j;
        for (int i=1;i<=a.length();i++) for(int j=1;j<=b.length();j++) {
            d[i][j]=Math.min(Math.min(d[i-1][j]+1,d[i][j-1]+1),d[i-1][j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
            if (i>1 && j>1 && a.charAt(i-1)==b.charAt(j-2) && a.charAt(i-2)==b.charAt(j-1)) d[i][j]=Math.min(d[i][j],d[i-2][j-2]+1);
        }
        return d[a.length()][b.length()];
    }
    public static String basicCleanup(String raw) {
        String clean=raw.replaceAll("(?i)\\b(um+|uh+|erm+)\\b[, ]*", "").replaceAll("[ \\t]+", " ").trim();
        if (clean.isEmpty()) return "";
        clean=Character.toUpperCase(clean.charAt(0))+clean.substring(1);
        if (!clean.matches("(?s).*[.!?]$")) clean+=".";
        return clean;
    }
    public List<String> glide(String trace) {
        if(trace.length()<2) return Collections.emptyList();
        List<String> options=new ArrayList<>();
        for(String w: words) if(w.length()>1 && w.charAt(0)==trace.charAt(0) && w.charAt(w.length()-1)==trace.charAt(trace.length()-1)) options.add(w);
        options.sort(Comparator.comparingInt(w -> distance(collapse(w),trace)));
        return options.subList(0,Math.min(3, options.size()));
    }
    private static String collapse(String s) { return s.replaceAll("(.)\\1+", "$1"); }
}
