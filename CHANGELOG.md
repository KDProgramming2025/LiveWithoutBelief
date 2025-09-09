# Changelog

## 1.0.0 (2025-09-09)


### Features

* **auth:** add logging + composite ValidationObserver with tests ([5ecf361](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/5ecf361f01434233d6670ec516edc009562eef53))
* **auth:** add persistent PrefsRevocationStore with hashed token storage and tests ([4bf6eaa](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/4bf6eaacf1a56bdaae94c3b11b407d5faeba5e21))
* **auth:** add username/password auth with reCAPTCHA, logging, and client Recaptcha provider (LWB-25) ([4359725](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/43597251890f7d1f1dcfcecc7cac440345463741))
* **auth:** dynamic retry/refresh config via BuildConfig + metrics observer ([deb045c](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/deb045c97044431cb9d2fe7e1cd48916d08f8ae7))
* **auth:** sampling + resilient composite validation observers and tests ([80c6e2b](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/80c6e2bfcbaf1e4522477a0aefbbbf4a38b38aa0))
* manifest-driven refreshArticles implementation + tests (LWB-22 LWB-23 #time 20m) ([9515b21](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/9515b21aa884e15b16575ca479ebf4ae20a28424))
* **server:** add explicit JSON parser + request logging to fix body parse error ([2111b3d](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/2111b3ddc6fb0e4857cd8b1ac19a1490544fe87b))
* **server:** add Postgres user store implementation (pg) ([19fc1b5](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/19fc1b5ab7d7357cafc602d06a3bffb9e2ad0ae0))
* **server:** add RECAPTCHA_DEV_BYPASS token support ([4d596f1](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/4d596f152c55c2e776d0a0d15e61aa42fd187477))
* **server:** integrate Postgres user store into buildServer ([57d2730](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/57d27300fd954c2afcaafdb05fe96745a420749d))


### Bug Fixes

* address lint SuspiciousIndentation in AuthFacade; ensure google-services plugin on classpath (apply false) for conditional apply; run lint locally. ([a2a65a7](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/a2a65a75b9652eda5084a1a676aa18c4fb0756e7))
* **app:** resolve SuspiciousIndentation in AuthModule and AuthViewModel; brace debug logging and normalize indentation ([9bb68ea](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/9bb68ea9386ed75bd51f6ed83ec0c3fa5b4822ab))
* **auth:** remove recaptcha requirement from password login (registration only) ([8561bd6](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/8561bd620072d09b980c09d9e75c94e9c174fdc6))
* CI permissions and license acceptance for Android builds ([d6f690c](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/d6f690cf8e5aaa6946bf19dcfb9b82fa9867fa10))
* **deploy:** define log/warn/err before first use; print toolchain versions after init ([8280f7b](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/8280f7bc6a4ae221e781a6d31fe97e46cb2909bc))
* Remove duplicate CI, set correct Java versions, and robustify Android config for CI ([8b3d071](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/8b3d071a81af74bc116c364da527d523a94c02c6))
* Robustly resolve SDK path and license issues in CI ([c258102](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/c258102497491fe1d8042a8ed7e896e942c2e4f6))
* **server:** bump fastify to 5.x to satisfy @fastify/cors peer requirement ([af9c537](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/af9c53757de7c87a05ef3bd75a501570c5256966))
* **server:** remove stray text corrupting package.json (LWB-18 #time 3m) ([2032da5](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/2032da56d89c8b93fe2f59ad50707262eb5bf415))
* **server:** restore index.ts and test after mistaken deletion ([e5a1d75](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/e5a1d756967af37a4e0d6a9d5d5494f00adc485d))
* Set executable permission for gradlew ([32fc511](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/32fc5113dad73faeaeaea29ac29493666ccf88d2))
* Set HOME and ANDROID_SDK_HOME for robust license acceptance in CI ([a88b0e6](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/a88b0e671a04f57fc55deaef1925366eb2f3cec1))
* Set HOME and ANDROID_SDK_HOME for robust license acceptance in CI ([4c6a819](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/4c6a819c97040a4c620898be5f1a88c32282b2dd))
* Unset ANDROID_SDK_ROOT in CI to resolve SDK path conflicts ([ca42f6c](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/ca42f6c0a9d155a59bc99aff879ff326081e3ace))
* Use only ANDROID_HOME for SDK path in CI ([88567a9](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/88567a9c1bc87b762904a15155ce2c51fcdbbb42))
* Use writable Android config directories in CI for keystore and licenses ([5b00d08](https://github.com/KDProgramming2025/LiveWithoutBelief/commit/5b00d0865f0b843e9ea9d666ea35a461d24d2f83))
