# Contributing

Blowhard is developed in small, independently reviewable increments. Each pull request should
make one architectural or product change and include the tests and documentation needed to explain
it.

## Local setup

Install JDK 21, Android SDK 36, and Gitleaks 8.30 or newer. Set the Android SDK path through
`ANDROID_HOME` or an ignored `local.properties` file:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

Never commit credentials. Use ignored local environment files or operating-system credential
storage when provider integration is introduced.

## Before opening a pull request

Format the project and run the complete local gate:

```shell
./gradlew spotlessApply
./gradlew qualityCheck
```

Public API changes must be intentional. If a reviewed contract change makes
`checkKotlinAbi` fail, inspect the diff before updating the baseline with:

```shell
./gradlew updateKotlinAbi
```

Commit the resulting ABI snapshot with the contract change. Do not regenerate a snapshot merely
to silence an unexplained failure.

## Pull requests

- Keep production classes and files focused; split unrelated behavior into later changes.
- Prefer platform-neutral contracts and adapters over platform checks in shared code.
- Add deterministic tests for behavior, edge cases, and safely classified failures.
- Explain security implications and avoid logging prompts, credentials, or provider bodies.
- Record architectural decisions that would otherwise be rediscovered during review.
- Include exact verification commands and acknowledge known limitations.
