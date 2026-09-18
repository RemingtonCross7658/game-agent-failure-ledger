package dev.infrai.game.web;

import dev.infrai.game.client.InfraiErrorsClient.InfraiException;
import dev.infrai.game.domain.ModerationQueueService;
import dev.infrai.game.domain.ModerationQueueService.AgentFailure;
import dev.infrai.game.domain.ModerationQueueService.QueueDecision;

import java.io.IOException;

public final class FailureTrackingController {
    private final ModerationQueueService service;

    public FailureTrackingController(ModerationQueueService service) { this.service = service; }

    public ApiResponse track(AgentFailure failure) {
        try {
            QueueDecision decision = service.trackFailure(failure);
            return new ApiResponse(202, decision.queue(), decision.requiresHumanReview());
        } catch (InfraiException e) {
            int status = e.statusCode() >= 400 && e.statusCode() < 500 ? e.statusCode() : 502;
            return new ApiResponse(status, e.code(), false);
        } catch (IOException e) {
            return new ApiResponse(502, "tracking transport error", false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ApiResponse(503, "tracking interrupted", false);
        }
    }

    public record ApiResponse(int status, String result, boolean requiresHumanReview) {}
}
