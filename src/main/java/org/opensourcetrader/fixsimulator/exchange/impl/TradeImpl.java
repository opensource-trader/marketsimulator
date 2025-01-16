package org.opensourcetrader.fixsimulator.exchange.impl;

import lombok.Getter;
import org.opensourcetrader.fixsimulator.exchange.Side;
import org.opensourcetrader.fixsimulator.exchange.Trade;

import java.math.BigDecimal;
@Getter
public class TradeImpl implements Trade {
    private final String tradeId;
    private final String clOrderId;
    private final BigDecimal price;
    private final double quantity;
    private final String instrument;
    private final Side orderSide;
    private final String orderId;
    private final double leavesQty;
    private final double cumQty;

    public TradeImpl( String tradeId, String clOrderId, BigDecimal price,
                      double quantity, String instrument, Side orderSide,
                      String orderId,
                      double leavesQty,
                      double cumQty) {
        this.clOrderId = clOrderId;
        this.price = price;
        this.quantity = quantity;
        this.instrument = instrument;
        this.orderSide = orderSide;
        this.orderId = orderId;
        this.tradeId = tradeId;
        this.leavesQty = leavesQty;
        this.cumQty = cumQty;
    }


}
