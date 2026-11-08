import java.util.*;
/** Reproducible single-JVM benchmark. Build once; no JSON, JVM launch or compilation in timings. */
public final class Benchmark {
    private static final int SEED = 20261107;
    private static final int WARMUP = 250;
    private static final int REPEATS = 250;
    private static volatile int sink;

    private static List<Integer> reference(String text, String pattern) {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i + pattern.length() <= text.length(); i++) {
            if (text.startsWith(pattern, i)) starts.add(i);
        }
        return starts;
    }
    @SuppressWarnings("unchecked")
    private static List<Integer> match(String algorithm, Map<String,Object> request) {
        Map<String,Object> response;
        switch(algorithm) {
            case "naive": response = Algorithms.naive(request); break;
            case "kmp": response = Algorithms.kmp(request); break;
            case "rk": response = Algorithms.rk(request); break;
            default: throw new IllegalArgumentException(algorithm);
        }
        return (List<Integer>) response.get("positions");
    }
    private static String randomString(Random rng, int length) {
        char[] chars = new char[length];
        for (int i=0;i<length;i++) chars[i] = "abcd".charAt(rng.nextInt(4));
        return new String(chars);
    }
    public static void main(String[] args) {
        System.out.println("distribution,n,m,seed,algorithm,run1_us,run2_us,run3_us,median_us,correct,repeats,warmup");
        Random rng = new Random(SEED);
        int[] lengths = {300,1200,3000};
        int[] patternLengths = {8,24,48};
        String[] names = {"naive","kmp","rk"};
        for (String distribution : new String[] {"random","repetitive"}) {
            for (int index=0;index<lengths.length;index++) {
                int n=lengths[index], m=patternLengths[index];
                String text = distribution.equals("random") ? randomString(rng,n) : "a".repeat(n);
                String pattern = distribution.equals("random") ? randomString(rng,m) : "a".repeat(m);
                List<Integer> expected = reference(text,pattern);
                Map<String,Object> request = Map.of("text",text,"pattern",pattern);
                for (String name : names) {
                    if (!match(name,request).equals(expected)) throw new AssertionError("Wrong: "+name);
                    for(int j=0;j<WARMUP;j++) sink ^= match(name,request).size();
                    double[] times = new double[3];
                    for(int run=0;run<3;run++) {
                        long start = System.nanoTime();
                        for(int iteration=0;iteration<REPEATS;iteration++) sink ^= match(name,request).size();
                        times[run]=(System.nanoTime()-start) / (1000.0 * REPEATS);
                    }
                    double[] sorted = times.clone(); Arrays.sort(sorted);
                    System.out.printf(Locale.ROOT,"%s,%d,%d,%d,%s,%.3f,%.3f,%.3f,%.3f,yes,%d,%d%n",
                            distribution,n,m,SEED,name,times[0],times[1],times[2],sorted[1],REPEATS,WARMUP);
                }
            }
        }
    }
}
