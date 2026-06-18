# OneLine

Anonymous magic-link chat with end-to-end encryption. Create a room, share the link, anyone with it can join. No accounts, no email, no phone.

## Highlights

- **End-to-end encryption.** Messages are encrypted and decrypted client-side.
  A 256-bit secret is generated in the browser and lives only in the URL fragment (`/c/{publicId}#{secret}`).
  Client derives via HKDF-SHA256 two independent values: an **auth token** and an **AES-256-GCM message key**. 
  Auth token is sent to the server for authentication and routing, message key stays in the browser for encryption and decryption.
- **"Session chats" live in the browser.** JS layer saves `{publicId, secret, displayName,  sessionToken, signing keys}` to `localStorage`. The `/me` page lists stored chats.
- **Per-chat session, no shared cookie.** Random session token generated on join, stored hashed in the DB.
  Lets you hold independent sessions in many chats at once. Re-opening a chat reuses the same participant.
- **Cryptographic message authorship (Ed25519).** Each participant holds a per-chat signing keypair.
  The private key never leaves the browser. Every message is signed inside the encrypted payload.
  Receivers verify the signature and pin each participant's public key on first use.
  A tampering server cannot forge or relabel authorship. A badge marks verified and unverified messages.
- **Redis-backed broadcast and rate limits.** A Redis pub/sub channel fans messages out across app instances.
  The local in-process broker handles per-instance STOMP delivery.
  Rate limiting uses Bucket4j token buckets stored in Redis, the limits are atomic and shared across instances.
- **Anonymous participants with a "presence-lite" name policy.** No accounts. User picks a display name before joining the chat. 
  The same name is allowed in the same chat only if the previous holder has been inactive for more than the configured activity window.
- **Server-rendered UI.** Thymeleaf for HTML, regular JS only for the STOMP client and small features. No build step.
- **Postgres + Flyway** for persistence and schema versioning.
- **Observability.** Prometheus + Micrometer (`/actuator/prometheus`), custom counters for messages sent, attachments uploaded, rate-limits rejections and WS connections.

## Chat features

- **End-to-end encrypted chunked attachments.** Files are split into 4 MB chunks and encrypted in the browser with a random per-file key. 
  The ciphertext is uploaded directly to MinIO via short-lived presigned URLs. 
  Image previews are rendered inline from decrypted blobs.
  A daily per-participant upload quota and a server kill switch guard against abuse. Drag-and-drop and clipboard paste supported.
- **Self-destruct messages.** Each chat can set a TTL (minutes/hours/days). 
  The frontend filters expired messages on render. A backend removes them and their attachment objects in MinIO periodically.
- **Presence with typing indicators.** Heartbeats over STOMP. Stale participants drop after a configurable window.

## Stack

- Java 25, Spring Boot 4.0, Spring Security, Spring WebSocket (STOMP)
- Hibernate ORM 7, Postgres 16, Flyway 11, Redis 7, MinIO
- Bucket4j (Redis-backed), Micrometer + Prometheus
- Thymeleaf, minimal CSS, self-hosted `@stomp/stompjs`
- Optional GraalVM native image build. See [`docs/NATIVE.md`](docs/NATIVE.md)

## Running locally

Requirements: Docker and a Java 25 JDK.

1. Clone the repo and `cd` into it.
2. Run the following to start Postgres, Redis, MinIO and OneLine.
```bash
# Postgres + Redis + MinIO + App
docker compose -f docker/docker-compose.yml up -d --build
```

Useful endpoints:
- App: <http://localhost:8080>
- MinIO console: <http://localhost:9001>
- Prometheus metrics: <http://localhost:8080/actuator/prometheus>

## Configuration

All settings live in [`src/main/resources/application.yaml`](src/main/resources/application.yaml).

| Env var                                                                             | Purpose                       |
|-------------------------------------------------------------------------------------|-------------------------------|
| `DB_URL`, `DB_USER`, `DB_PASSWORD`                                                  | Postgres connection           |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME`, `REDIS_PASSWORD`, `REDIS_SSL_ENABLED` | Redis connection              |
| `MINIO_ENDPOINT`                                                                    | In-cluster MinIO endpoint     |
| `MINIO_PUBLIC_ENDPOINT`                                                             | Browser-facing MinIO endpoint |
| `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`                              | MinIO credentials and bucket  |

Application-level:

| Key                                   | Purpose                                                                  |
|---------------------------------------|--------------------------------------------------------------------------|
| `oneline.participant.activity-window` | How long a display name stays reserved after the holder went idle        |
| `oneline.rate-limit.*`                | Bucket capacity and refill period per endpoint group                     |
| `oneline.retention.inactivity-window` | How long a chat with no recent activity is kept before automatic removal |
| `oneline.retention.cron` and `.zone`  | Cron expression and time zone for the cleanup job                        |
| `oneline.storage.max-file-size`       | Attachment size cap                                                      |
| `oneline.storage.presign-ttl`         | Lifetime of presigned upload/download URLs                               |
| `oneline.storage.unconfirmed-ttl`     | Lifetime of reserved, not uploaded attachment row                        |
| `oneline.attachments.enabled`         | Kill switch for uploads                                                  |
| `oneline.attachments.ttl`             | How long an attachment is kept before automatic removal                  |

## What this is not

- The server still serves the JavaScript that performs the encryption. A compromised origin could leak plaintext or keys.
- E2E protects data in transit and on the server, not a compromised endpoint.
  Malware or a malicious browser extension on a participant's device can read the decrypted messages, the secret in the URL, or the secrets cached in `localStorage`.
- Not built for many users in one chat. Single-instance broker with Redis fan-out is enough for the current scale target.
- Redis is required. Both the cross-instance broadcast and the rate limiter run through it.
- No accounts, no recovery. By design.
- MinIO single-node has no extra durability beyond the underlying disk

## License

See [`LICENSE`](LICENSE).
