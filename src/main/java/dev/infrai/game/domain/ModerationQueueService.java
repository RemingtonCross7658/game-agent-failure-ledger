package dev.infrai.game.domain;

import dev.infrai.game.client.InfraiErrorsClient;
import dev.infrai.game.client.InfraiErrorsClient.InfraiException;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ModerationQueueService {
    private static final Set<String> CONTROLLED_ASSET_TYPES = Set.of("voice-clone", "cash-prize", "player-trade");
    private final InfraiErrorsClient errors;

    public ModerationQueueService(InfraiErrorsClient errors) { this.errors = errors; }

    public QueueDecision trackFailure(AgentFailure failure)
            throws IOException, InterruptedException, InfraiException {
        QueueDecision decision = decide(failure.asset());
        String exceptionPayload = "agent=" + failure.agentId()
                + "; event=" + failure.eventId()
                + "; asset=" + failure.asset().assetId()
                + "; type=" + failure.asset().assetType()
                + "; queue=" + decision.queue()
                + "; cause=" + failure.cause();
        String idempotencyKey = failure.eventId() + ":" + failure.agentId() + ":" + failure.asset().assetId();
        Map<String, Object> receipt = errors.capture(exceptionPayload, idempotencyKey);
        return new QueueDecision(decision.queue(), decision.requiresHumanReview(), receipt);
    }

    public static QueueDecision decide(PlayerAsset asset) {
        String type = asset.assetType().toLowerCase(Locale.ROOT);
        boolean review = asset.liveEvent() && (asset.reportCount() >= 3 || CONTROLLED_ASSET_TYPES.contains(type));
        return new QueueDecision(review ? "human-review" : "automated-retry", review, Map.of());
    }

    public record PlayerAsset(String assetId, String assetType, boolean liveEvent, int reportCount) {
        public PlayerAsset {
            if (assetId.isBlank() || assetType.isBlank() || reportCount < 0) {
                throw new IllegalArgumentException("Asset fields are invalid");
            }
        }
    }

    public record AgentFailure(String agentId, String eventId, PlayerAsset asset, String cause) {
        public AgentFailure {
            if (agentId.isBlank() || eventId.isBlank() || cause.isBlank()) {
                throw new IllegalArgumentException("Failure fields are invalid");
            }
        }
    }

    public record QueueDecision(String queue, boolean requiresHumanReview, Map<String, Object> captureReceipt) {}
}
