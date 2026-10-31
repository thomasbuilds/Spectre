# Contributing

Contributions are greatly appreciated. Hands-on testing happens on a single device so contributors running Spectre on other phones, carriers, and RF environments are the best way for it to improve. Pull requests are very welcome, and bug reports about device-specific radio behavior are especially useful.

Feature requests from the community are equally welcome. If there is something Spectre should surface or do please open an issue and describe it.

## Building

Building needs the Android SDK with platform 37 and JDK 25. Gradle itself must run on JDK 25 too because lint refuses to run on an older Gradle JVM. Before opening a pull request run the checks CI runs:

```
CI=true ./gradlew spotlessCheck :app:lintDebug :app:assembleRelease
```

Lint warnings fail `:app:lintDebug` and `CI=true` makes Kotlin warnings fail the build too, as in CI. Run `./gradlew spotlessApply` to fix the formatting that can be fixed automatically. CI does not lint the `release` script so run `shellcheck release` after changing it.

Keep builds reproducible since F-Droid rebuilds each release and must get the same APK apart from the signature. Don't add build timestamps or VCS details and keep `versionCode` and `versionName` as literal values because F-Droid reads them that way.

## Commit messages

Write the summary line in the imperative, starting with an uppercase letter, and make it ~50 characters.

A commit message explains the justification and background for a change where the code alone does not make them clear. Write it for the people reading the history later, including your future self, and get straight to the point.

- Trivial or obvious changes need only a summary line. Do not pad them with detail.
- Write a body when the reason for a change is not obvious, or when the change is invasive or complex. Explain why it is being made and the background that matters, such as the issue it fixes or a measurement that motivated it.
- Leave out the obvious. Do not explain how well-known technologies work, and do not restate what the diff already shows. Details about how something is implemented usually belong in the code, not the message.
- Be accurate and proportional. A wrong detail misleads whoever reads the history later and adds noise to review, so include only what you are sure of. A long message implies there is a lot to understand, so keep its length matched to the change.

## Code comments

Prefer code that explains itself. Most of the time clarity comes from good function and variable names and from splitting logic sensibly, not from comments.

- Reach for a comment only when naming and structure cannot make the intent clear.
- When a comment is needed, keep it to the minimum that quickly explains the point.

## Releases

`./release vX.Y.Z` releases the tip of `main` once its CI run has passed: it signs the APK, drafts the GitHub release, and reports what the commit still needs, such as the version or changelog. Publishing the draft locks its tag and assets. F-Droid builds the newest `vX.Y.Z` tag and ships the release's `spectre-vX.Y.Z.apk` only if its own build matches it apart from the signature.
