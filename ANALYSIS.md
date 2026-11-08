# Assignment 4 — Algorithm Analysis, Verification and Search Comparison

## 1. Environment, interface and reproducibility

**Selected language:** Java (`submission.json` = `Java`). Code targets Java 17 and uses no external dependencies. Testing environment: Linux x86_64 container, OpenJDK 21.0.12.1 runtime/compiler (compiled with `javac --release 17`), Python 3.13.5, AMD EPYC 9V74 processor (5 logical CPUs visible), about 5.8 GiB total RAM visible to the container. Official Java launcher applies `-Xmx384m`; the published grading limit is Java heap <=512 MiB. The local CPU, wall-clock measurements and memory environment differ from the grader, so measured times are indicative only.

**Commands** (run in the project root):

```sh
python tools/run.py --setup
python tools/run.py --check
python tools/run.py --input examples/lcs.jsonl
python tests/student/property_tests.py
javac --release 17 -encoding UTF-8 -cp Java/build -d Java/build tests/student/Benchmark.java
java -Xmx384m -cp Java/build Benchmark > tests/student/benchmark.csv
```

`tools/run.py` compiles every `.java` file in `Java/source`, creates `Java/build` and launches `Main`. The program reads one UTF-8 JSON request per line and writes one JSON result per line. `Main` is the provided template dispatcher and has no mutable algorithm state. Every request creates its own algorithm data structures; neither output messages nor debug logs are written to stdout. All indices are zero-based; values whose sums could exceed the 32-bit range use `long`.

**Actual recorded local checks:** official public `--setup` passed, 44/44 public cases passed, the repeated 88-request batch passed, 25 student fixtures passed, a seeded 660-request mixed batch passed, and 10 explicit maximum-domain scale tests passed. Details are preserved in `tests/student/results.txt`. The 660-request count includes 25 fixtures before and after the randomized cases (85 LCS, 70 merge, 75 tree, 255 string-search, 60 suffix and 65 Aho requests). Official hidden tests and Moodle grading were not run.

## 2. Dynamic programming algorithms

### 2.1 LCS — `lcs`

Let `dp[i][j]` represent the length of a longest common subsequence of `a[0:i]` and `b[0:j]`. Both `dp[0][j]` and `dp[i][0]` equal zero. For `i,j>0`:

- If `a[i-1]==b[j-1]`, `dp[i][j]=dp[i-1][j-1]+1`.
- Otherwise `dp[i][j]=max(dp[i-1][j],dp[i][j-1])`.

Fill rows in increasing `i` and columns in increasing `j`. Every dependency is a strictly shorter prefix and has already been calculated. The last equality can extend a common subsequence, whereas a mismatch requires excluding at least one of the unequal trailing characters. These two cases cover every optimum by induction on `i+j`.

Reconstruct from `(n,m)`. On equality, append that character and move diagonally. Otherwise follow a neighbor with the larger DP value, breaking ties in favor of `(i-1,j)`. Reverse the resulting character sequence before returning it. Equal-score tie paths are both valid; only the optimal length and membership as a subsequence matter. If one string is empty, return length 0 and the empty string.

**Complexity:** O((n+1)(m+1)) time and auxiliary memory for the DP table, plus O(min(n,m)) for the reconstructed output. Boundary test `a="aXbYc", b="abc"` expects length 3, demonstrating that an LCS is **not necessarily contiguous**.

### 2.2 Ordered adjacent-block merging — `merge`

Let `prefix[i]` be the sum of block sizes before position `i`, and `sum(l,r)=prefix[r+1]-prefix[l]`. Define `dp[l][r]` as the minimum cost to merge the consecutive original blocks `l..r` into a single block. The base case is `dp[l][l]=0`. For each nontrivial interval:

`dp[l][r] = sum(l,r) + min_{l<=k<r}(dp[l][k]+dp[k+1][r])`.

Every legal **last** merge combines two completed **adjacent** intervals `[l,k]` and `[k+1,r]`, so considering every split covers the final action of every valid merge plan. Conversely, combining optimal subplans followed by the final merge constructs a valid plan at the candidate cost. This proves the recurrence by optimal substructure. Fill states by increasing interval length.

Store the best split `split[l][r]`. Reconstruct **left subtree**, then **right subtree**, then append `[l,k,r]`. This postorder makes the merge plan chronological; emitting the parent first would be invalid. `n<=1` yields `{cost:0,plan:[]}`. Ties use the first minimizing split; other optimal plans are equally valid. Prefix sums and DP cost are Java `long`, supporting up to 200 blocks of size 1 billion.

**Complexity:** O(n³) time, O(n²) auxiliary memory and O(n) output triples. The depth of the reconstruction is at most 200. Test `[1,100,1]` expects cost 203 rather than illegally merging two nonadjacent size-1 blocks first.

### 2.3 Maximum-weight independent set on a tree — `tree`

The input is a connected undirected tree (or empty). Choose root vertex 0 and generate a root-to-leaf `order[]` with an iterative traversal, simultaneously recording each `parent[]`. Use primitive adjacency arrays (`head`, `to`, `next`) to avoid expensive boxed vertex objects. Evaluate vertices in reverse `order[]`, so all child states are ready.

`take[u] = weight[u] + sum(skip[v])` over children `v`.

`skip[u] = sum(max(take[v],skip[v]))` over children `v`.

The first equation follows because choosing `u` forbids every neighboring child. The second follows because, without `u`, each child subtree can independently choose its better state. Because a tree has no cross-edges between distinct child subtrees, adding their optimal values is valid; induction from leaves proves global optimality. The answer is `max(take[0],skip[0])`, with the empty set allowed.

Reconstruct in root-to-leaf order without recursion. If the parent was chosen, skip `u`; otherwise choose `u` precisely when `take[u] > skip[u]`. Breaking ties toward skip is valid and makes all-negative and all-zero trees return an empty witness. Iterate through all vertex IDs at the end to emit each selected ID once. Java `long` handles total weights up to roughly 10^14 in absolute magnitude.

**Complexity:** O(n+1) time and O(n+1) auxiliary memory for adjacency, parent, order, states and choices; O(n) worst-case witness size. This avoids stack overflow on a path of 100,000 vertices. Counterexample: a chain with weights `[4,9,6]` is best served by endpoints `{0,2}` of total weight 10, not by the locally tempting middle vertex of value 9.

## 3. Single-pattern string search

All three commands return every occurrence start in **strictly increasing** zero-based order. Overlaps count. If the pattern is empty, every boundary `0..n` is a match (including the final boundary). A pattern longer than the text has no starts. Characters are exact ASCII code units: case, spaces and punctuation matter.

### 3.1 Naive search — `naive`

For every candidate start `i` with `0 <= i <= n-m`, compare `text[i+j]` and `pattern[j]` until the first mismatch or `j==m`. Append `i` only if all `m` characters match; incrementing start by exactly one preserves overlapping matches. No built-in substring search is used in the algorithm.

**Correctness:** every possible valid window is visited exactly once, and only a window whose entire pattern is equal is output. **Complexity:** O(nm+n+1) worst-case time including empty-pattern output, O(1) auxiliary memory and O(z+1) space for z returned starts.

### 3.2 KMP — `kmp`

The prefix function `pi[i]` is the length of the longest proper prefix of `pattern[0:i+1]` that is also a suffix of that prefix. Construct it from left to right; mismatch fallback sets `j=pi[j-1]` until the character matches or `j==0`. The scan variable `j` is the length of the currently matched prefix of the pattern. For each text character, perform the same fallback, then advance `j` on equality.

Whenever `j==m`, emit `i-m+1`, and **set `j=pi[m-1]`**. This fallback is essential: a prefix of the pattern can overlap its previous occurrence. The invariant is that `j` is the longest prefix already matched at the end of the consumed text. All rejected longer candidates have mismatched; the next valid candidate is the failure-border candidate. Thus no valid occurrence is skipped, and each emitted position is exact.

**Complexity:** O(n+m+z+1) time, O(m+1) auxiliary memory for the prefix function and O(z+1) output. Test `text="abababa", pattern="aba"` expects `[0,2,4]` (two overlaps).

### 3.3 Verified Rabin–Karp — `rk`

Use the required polynomial hash with `base=31`, `mod=101`, ASCII character codes and the leftmost character weighted by `31^(m-1)`. Initialize the pattern hash and first window hash with `h = (31*h + characterCode) mod 101`. Let `power = 31^(m-1) mod 101`. On a slide from `text[i:i+m]` to `text[i+1:i+m+1]`, compute:

`hNext = ((h - code(text[i])*power) mod 101 + 101) mod 101`;

`hNext = (hNext*31 + code(text[i+m])) mod 101`.

The double modulo normalization avoids negative rolling hashes. Hash equality is **only a candidate**. Compare actual characters before appending a result, preventing false positives even when collisions occur. The polynomial recurrence tracks the window fingerprint by removing the former leading coefficient, shifting all powers by a factor of 31, and adding the new last character. Exact verification ensures collision-free *results* even though the hashes themselves collide.

**Complexity:** O(n+m+c*m) for `1<=m<=n`, where `c` is the number of equal-hash candidate windows. Worst-case O(nm+n+m+1), not deterministic linear time. O(1) rolling-hash auxiliary space plus O(z+1) output. Test `text="abdj", pattern="ab"` has equal hashes for `"ab"` and `"dj"`, but only position 0 is valid.

## 4. Suffix array and Kasai LCP — `suffix`

Sort the **nonempty** suffixes, without a sentinel in the returned array. Start with `rank[i] = ASCII(text[i])-31` (positive ranks 1..95) and `sa[i]=i`. Rank 0 is reserved for an out-of-bounds suffix position. During a doubling round of length `k`, sort suffix positions by rank pair `(rank[i], rank[i+k] or 0)`. The implementation performs a **stable counting sort by the second component**, followed by a stable counting sort by the first, making pair sorting linear per round. Assign new ranks starting at 1, increasing when adjacent rank pairs differ. Once all n classes are distinct, stop. Doubling increases distinguishable prefix length from k to 2k. After enough rounds, the relative order equals the lexicographic order of entire suffixes, including proper-prefix behavior (shorter suffix sorts first because missing positions have rank zero).

Build `inverse[sa[i]]=i`. Kasai scans starting positions `i` in text order. If `inverse[i]==0`, the LCP with the nonexistent predecessor is 0. Otherwise compare suffixes at `i` and `sa[inverse[i]-1]`, reusing at least the previous LCP length minus one. Advancing `i` by one can reduce the previously known common prefix by at most one; this is the reason Kasai's comparisons take O(n) total rather than quadratic time. Store `lcp[0]=0`, and `lcp[p]` for the adjacent suffixes `sa[p-1]` and `sa[p]`.

The maximum length among adjacent LCP values equals the longest substring that appears at two distinct positions: any pair of suffixes sharing a prefix of length L defines a contiguous interval of suffixes with that prefix, hence at least one adjacent pair shares length >=L. Conversely, every adjacent LCP is a real repeated substring length. Return any such substring; overlapping occurrences are permitted. For an empty text, all arrays and repeated substring are empty.

**Complexity:** O(n log(n+1)) time for doubling with counting-sort pairs (plus O(n) Kasai) and O(n+1) auxiliary memory. Output has n suffix starts, n LCP values and up to n-1 characters of repetition. Test `banana` yields `sa=[5,3,1,0,4,2]`, `lcp=[0,1,3,0,0,2]`, `repeated="ana"`. `aaaa` tests overlap and the absence of a sentinel suffix.

## 5. Aho–Corasick multiple-pattern counting — `aho`

Build a trie of all nonempty patterns, over the fixed 95-symbol printable ASCII alphabet. `go[state][char]` gives a child transition; state zero is root. Duplicate dictionary strings follow the same path and record the **same terminal state**, but retain their individual dictionary indices. Every trie node has a failure link to the longest proper suffix that is also a trie prefix.

Build failure links with a BFS over trie nodes. Root's missing transitions remain at root; for each discovered node, existing children receive `fail[child]=go[fail[parent]][symbol]`, and missing transitions are filled with transitions from the failure state. Thus the completed transition table is a DFA that consumes each input character in O(1) time. BFS ensures a failure target's transitions were already computed.

For each independent text, create a fresh `long[] visits` and reset automaton state to root. Follow the DFA and increment `visits[state]` for every consumed character. In **reverse BFS order**, add each node's accumulated count to its failure node: `visits[fail[node]] += visits[node]`. A pattern ending at a node must also end whenever the current state has that node along its failure-suffix chain, so reverse propagation collects all suffix-pattern matches **without iterating over every match**. Finally, for each original pattern index, return the visit count of its terminal state; duplicate strings naturally receive equal counts, including overlapping matches. An empty text produces all zero counts, and an empty dictionary produces one empty row per text.

**Complexity:** let L be total pattern length, S trie states including root, p patterns, and q texts. Building the fixed-alphabet automaton takes O(L+95S+p) time and O(95S+p) auxiliary memory. Each independent text takes O(|text|+S+p) time using reverse-failure propagation and O(S) transient counts. Total results use O(qp) output space. Arrays are request-local. This avoids the potentially enormous O(number of matches) explicit output-link traversal on dense inputs. Boundary test `patterns=["a","aa","a"]`, `texts=["aaa","","aaa"]` expects `[[3,2,3],[0,0,0],[3,2,3]]`.

## 6. Own tests and verification

The test file `tests/student/cases.json` contains **25 named boundary cases** with the `input`, an optimal/expected `output` and the `defect` they detect. For non-unique optima, the validator accepts any valid witness rather than demanding a particular sequence or choice of vertices. `tests/student/property_tests.py` additionally tests 610 generated cases against independent Python oracles (brute-force enumeration for small trees; memoized interval recursion for merging; independently generated reference substring searches, suffix sorting, and multi-pattern counts) and checks **660 total mixed requests** after repeating 25 named fixtures at both ends. Random seed: `20261107`.

| Task | Representative input | Expected value / witness properties | Actual local result | Defect targeted |
|---|---|---|---|---|
| lcs | `a="aXbYc"`, `b="abc"` | length 3; common subsequence `abc` | PASS | Confusing subsequence with substring |
| merge | `sizes=[1,100,1]` | cost 203; 2 chronological adjacent merges | PASS | Illegally merging distant small blocks |
| merge | four 1-billion blocks | cost 8,000,000,000 | PASS | 32-bit overflow |
| tree | all negative weights | weight 0 and valid empty set | PASS | Forcing a negative selection |
| tree | chain `[4,9,6]` | total weight 10 and compatible witness | PASS | Wrong take/skip restriction |
| all search | `text="Hi!",pattern=""` | starts `[0,1,2,3]` | PASS | Missing final boundary |
| all search | `aaaaa` / `aaa` | `[0,1,2]` | PASS | Dropping overlaps |
| rk | `abdj` / `ab` | `[0]` only | PASS | Reporting hash collision as exact match |
| suffix | `banana` | `[5,3,1,0,4,2]`; LCP `[0,1,3,0,0,2]`; `ana` | PASS | Bad rank tie or Kasai indexing |
| aho | `a,aa,a` / `aaa,"",aaa` | `[[3,2,3],[0,0,0],[3,2,3]]` | PASS | Duplicate/suffix/state-reset errors |

**Maximum-domain tests:** LCS 1000×1000; merging 200 zeros and 200 billion-sized blocks; path tree with 100,000 vertices with both unit and billion weights; KMP text of 200,000 and pattern of 100,000 characters; Rabin–Karp with text 10,000 and pattern 2,000; suffix array of 50,000 equal characters; Aho with 10,000 duplicate patterns across 100 texts (one million returned counts); and a near-100,000-state trie scanned against a 200,000-character text. All 10 cases passed locally; detailed measured case durations are in `tests/student/results.txt`. These tests also exercise large witnesses and 64-bit arithmetic. Separate requests also validate that one task does not leak state into the next.

## 7. Naive, KMP and exact RK comparison

**Reproduction:** compile the source, compile `tests/student/Benchmark.java`, and run `java -Xmx384m -cp Java/build Benchmark > tests/student/benchmark.csv`. The benchmark uses `java.util.Random(20261107)`, ASCII alphabet `abcd` for random inputs, and repeated `a` text/patterns for repetitive inputs. Three increasing `(n,m)` sizes are `(300,8)`, `(1200,24)` and `(3000,48)`, all valid for every search command. Every method is checked for exact equality against an independently computed `String.startsWith` reference **before** timing. `String.startsWith` is only used in the benchmark reference, not inside the algorithms.

**Identical timing scope:** direct `Algorithms.naive`, `Algorithms.kmp` or `Algorithms.rk` call through a constant input map, including result-list creation, but excluding compilation, JVM startup, JSON parsing/serialization, reference verification and input generation. For each method/case, perform 250 **untimed warm-up calls** to allow JIT compilation, then three **separate timed runs of 250 calls** each. Each CSV run is elapsed `System.nanoTime()` / (250 calls × 1000) in **microseconds per call**; report the median of three runs. A volatile accumulator consumes the match-count result. The measured container reports AMD EPYC 9V74 CPU (five visible vCPUs), Linux x86_64, OpenJDK 21.0.12.1. No CPU frequency isolation or exclusive host access is guaranteed.

**Actually recorded medians (microseconds/call):**

| Input type | n | m | Naive | KMP | Exact RK |
|---|---:|---:|---:|---:|---:|
| random | 300 | 8 | 5.231 | 5.451 | 4.720 |
| random | 1200 | 24 | 5.363 | 24.439 | 21.944 |
| random | 3000 | 48 | 5.235 | 5.974 | 35.052 |
| repetitive | 300 | 8 | 37.724 | 10.495 | 37.359 |
| repetitive | 1200 | 24 | 42.196 | 7.629 | 87.401 |
| repetitive | 3000 | 48 | 175.663 | 22.754 | 399.742 |

The `benchmark.csv` file contains all **three individual samples per algorithm per input case**, the seed, warm-up and repetition counts; it is the reproducible raw evidence for this table. Short random cases often fail on the first few character comparisons, so naive search can be competitive despite quadratic worst-case bounds. For highly repetitive text, naive and exact RK must verify nearly every full-length candidate and can cost O(nm), whereas KMP reuses previously established prefix information for O(n+m+z). RK's deliberately small modulus 101 makes collisions easy to demonstrate and increases verification candidates on some inputs; timing behavior is not representative of every production rolling-hash system. JIT compilation, virtualization and garbage collection can significantly affect the small measured times (notably the non-monotonic random-case results); medians from these samples **do not prove complexity classes**. Correctness was checked independently before each timed case.

## 8. Sources, assistance and independent responsibility

- **Official course template** `RZhaniya/daa-assignment-4-archive`, README, contracts, rubric, published 44-case tests and public launcher: requirements, starter JSON transport, grading and test specification. https://github.com/RZhaniya/daa-assignment-4-archive
- **Course interactive lesson and practice:** provided as study references; https://rzhaniya.github.io/daa-assignment-4-archive/
- **ChatGPT:** AI was used as an assist tool for writing and debugging tests, as well as for receiving recommendations on code structure and optimization.
- **Local verification executed by the assistant:** JDK 17-compatible compilation, 44 public checks, repeated mixed batch, 660 own mixed requests, 10 scale tests, and 18 algorithm/case benchmark measurements (each with three timed samples), with recorded output under `tests/student`. No GitHub Actions run, external official grade, Moodle submission, or graded defence attempt was made.
