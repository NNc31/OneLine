# OneLine

Anonymous magic-link chat. Create a room, share the link, and anyone with it can join. No accounts, no email, no phone.

## Highlights

- **End-to-end encryption.** Messages are encrypted in the browser with WebCrypto.
  The server stores and routes opaque ciphertext bytes and never sees plaintext.
  The chat token lives in the URL fragment only (`/c/{publicId}#{chatToken}`) – browser never sends the `chatToken` to the server.
  The database stores only an SHA-256 hash of the token for routing authorization.
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
- Not built for many users in one chat. The Redis pub/sub channel is enough for the current scale target, 
  but the in-process broker still holds active subscribers per instance.
- Redis is required. Both the cross-instance broadcast and the rate limiter run through it.
- No accounts, no recovery. By design.

## License

See [`LICENSE`](LICENSE).
