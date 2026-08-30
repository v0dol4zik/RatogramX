# Security policy

## Supported versions

Until the first stable Ratogram X release, security fixes are applied only to
the current `main` branch. After public releases begin, this file will list the
supported release lines.

## Reporting a vulnerability

Do not open a public issue for a vulnerability or include exploit details in a
public discussion.

Use GitHub's **Report a vulnerability** form in the Security tab of the
Ratogram X repository. Include:

- the affected Ratogram X commit or release;
- Android version, device architecture, and installation source;
- reproduction steps;
- expected impact;
- logs or proof of concept with private data removed;
- whether the issue also reproduces in Telegram X or another TDLib client.

If private vulnerability reporting is temporarily unavailable, open a public
issue containing no sensitive details and ask the maintainer for a private
contact channel.

## Upstream security issues

Ratogram X relies on Telegram X and TDLib for security-critical Telegram
functionality. Issues that affect Telegram infrastructure, the Telegram
protocol, or TDLib itself should also be reported through the official
[Telegram bug bounty program](https://core.telegram.org/bug-bounty) or the
relevant upstream security channel.

We will coordinate fixes and attribution where possible. Please allow a
reasonable time for investigation before public disclosure.
