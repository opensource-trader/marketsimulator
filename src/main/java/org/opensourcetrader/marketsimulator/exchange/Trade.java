package org.opensourcetrader.marketsimulator.exchange;

import java.math.BigDecimal;

public interface Trade {
    String getInstrument();

    String getClOrderId();

    BigDecimal getPrice();

    // TODO: Change to int if possible
    double getQuantity();

    Side getOrderSide();

    String getOrderId();

    String getTradeId();

    double getLeavesQty();

    double getCumQty();

}
