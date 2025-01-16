package org.opensourcetrader.fixsimulator.exchange;

public interface OrderState {
    String getOrderId();
    double getLeavesQty();
    double getQty();
}
