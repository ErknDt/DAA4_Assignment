import java.util.*;

/** Assignment 4: original implementations of dynamic programming and string algorithms. */
public final class Algorithms {
    private Algorithms() {}

    private static int number(Object value) { return ((Number) value).intValue(); }
    private static long longNumber(Object value) { return ((Number) value).longValue(); }

    /** Prefix DP: dp[i][j] is LCS length for a[0..i), b[0..j). */
    static Map<String, Object> lcs(Map<String, Object> input) {
        String a = (String) input.get("a"), b = (String) input.get("b");
        int n = a.length(), m = b.length();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        StringBuilder reversed = new StringBuilder();
        int i = n, j = m;
        while (i > 0 && j > 0) {
            if (a.charAt(i - 1) == b.charAt(j - 1)) {
                reversed.append(a.charAt(i - 1));
                i--; j--;
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }
        return Map.of("length", dp[n][m], "sequence", reversed.reverse().toString());
    }

    /** Interval DP, with a postorder reconstruction of the actual adjacent merges. */
    static Map<String, Object> merge(Map<String, Object> input) {
        List<?> sizes = (List<?>) input.get("sizes");
        int n = sizes.size();
        List<List<Integer>> plan = new ArrayList<>();
        if (n <= 1) return Map.of("cost", 0L, "plan", plan);
        long[] prefix = new long[n + 1];
        for (int i = 0; i < n; i++) prefix[i + 1] = prefix[i] + longNumber(sizes.get(i));
        long[][] dp = new long[n][n];
        int[][] split = new int[n][n];
        for (int length = 2; length <= n; length++) {
            for (int l = 0; l + length <= n; l++) {
                int r = l + length - 1;
                long best = Long.MAX_VALUE;
                for (int k = l; k < r; k++) {
                    long candidate = dp[l][k] + dp[k + 1][r] + prefix[r + 1] - prefix[l];
                    if (candidate < best) {
                        best = candidate;
                        split[l][r] = k;
                    }
                }
                dp[l][r] = best;
            }
        }
        buildPlan(0, n - 1, split, plan);
        return Map.of("cost", dp[0][n - 1], "plan", plan);
    }

    private static void buildPlan(int l, int r, int[][] split, List<List<Integer>> out) {
        if (l == r) return;
        int k = split[l][r];
        buildPlan(l, k, split, out);
        buildPlan(k + 1, r, split, out);
        out.add(List.of(l, k, r));
    }

    /** Linear-time iterative take/skip tree DP. No recursion on deep chains. */
    static Map<String, Object> tree(Map<String, Object> input) {
        int n = number(input.get("n"));
        List<Integer> selected = new ArrayList<>();
        if (n == 0) return Map.of("weight", 0L, "selected", selected);
        List<?> weights = (List<?>) input.get("weights");
        List<?> edges = (List<?>) input.get("edges");
        int[] head = new int[n];
        Arrays.fill(head, -1);
        int[] to = new int[2 * (n - 1)], next = new int[to.length];
        int edgeIndex = 0;
        for (Object edge : edges) {
            List<?> pair = (List<?>) edge;
            int u = number(pair.get(0)), v = number(pair.get(1));
            to[edgeIndex] = v; next[edgeIndex] = head[u]; head[u] = edgeIndex++;
            to[edgeIndex] = u; next[edgeIndex] = head[v]; head[v] = edgeIndex++;
        }
        int[] parent = new int[n], order = new int[n];
        Arrays.fill(parent, -1);
        parent[0] = 0;
        int count = 1;
        order[0] = 0;
        for (int pos = 0; pos < count; pos++) {
            int u = order[pos];
            for (int edge = head[u]; edge != -1; edge = next[edge]) {
                int v = to[edge];
                if (v == parent[u]) continue;
                parent[v] = u;
                order[count++] = v;
            }
        }
        long[] take = new long[n], skip = new long[n];
        for (int pos = n - 1; pos >= 0; pos--) {
            int u = order[pos];
            take[u] = longNumber(weights.get(u));
            for (int edge = head[u]; edge != -1; edge = next[edge]) {
                int v = to[edge];
                if (parent[v] == u) {
                    take[u] += skip[v];
                    skip[u] += Math.max(take[v], skip[v]);
                }
            }
        }
        boolean[] chosen = new boolean[n];
        for (int pos = 0; pos < n; pos++) {
            int u = order[pos];
            if (u == 0 || !chosen[parent[u]]) chosen[u] = take[u] > skip[u];
        }
        for (int u = 0; u < n; u++) if (chosen[u]) selected.add(u);
        return Map.of("weight", Math.max(take[0], skip[0]), "selected", selected);
    }

    private static List<Integer> allBoundaries(int n) {
        List<Integer> positions = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) positions.add(i);
        return positions;
    }

    /** Naive all-occurrences matcher, including overlapping matches and empty pattern. */
    static Map<String, Object> naive(Map<String, Object> input) {
        String text = (String) input.get("text"), pattern = (String) input.get("pattern");
        int n = text.length(), m = pattern.length();
        if (m == 0) return Map.of("positions", allBoundaries(n));
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i + m <= n; i++) {
            int j = 0;
            while (j < m && text.charAt(i + j) == pattern.charAt(j)) j++;
            if (j == m) positions.add(i);
        }
        return Map.of("positions", positions);
    }

    /** Prefix function and KMP with fallback after complete matches for overlaps. */
    static Map<String, Object> kmp(Map<String, Object> input) {
        String text = (String) input.get("text"), pattern = (String) input.get("pattern");
        int n = text.length(), m = pattern.length();
        if (m == 0) return Map.of("positions", allBoundaries(n));
        int[] pi = new int[m];
        for (int i = 1; i < m; i++) {
            int j = pi[i - 1];
            while (j > 0 && pattern.charAt(i) != pattern.charAt(j)) j = pi[j - 1];
            if (pattern.charAt(i) == pattern.charAt(j)) j++;
            pi[i] = j;
        }
        List<Integer> positions = new ArrayList<>();
        int j = 0;
        for (int i = 0; i < n; i++) {
            while (j > 0 && text.charAt(i) != pattern.charAt(j)) j = pi[j - 1];
            if (text.charAt(i) == pattern.charAt(j)) j++;
            if (j == m) {
                positions.add(i - m + 1);
                j = pi[j - 1];
            }
        }
        return Map.of("positions", positions);
    }

    /** Rabin-Karp: base=31, modulus=101, verify each equal-hash window. */
    static Map<String, Object> rk(Map<String, Object> input) {
        String text = (String) input.get("text"), pattern = (String) input.get("pattern");
        int n = text.length(), m = pattern.length();
        if (m == 0) return Map.of("positions", allBoundaries(n));
        List<Integer> positions = new ArrayList<>();
        if (m > n) return Map.of("positions", positions);
        final int base = 31, mod = 101;
        long power = 1, pHash = 0, wHash = 0;
        for (int i = 0; i < m; i++) {
            pHash = (pHash * base + pattern.charAt(i)) % mod;
            wHash = (wHash * base + text.charAt(i)) % mod;
            if (i + 1 < m) power = (power * base) % mod;
        }
        for (int i = 0; i + m <= n; i++) {
            if (wHash == pHash) {
                int j = 0;
                while (j < m && text.charAt(i + j) == pattern.charAt(j)) j++;
                if (j == m) positions.add(i);
            }
            if (i + m < n) {
                wHash = ((wHash - text.charAt(i) * power) % mod + mod) % mod;
                wHash = (wHash * base + text.charAt(i + m)) % mod;
            }
        }
        return Map.of("positions", positions);
    }

    /** Stable counting sort by rank[i+offset], treating out-of-bounds as rank zero. */
    private static void radixPass(int[] source, int[] target, int[] rank,
                                  int offset, int highestRank, int n, int[] counts) {
        Arrays.fill(counts, 0, highestRank + 1, 0);
        for (int i : source) counts[i + offset < n ? rank[i + offset] : 0]++;
        int start = 0;
        for (int r = 0; r <= highestRank; r++) {
            int size = counts[r];
            counts[r] = start;
            start += size;
        }
        for (int i : source) {
            int key = i + offset < n ? rank[i + offset] : 0;
            target[counts[key]++] = i;
        }
    }

    /** Prefix-doubling suffix array using two counting sorts, followed by Kasai. */
    static Map<String, Object> suffix(Map<String, Object> input) {
        String text = (String) input.get("text");
        int n = text.length();
        if (n == 0) return Map.of("sa", List.of(), "lcp", List.of(), "repeated", "");
        int[] sa = new int[n], rank = new int[n], temp = new int[n];
        int[] newRank = new int[n], counts = new int[Math.max(n, 128) + 2];
        for (int i = 0; i < n; i++) {
            sa[i] = i;
            rank[i] = text.charAt(i) - 31; // printable ASCII -> [1,95]
        }
        int highestRank = 95;
        for (int length = 1; length < n; length <<= 1) {
            radixPass(sa, temp, rank, length, highestRank, n, counts);
            radixPass(temp, sa, rank, 0, highestRank, n, counts);
            int classes = 1;
            newRank[sa[0]] = classes;
            for (int i = 1; i < n; i++) {
                int a = sa[i - 1], b = sa[i];
                if (rank[a] != rank[b] ||
                    (a + length < n ? rank[a + length] : 0) !=
                    (b + length < n ? rank[b + length] : 0)) classes++;
                newRank[b] = classes;
            }
            int[] swap = rank; rank = newRank; newRank = swap;
            highestRank = classes;
            if (classes == n) break;
        }
        // For n=1, there is only one suffix. For n>1 all suffix ranks are unique
        // after at most ceil(log2(n)) doubling rounds.
        int[] inverse = new int[n], lcp = new int[n];
        for (int i = 0; i < n; i++) inverse[sa[i]] = i;
        int same = 0, bestLength = 0, bestStart = 0;
        for (int i = 0; i < n; i++) {
            int pos = inverse[i];
            if (pos == 0) { same = 0; continue; }
            int j = sa[pos - 1];
            while (i + same < n && j + same < n &&
                   text.charAt(i + same) == text.charAt(j + same)) same++;
            lcp[pos] = same;
            if (same > bestLength) { bestLength = same; bestStart = i; }
            if (same > 0) same--;
        }
        List<Integer> saOut = new ArrayList<>(n), lcpOut = new ArrayList<>(n);
        for (int i = 0; i < n; i++) { saOut.add(sa[i]); lcpOut.add(lcp[i]); }
        return Map.of("sa", saOut, "lcp", lcpOut,
                      "repeated", text.substring(bestStart, bestStart + bestLength));
    }

    /** Aho-Corasick DFA with failure-link occurrence aggregation, no per-match traversal. */
    static Map<String, Object> aho(Map<String, Object> input) {
        List<?> patterns = (List<?>) input.get("patterns");
        List<?> texts = (List<?>) input.get("texts");
        int p = patterns.size();
        List<List<Long>> allCounts = new ArrayList<>(texts.size());
        if (p == 0) {
            for (Object ignored : texts) allCounts.add(new ArrayList<>());
            return Map.of("counts", allCounts);
        }
        int totalLength = 0;
        for (Object pattern : patterns) totalLength += ((String) pattern).length();
        final int alphabet = 95;
        int[][] go = new int[totalLength + 1][alphabet];
        int[] fail = new int[totalLength + 1];
        int[] terminal = new int[p], queue = new int[totalLength + 1];
        int stateCount = 1;
        for (int i = 0; i < p; i++) {
            String pattern = (String) patterns.get(i);
            int at = 0;
            for (int j = 0; j < pattern.length(); j++) {
                int ch = pattern.charAt(j) - 32;
                if (go[at][ch] == 0) go[at][ch] = stateCount++;
                at = go[at][ch];
            }
            terminal[i] = at;  // duplicates intentionally share the terminal state
        }
        int first = 0, last = 0;
        for (int ch = 0; ch < alphabet; ch++) {
            int child = go[0][ch];
            if (child != 0) queue[last++] = child;
        }
        while (first < last) {
            int u = queue[first++];
            for (int ch = 0; ch < alphabet; ch++) {
                int v = go[u][ch];
                if (v != 0) {
                    fail[v] = go[fail[u]][ch];
                    queue[last++] = v;
                } else {
                    go[u][ch] = go[fail[u]][ch];
                }
            }
        }
        for (Object request : texts) {
            String text = (String) request;
            long[] visits = new long[stateCount];
            int state = 0;
            for (int i = 0; i < text.length(); i++) {
                state = go[state][text.charAt(i) - 32];
                visits[state]++;
            }
            for (int i = last - 1; i >= 0; i--) {
                int u = queue[i];
                visits[fail[u]] += visits[u];
            }
            List<Long> row = new ArrayList<>(p);
            for (int terminalState : terminal) row.add(visits[terminalState]);
            allCounts.add(row);
        }
        return Map.of("counts", allCounts);
    }
}
