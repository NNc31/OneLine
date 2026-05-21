# OneLine

Anonymous magic-link chat. Create a room, share the link, and anyone with it can join. No accounts, no email, no phone.

## Highlights

- **End-to-end encryption.** Messages are encrypted and decrypted client-side.
  A 256-bit secret is generated in the browser and lives only in the URL fragment (`/c/{publicId}#{secret}`).
  Client derives via HKDF-SHA256 two independent values: an **auth token** and an **AES-256-GCM message key**. 
  Auth token is sent to the server for authentication and routing, message key stays in the browser for encryption and decryption.
- **"Session chats" live in the browser.** JS layer saves `{publicId, secret, displayName}` to `localStorage`.
  `/me` page renders list of stored chats.
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

## Stack

- Java 21, Spring Boot 4.0, Spring Security, Spring WebSocket (STOMP)
- Hibernate ORM 7, Postgres 16, Flyway 11, Redis 7
- Thymeleaf, minimal CSS, `@stomp/stompjs`

## Running locally

Requirements: Docker and a Java 21 JDK.

1. Clone the repo and `cd` into it.
2. Run the following to start Postgres, Redis and OneLine.
```bash
# Postgres + Redis + App
docker compose -f docker/docker-compose.yml up -d --build
```

3. Open <http://localhost:8080>, hit **Create chat**, share the resulting URL.

Configuration properties can be found in [`src/main/resources/application.yaml`](src/main/resources/application.yaml):

| Key                                                         | Purpose                                                                  |
|-------------------------------------------------------------|--------------------------------------------------------------------------|
| `oneline.participant.activity-window`                       | How long a display name stays reserved after the holder went idle        |
| `oneline.session.cookie-max-age`                            | Lifetime of the session cookie in the browser                            |
| `oneline.rate-limit.create-chat.*`, `.join.*`, `.message.*` | Bucket capacity and refill period, for create, join and message sending  |
| `oneline.retention.inactivity-window`                       | How long a chat with no recent activity is kept before automatic removal |
| `oneline.retention.cron` and `.zone`                        | Cron expression and time zone for the cleanup job                        |

## What this is not

- The server still serves the JavaScript that performs the encryption. A compromised origin could leak plaintext or keys.
- E2E protects data in transit and on the server, not a compromised endpoint.
  Malware or a malicious browser extension on a participant's device can read the decrypted messages, the secret in the URL, or the secrets cached in `localStorage`.
- Not built for many users in one chat. The Redis pub/sub channel is enough for the current scale target, 
  but the in-process broker still holds active subscribers per instance.
- Redis is required. Both the cross-instance broadcast and the rate limiter run through it.
- No accounts, no recovery. By design.

## License

See [`LICENSE`](LICENSE).
