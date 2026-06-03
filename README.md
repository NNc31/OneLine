# OneLine

Anonymous magic-link chat with end-to-end encryption. Create a room, share the link, anyone with it can join. No accounts, no email, no phone.

## Highlights

- **End-to-end encryption.** Messages are encrypted and decrypted client-side.
  A 256-bit secret is generated in the browser and lives only in the URL fragment (`/c/{publicId}#{secret}`).
  Client derives via HKDF-SHA256 two independent values: an **auth token** and an **AES-256-GCM message key**. 
  Auth token is sent to the server for authentication and routing, message key stays in the browser for encryption and decryption.
- **"Session chats" live in the browser.** JS layer saves `{publicId, secret, displayName}` to `localStorage`. The `/me` page lists stored chats.
- **Sliding, idempotent session.** Random session token generated during joining, 
  stored hashed in the DB and handed back as an `HttpOnly` cookie.
  Every authenticated request re-issues the cookie with a fresh `Max-Age`,
  so an active participant is never logged out at the 30-day mark.
  Re-opening a chat in which you already hold a session reuses the same participant.
- **Redis-backed broadcast and rate limits.** A Redis pub/sub channel fans messages out across app instances.
  The local in-process broker handles per-instance STOMP delivery.
  Rate limiting uses Bucket4j token buckets stored in Redis, the limits are atomic and shared across instances.
- **Anonymous participants with a "presence-lite" name policy.** No accounts. User picks a display name before joining the chat. 
  The same name is allowed in the same chat only if the previous holder has been inactive for more than the configured activity window.
- **Server-rendered UI.** Thymeleaf for HTML, regular JS only for the STOMP client and small features. No build step.
- **Postgres + Flyway** for persistence and schema versioning.
- **Observability.** Prometheus + Micrometer (`/actuator/prometheus`), custom counters for messages sent, attachments uploaded, rate-limits rejections and WS connections.

## Chat features
- **End-to-end encrypted attachments.** Files are encrypted in the browser with a random per-file key. 
  The ciphertext blob is uploaded directly to MinIO via short-lived presigned URLs. 
  Image previews are rendered inline from decrypted blobs.
- **Self-destruct messages.** Each chat can set a TTL (minutes/hours/days). 
  The frontend filters expired messages on render. A backend removes them and their attachment objects in MinIO periodically.
- **Presence with typing indicators.** Heartbeats over STOMP. Stale participants drop after a configurable window.

## Stack

- Java 21, Spring Boot 4.0, Spring Security, Spring WebSocket (STOMP)
- Hibernate ORM 7, Postgres 16, Flyway 11, Redis 7, MinIO
- Bucket4j (Redis-backed), Micrometer + Prometheus
- Thymeleaf, minimal CSS, self-hosted `@stomp/stompjs`

## Running locally

Requirements: Docker and a Java 21 JDK.

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
| `oneline.session.cookie-max-age`      | Lifetime of the session cookie in the browser                            |
| `oneline.rate-limit.*`                | Bucket capacity and refill period per endpoint group                     |
| `oneline.retention.inactivity-window` | How long a chat with no recent activity is kept before automatic removal |
| `oneline.retention.cron` and `.zone`  | Cron expression and time zone for the cleanup job                        |
| `oneline.storage.max-file-size`       | Attachment size cap                                                      |
| `oneline.storage.presign-ttl`         | Lifetime of presigned upload/download URLs                               |
| `oneline.storage.unconfirmed-ttl`     | Lifetime of reserved, not uploaded attachment row                        |

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
