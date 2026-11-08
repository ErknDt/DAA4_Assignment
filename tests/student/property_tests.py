"""Deterministic independent oracles and scale tests for Assignment 4.
Run from repo root: python tests/student/property_tests.py
"""
import functools
import json
import random
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from public_validate import check
R = random.Random(20261107)


def lcs_length(a, b):
    dp = [[0] * (len(b) + 1) for _ in range(len(a) + 1)]
    for i, c in enumerate(a, 1):
        for j, d in enumerate(b, 1):
            dp[i][j] = dp[i-1][j-1] + 1 if c == d else max(dp[i-1][j], dp[i][j-1])
    return dp[-1][-1]


def merge_cost(sizes):
    @functools.lru_cache(None)
    def solve(l, r):
        if l >= r:
            return 0
        return sum(sizes[l:r+1]) + min(solve(l,k) + solve(k+1,r) for k in range(l,r))
    return solve(0, len(sizes)-1)


def tree_optimal(weights, edges):
    best = 0
    for mask in range(1 << len(weights)):
        if any((mask >> u & 1) and (mask >> v & 1) for u,v in edges):
            continue
        best = max(best, sum(w for i, w in enumerate(weights) if mask >> i & 1))
    return best


def find_all(s, p):
    return [i for i in range(len(s)+1) if s.startswith(p, i)]


def suffix_oracle(text):
    sa = sorted(range(len(text)), key=lambda i: text[i:])
    lcp = [0] * len(text)
    for j in range(1,len(text)):
        a,b = sa[j-1],sa[j]
        while a+lcp[j]<len(text) and b+lcp[j]<len(text) and text[a+lcp[j]]==text[b+lcp[j]]:
            lcp[j] += 1
    longest = max(lcp,default=0)
    repeat = text[sa[lcp.index(longest)]:sa[lcp.index(longest)]+longest] if longest else ''
    return {'sa':sa,'lcp':lcp,'repeated':repeat}


def aho_oracle(patterns, texts):
    return [[len(find_all(t, p)) for p in patterns] for t in texts]


def case(name, task, inp, expected, defect):
    return {'name': name, 'input': {'task':task,**inp}, 'output':expected, 'defect': defect}


def random_cases():
    cases=[]
    alphabet='abcA !'
    for i in range(85):
        a=''.join(R.choices(alphabet,k=R.randrange(0,12)))
        b=''.join(R.choices(alphabet,k=R.randrange(0,12)))
        cases.append(case(f'lcs-random-{i}','lcs',{'a':a,'b':b},
                     {'length':lcs_length(a,b),'sequence':''},'DP optimum and subsequence witness'))
    for i in range(70):
        sizes=[R.randrange(0,10**9+1) for _ in range(R.randrange(0,9))]
        cases.append(case(f'merge-random-{i}','merge',{'sizes':sizes},
                          {'cost':merge_cost(sizes),'plan':[]},'Interval optimum and valid chronological plan'))
    for i in range(75):
        n=R.randrange(0,11)
        edges=[[v,R.randrange(v)] for v in range(1,n)]
        R.shuffle(edges)
        weights=[R.randrange(-10,11) for _ in range(n)]
        cases.append(case(f'tree-random-{i}','tree',{'n':n,'weights':weights,'edges':edges},
                          {'weight':tree_optimal(weights,edges),'selected':[]},'Independent brute-force tree set'))
    for i in range(85):
        text=''.join(R.choices(alphabet,k=R.randrange(0,70)))
        pattern=''.join(R.choices(alphabet,k=R.randrange(0,17)))
        expected={'positions':find_all(text,pattern)}
        for algo in ('naive','kmp','rk'):
            cases.append(case(f'{algo}-random-{i}',algo,{'text':text,'pattern':pattern},
                              expected,'All starts, overlaps, empty pattern and collisions'))
    for i in range(60):
        text=''.join(R.choices('abC!',k=R.randrange(0,45)))
        cases.append(case(f'suffix-random-{i}','suffix',{'text':text},
                          suffix_oracle(text),'Full suffix sort and adjacent Kasai LCP'))
    for i in range(65):
        pats=[''.join(R.choices('ab!',k=R.randrange(1,6))) for _ in range(R.randrange(0,12))]
        txts=[''.join(R.choices('ab!',k=R.randrange(0,35))) for _ in range(R.randrange(0,6))]
        cases.append(case(f'aho-random-{i}','aho',{'patterns':pats,'texts':txts},
                          {'counts':aho_oracle(pats,txts)},'All dictionary positions and independent text state'))
    return cases


def scale_cases():
    n=100000
    distinct = [''.join(R.choices('bcdefghijklmnopqrstuvwxyz', k=10)) for _ in range(10000)]
    cases = [
       case('scale-lcs-1000x1000','lcs',{'a':'a'*1000,'b':'a'*1000},
            {'length':1000,'sequence':''},'Maximum LCS DP dimensions'),
       case('scale-merge-200-zeros','merge',{'sizes':[0]*200},
            {'cost':0,'plan':[]},'Maximum interval DP and n-1 merge plan'),
       case('scale-merge-200-billions','merge',{'sizes':[10**9]*200},
            {'cost':merge_cost(tuple([10**9]*200)),'plan':[]},'64-bit total and maximum intervals'),
       case('scale-tree-100k-chain','tree',{'n':n,'weights':[1]*n,'edges':[[i,i+1] for i in range(n-1)]},
            {'weight':50000,'selected':[]},'Iterative traversal avoids stack overflow'),
       case('scale-tree-100k-large-sum','tree',{'n':n,'weights':[10**9]*n,'edges':[[i,i+1] for i in range(n-1)]},
            {'weight':50000*10**9,'selected':[]},'64-bit weight and iterative 100k tree'),
       case('scale-kmp-200k-100k','kmp',{'text':'a'*200000,'pattern':'a'*100000},
            {'positions':list(range(100001))},'Maximum KMP input and overlapping starts'),
       case('scale-rk-10k-2k','rk',{'text':'a'*10000,'pattern':'a'*2000},
            {'positions':list(range(8001))},'Hash candidates verified at legal product limit'),
       case('scale-suffix-50k','suffix',{'text':'a'*50000},
            {'sa':list(range(49999,-1,-1)),'lcp':list(range(50000)),
             'repeated':'a'*49999},'Maximum suffix array / LCP / repeated witness'),
       case('scale-aho-10000-100texts','aho',{'patterns':['a'*10]*10000,'texts':['a'*2000]*100},
            {'counts':[[1991]*10000 for _ in range(100)]},'Million output counts; duplicate terminal aggregation'),
       case('scale-aho-100k-trie','aho',{'patterns':distinct,'texts':['a'*200000]},
            {'counts':[[0]*10000]},'Near 100k distinct trie nodes and maximum text'),
    ]
    return cases


def execute(cases, timeout=20):
    payload=''.join(json.dumps(c['input'],separators=(',',':'))+'\n' for c in cases)
    command=['java','-Xmx384m','-cp',str(ROOT/'Java'/'build'),'Main']
    start=time.perf_counter()
    result=subprocess.run(command,input=payload,text=True,capture_output=True,encoding='utf-8',timeout=timeout)
    duration=time.perf_counter()-start
    if result.returncode:
        raise RuntimeError(result.stderr[-3000:])
    lines=result.stdout.splitlines()
    if len(lines)!=len(cases):
        raise AssertionError(f'Wrong JSONL line count: {len(lines)} vs {len(cases)}')
    for index,(c,line) in enumerate(zip(cases,lines),1):
        try:
            actual=json.loads(line)
        except ValueError as exc:
            raise AssertionError(f'Non-JSON output at {index}') from exc
        if not check(c['input'],actual,c['output']):
            raise AssertionError(f"Failed {index}: {c['name']}\nexpected: {str(c['output'])[:800]}\nactual: {str(actual)[:800]}")
    return duration


def main():
    subprocess.run(['javac','--release','17','-encoding','UTF-8','-d',str(ROOT/'Java'/'build'),
                    *map(str,sorted((ROOT/'Java'/'source').glob('*.java')))],check=True)
    fixtures=json.loads((ROOT/'tests'/'student'/'cases.json').read_text(encoding='utf-8'))
    randoms=random_cases()
    mixed=fixtures+randoms+fixtures[::-1]
    random_seconds=execute(mixed)
    scales=scale_cases()
    # Each maximum-size case is checked independently; public official batches are separate.
    timings=[]
    for c in scales:
        seconds=execute([c])
        timings.append(f"PASS {c['name']}: {seconds:.3f}s")
    report=f"PASS own fixtures: {len(fixtures)}\nPASS randomized + repeated mixed: {len(mixed)} total requests, seed 20261107, {random_seconds:.3f}s\n"+'\n'.join(timings)+'\n'
    (ROOT/'tests'/'student'/'results.txt').write_text(report,encoding='utf-8')
    print(report,end='')

if __name__ == '__main__':
    main()
