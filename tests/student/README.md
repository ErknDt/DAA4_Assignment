# Student-defined tests

- `cases.json`: 25 manually chosen boundary cases with input, expected values and defects.
- `property_tests.py`: seeded (20261107) independent reference oracles, 660 mixed requests and 10 maximum-size cases. Run with `python tests/student/property_tests.py`.
- `results.txt`: actually recorded passing test results in the assistant's local environment.
- `Benchmark.java`: independent correctness check and benchmark of three search methods at three random and three repetitive sizes. Run commands in project README.
- `benchmark.csv`: actual raw microsecond-per-call samples (three for each of 18 search method/case pairs).

Please re-run these tests on your own system and keep your own evidence if a new commit is made.
