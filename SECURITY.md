# Security policy

Ashva is a development alpha, not an audited or production-supported service. Security fixes target the current source version; older alpha versions have no maintenance guarantee.

## Reporting

Do not post vulnerability details, private games, credentials or unredacted logs in public issues. After publication, use GitHub's **Security → Report a vulnerability** if the owner has enabled private vulnerability reporting. If that option is unavailable, open a minimal issue asking for a private reporting channel **without exploit details**, then wait for the maintainer's response. There is no guaranteed response time or bug bounty.

Include the version, affected input/screen, impact and a minimal synthetic reproduction through the private channel. Never use another person's data or attack a third-party chess provider. Do not rotate or revoke credentials on someone else's behalf.

## Current security boundaries

- No account, server, cloud sync, analytics or engine service is implemented. Release code does not request internet permission. Debug tooling can add network permission; debug APKs are for local testing only.
- Lessons, attempts and source packs are local SQLite data. Automatic cloud/device-transfer backups are excluded. Uninstall/clear-data destroys local study history; there is no export/recovery feature yet.
- PGN/FEN, packs and manifests have explicit limits and legal/checksum/schema validation, but this is not a formal parser-security or application audit.
- APK asset packs have trusted manifest hashes and atomic installs. Checksums establish expected bytes, not independent source authenticity or chess truth.
- No release keystore or production signing configuration is supplied. Do not distribute a debug APK as a production release.
- CI uses hosted disposable runners, read-only repository permissions, pinned actions, no persisted checkout credentials and no publication steps. Never add pull_request_target execution of untrusted code, production secrets or self-hosted public-PR runners.

Directory scanning cannot find deleted secrets in Git history or private information inside screenshots. If a real credential was ever exposed, treat it as compromised: the owner must revoke/rotate it and assess history and copies. Removing a file or adding .gitignore alone is insufficient.
