package dev.infrai.game;

import dev.infrai.game.client.InfraiErrorsClient;
import dev.infrai.game.config.ErrorTrackingConfig;
import dev.infrai.game.domain.ModerationQueueService;
import dev.infrai.game.domain.ModerationQueueService.AgentFailure;
import dev.infrai.game.domain.ModerationQueueService.PlayerAsset;
import dev.infrai.game.web.FailureTrackingController;

public final class GameFailureApplication {
    private GameFailureApplication() {}

    public static void main(String[] args) {
        ErrorTrackingConfig config = ErrorTrackingConfig.fromEnvironment();
        InfraiErrorsClient client = new InfraiErrorsClient(config);
        FailureTrackingController controller = new FailureTrackingController(new ModerationQueueService(client));

        PlayerAsset asset = new PlayerAsset("ugc-2048", "voice-clone", true, 1);
        AgentFailure failure = new AgentFailure("guardian-7", "live-final-42", asset, "policy model rejected output");
        FailureTrackingController.ApiResponse response = controller.track(failure);
        System.out.printf("status=%d queue=%s humanReview=%s%n",
                response.status(), response.result(), response.requiresHumanReview());
    }
}
