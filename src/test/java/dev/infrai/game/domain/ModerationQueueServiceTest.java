package dev.infrai.game.domain;

import dev.infrai.game.domain.ModerationQueueService.PlayerAsset;

public final class ModerationQueueServiceTest {
    public static void main(String[] args) {
        PlayerAsset liveControlledAsset = new PlayerAsset("ugc-2048", "voice-clone", true, 1);
        var controlledDecision = ModerationQueueService.decide(liveControlledAsset);
        require(controlledDecision.requiresHumanReview(), "controlled live asset must receive human review");
        require("human-review".equals(controlledDecision.queue()), "controlled asset queue mismatch");

        PlayerAsset routineAsset = new PlayerAsset("ugc-2049", "emote", true, 0);
        var routineDecision = ModerationQueueService.decide(routineAsset);
        require(!routineDecision.requiresHumanReview(), "routine asset should remain automated");
        require("automated-retry".equals(routineDecision.queue()), "routine asset queue mismatch");
        System.out.println("moderation queue decisions passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
