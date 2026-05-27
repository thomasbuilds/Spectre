# Contributing

Contributions are greatly appreciated. Hands-on testing happens on a single device, so contributors running Spectre on other phones, carriers, and RF environments are the single best way for it to improve. Pull requests are very welcome, and bug reports about device-specific radio behavior are especially useful.

Feature requests from the community are equally welcome. If there is something Spectre should surface or do, please open an issue and describe it.

## Commit messages

Start the summary line with an uppercase letter.

A commit message exists to explain the justification and background for a change that are not already clear from the code itself, for the people reading the history later (including your future self). Get straight to that point, for both the reasoning and the background.

- Trivial or obvious changes need only a summary line. Do not pad them with detail.
- Write a body when the reason for a change is not obvious, or when the change is invasive or complex. Explain why it is being made and the background that matters, such as the issue it fixes or a measurement that motivated it.
- Leave out the obvious. Do not explain how well-known technologies work, and do not restate what the diff already shows. Details about how something is implemented usually belong in the code, not the message.
- Be accurate and proportional. A wrong detail misleads whoever reads the history later and adds noise to review, so include only what you are sure of. A long message implies there is a lot to understand, so keep its length matched to the change.

## Code comments

Prefer code that explains itself. Most of the time clarity comes from good function and variable names and from splitting logic sensibly, not from comments.

- Reach for a comment only when naming and structure cannot make the intent clear.
- When a comment is needed, keep it to the minimum that quickly explains the point.
