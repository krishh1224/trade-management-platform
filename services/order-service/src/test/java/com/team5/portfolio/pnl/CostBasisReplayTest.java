package com.team5.portfolio.pnl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.team5.portfolio.pnl.CostBasisReplay.Fill;
import com.team5.portfolio.pnl.CostBasisReplay.Holding;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CostBasisReplayTest {

    @Test
    void aSellLeavesTheAverageCostUnchangedSoLaterSalesUseTheSameCost() {
        Map<Long, BigDecimal> costs = CostBasisReplay.averageCostAtSale(
            List.of(buy(1, 20, "1500"), sell(2, 5, "1550"), sell(3, 5, "1600")), new Holding(10, bd("1500")));

        assertEquals(bd("1500.0000"), costs.get(2L));
        assertEquals(bd("1500.0000"), costs.get(3L));
    }

    @Test
    void aBuyReweightsTheAverageBeforeALaterSale() {
        // 10 @ 100, then 10 @ 200 -> 150, then sell: the cost at sale is 150
        Map<Long, BigDecimal> costs = CostBasisReplay.averageCostAtSale(
            List.of(buy(1, 10, "100"), buy(2, 10, "200"), sell(3, 5, "300")), new Holding(15, bd("150")));

        assertEquals(bd("150.0000"), costs.get(3L));
    }

    @Test
    void aSaleBeforeALaterBuyIsPricedAtTheCostItHadThen() {
        // sold at 100 average, then a dearer buy lifted the average to 150: the sale keeps the old cost
        Map<Long, BigDecimal> costs = CostBasisReplay.averageCostAtSale(
            List.of(buy(1, 10, "100"), sell(2, 5, "130"), buy(3, 5, "250")), new Holding(10, bd("175")));

        assertEquals(bd("100.0000"), costs.get(2L));
    }

    @Test
    void anOpeningPositionLoadedWithoutAnOrderIsRecoveredFromTheCurrentHolding() {
        // account 102 in the seed data: HDFCBANK sold 20, no buy order, 75 still held at 1650
        Map<Long, BigDecimal> costs = CostBasisReplay.averageCostAtSale(List.of(sell(506, 20, "1725")),
            new Holding(75, bd("1650")));

        assertEquals(bd("1650.0000"), costs.get(506L));
    }

    @Test
    void theCurrentHoldingOutranksAnOrderHistoryThatDoesNotMatchIt() {
        // account 106 in the seed data: orders say buy 20 @1650 then sell 10, but 80 are held at 1600
        Map<Long, BigDecimal> costs = CostBasisReplay.averageCostAtSale(
            List.of(buy(513, 20, "1650"), sell(514, 10, "1700")), new Holding(80, bd("1600")));

        assertEquals(bd("1600.0000"), costs.get(514L));
    }

    @Test
    void aFullySoldPositionIsReplayedFromItsFirstBuy() {
        Map<Long, BigDecimal> costs = CostBasisReplay.averageCostAtSale(
            List.of(buy(1, 10, "100"), buy(2, 10, "120"), sell(3, 20, "140")), null);

        assertEquals(bd("110.0000"), costs.get(3L));
    }

    @Test
    void aSoldOutPositionStartsAgainAtTheNextBuyPrice() {
        Map<Long, BigDecimal> costs = CostBasisReplay.averageCostAtSale(
            List.of(buy(1, 10, "100"), sell(2, 10, "110"), buy(3, 4, "300"), sell(4, 2, "320")), new Holding(2, bd("300")));

        assertEquals(bd("100.0000"), costs.get(2L));
        assertEquals(bd("300.0000"), costs.get(4L));
    }

    @Test
    void theAverageIsRoundedToFourPlacesLikeTheExecutor() {
        Map<Long, BigDecimal> costs = CostBasisReplay.averageCostAtSale(
            List.of(buy(1, 1, "100"), buy(2, 2, "101"), sell(3, 1, "120")), new Holding(2, bd("100.6667")));

        assertEquals(bd("100.6667"), costs.get(3L));
    }

    @Test
    void aSaleTheHistoryCannotExplainIsLeftOutRatherThanGuessed() {
        // nothing held now and no buy before the sale
        Map<Long, BigDecimal> costs = CostBasisReplay.averageCostAtSale(List.of(sell(1, 5, "100")), null);

        assertTrue(costs.isEmpty());
    }

    @Test
    void aSaleOfMoreThanWasHeldIsLeftOutButLaterExplainedSalesStillResolve() {
        Map<Long, BigDecimal> costs = CostBasisReplay.averageCostAtSale(
            List.of(sell(1, 50, "100"), buy(2, 10, "80"), sell(3, 5, "90")), null);

        assertFalse(costs.containsKey(1L));
        assertEquals(bd("80.0000"), costs.get(3L));
    }

    @Test
    void buysAloneBookNothing() {
        assertTrue(CostBasisReplay.averageCostAtSale(List.of(buy(1, 10, "100")), new Holding(10, bd("100"))).isEmpty());
    }

    private static Fill buy(long id, long quantity, String price) {
        return new Fill(id, false, quantity, bd(price));
    }

    private static Fill sell(long id, long quantity, String price) {
        return new Fill(id, true, quantity, bd(price));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
