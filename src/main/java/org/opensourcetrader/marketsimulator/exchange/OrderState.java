package org.opensourcetrader.marketsimulator.exchange;

public interface OrderState {
    String getOrderId();
    double getLeavesQty();
    double getQty();
}
