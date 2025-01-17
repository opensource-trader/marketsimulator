package org.opensourcetrader.marketsimulator.exchange;

import java.math.BigDecimal;

public interface Order {

    double getQuantity();

    double getRemainingQty();

    BigDecimal getPrice();

    String getClOrdId();

    Side getSide();

    String getOrderId();

}
