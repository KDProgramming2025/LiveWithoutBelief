# Benchmark Module

Provides macrobenchmark tests (startup, future scroll/path benchmarks) for the `app` module.

## Run Locally

```
./gradlew :benchmark:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=info.lwb.benchmark.StartupBenchmark
```

To use a managed virtual device:
```
./gradlew :benchmark:pixel6Api34DebugAndroidTest
```

## Output
Results appear under `benchmark/build/outputs/connected_android_test_additional_output/` and in Android Studio's test window.

## Next Steps
- Add Baseline Profile generation test
- Add scroll benchmark for Reader list once implemented
- Wire results export into CI (optional future task)
