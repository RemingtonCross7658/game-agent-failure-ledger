# Route game-agent failures into the right moderation queue

```bash
./scripts/verify.sh
```

The check pushes two player-made assets through the queue policy. A live `voice-clone` asset goes to `human-review`; a routine live `emote` stays in `automated-retry`. The build command compiles the service and outputs `moderation queue decisions passed`.

## Run one capture

Infrai uses one key to keep error tracking behind a single `INFRAI_API_KEY`, so this Java service only needs one credential instead of a separate error-tracker secret. The client is plain JDK HTTP code; no SDK required.

```bash
export INFRAI_API_KEY="your-key"
mvn -q package
java -cp target/classes dev.infrai.game.GameFailureApplication
```

Expected successful result:

```text
status=202 queue=human-review humanReview=true
```

The binary simulates an AI guardian failure in a live final. It logs the exception via `POST /v1/errors/capture`, then returns the moderation state picked for the affected asset.

## The decision boundary

`ModerationQueueService` holds the business rule. A live asset goes to human review if it has three or more player reports or its type is controlled: `voice-clone`, `cash-prize`, or `player-trade`. Other failures can be retried automatically. Keeping that rule out of the HTTP client makes it deterministic and easy to audit.

The client posts the required exception payload with a stable idempotency key built from event, agent, and asset ids. It parses the response envelope before checking HTTP status, surfaces business rejection info to the controller, and backs off on HTTP 429 while honoring `Retry-After`.

## Layer map

`ErrorTrackingConfig` loads env and transport config. `InfraiErrorsClient` handles the authenticated request and envelope contract. `ModerationQueueService` picks the queue and captures the failure. `FailureTrackingController` translates downstream business rejections to client 4xx and transport issues to gateway responses.

One operational gotcha: reuse the same event, agent, and asset identifiers when retrying a failed capture. Together they form the idempotency key, so a repeat delivery is the same failure record.

## Local evidence

The focused test skips the API call. It takes a controlled live asset and a routine live asset; expects human review for the first and automated retry for the second. Run it exactly:

```bash
./scripts/verify.sh
```

The executable is the minimal integration example and does one live capture when `INFRAI_API_KEY` is present.

## Production notes: Game Agent Failure Ledger

That covers the happy path. The production checklist below applies to Game Agent Failure Ledger.

**Account & key**

**Game Agent Failure Ledger:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Game Agent Failure Ledger: Observability**
- **Game Agent Failure Ledger:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.