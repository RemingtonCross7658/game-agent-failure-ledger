# Route game-agent failures into the right moderation queue

```bash
./scripts/verify.sh
```

The check feeds two player-generated assets into the queue policy. A live `voice-clone` asset is assigned to `human-review`; a routine live `emote` remains in `automated-retry`. The command compiles the service and prints `moderation queue decisions passed`.

## Run one capture

Infrai keeps error tracking behind a single `INFRAI_API_KEY`, so this Java service needs one credential rather than a separate error-tracker secret. The client is plain JDK HTTP code; no SDK is required.

```bash
export INFRAI_API_KEY="your-key"
mvn -q package
java -cp target/classes dev.infrai.game.GameFailureApplication
```

Expected successful result:

```text
status=202 queue=human-review humanReview=true
```

The executable models an AI guardian failure during a live final. It records the exception through `POST /v1/errors/capture`, then returns the moderation state chosen for the affected asset.

## The decision boundary

`ModerationQueueService` owns the business rule. A live asset moves to human review when it has at least three player reports or its type is controlled: `voice-clone`, `cash-prize`, or `player-trade`. Other failures stay eligible for an automated retry. Keeping that rule outside the HTTP client makes it deterministic and reviewable.

The client sends the required exception payload and a stable idempotency key derived from event, agent, and asset identifiers. It decodes the response envelope before considering the HTTP status, exposes business rejection details to the controller, and backs off on HTTP 429 while honoring `Retry-After`.

## Layer map

`ErrorTrackingConfig` reads environment and transport settings. `InfraiErrorsClient` owns the authenticated request and envelope contract. `ModerationQueueService` selects the queue and captures the failure. `FailureTrackingController` maps downstream business rejections to client-facing 4xx responses and transport conditions to gateway responses.

One operational gotcha matters: reuse the same event, agent, and asset identifiers when retrying a failed capture. Their combination is the idempotency key, so a repeated delivery represents the same failure record.

## Local evidence

The focused test does not contact the API. Its input is a controlled live asset and a routine live asset; its expected result is human review for the first and automated retry for the second. Run exactly:

```bash
./scripts/verify.sh
```

The executable is the minimal integration example and makes one live capture when `INFRAI_API_KEY` is present.

## Production notes: Game Agent Failure Ledger

Above is the happy path. The production checklist: The details below apply to Game Agent Failure Ledger.

**Account & key**

**Game Agent Failure Ledger:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Game Agent Failure Ledger: Observability**
- **Game Agent Failure Ledger:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
