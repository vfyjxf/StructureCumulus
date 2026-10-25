package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.Severity;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplyTest {

    private static final BindingKey<Integer> COUNT = BindingKey.of("test:count", Integer.class);

    @Test
    void combineFollowsTheAttemptPrecedenceOverEveryPair() {
        Reply[] replies = {Reply.match(), Reply.mismatch(finding(1)), Reply.unreadable(finding(2)),
                Reply.abstain()};
        int[] ranks = {0, 2, 3, 1};
        for (int left = 0; left < replies.length; left++) {
            for (int right = 0; right < replies.length; right++) {
                Reply combined = Reply.combine(replies[left], replies[right]);
                assertEquals(Math.max(ranks[left], ranks[right]), rank(combined),
                        "combine(" + replies[left] + ", " + replies[right] + ")");
            }
        }
    }

    @Test
    void combineUnionsTheFindingsOfOneRank() {
        Reply combined = Reply.combine(Reply.mismatch(finding(1)), Reply.mismatch(finding(2)));

        assertTrue(combined instanceof Reply.Mismatch);
        assertEquals(2, ((Reply.Mismatch) combined).findings().size());
    }

    @Test
    void combineMergesTheBindingsOfTwoMatches() {
        Reply combined = Reply.combine(Reply.match(Bindings.of(COUNT, 1)), Reply.match());

        assertTrue(combined instanceof Reply.Match);
        assertEquals(Integer.valueOf(1), ((Reply.Match) combined).bindings().get(COUNT));
    }

    @Test
    void aRefutationWithoutEvidenceIsUnrepresentable() {
        assertThrows(IllegalArgumentException.class, () -> Reply.mismatch(FindingBundle.empty()));
        assertThrows(IllegalArgumentException.class, () -> Reply.unreadable(FindingBundle.empty()));
        assertTrue(Reply.abstain().equals(new Reply.Abstain()));
    }

    private static int rank(Reply reply) {
        if (reply instanceof Reply.Unreadable) {
            return 3;
        }
        if (reply instanceof Reply.Mismatch) {
            return 2;
        }
        return reply instanceof Reply.Abstain ? 1 : 0;
    }

    static Finding finding(int number) {
        return Finding.builder(FindingCode.of(FindingCode.Family.EXP, number))
                .severity(Severity.ERROR)
                .message("finding " + number)
                .stage(Stage.FORM)
                .build();
    }
}
