package org.opensourcetrader.fixsimulator.exchange;

public interface Exchange {
    OrderBook getOrderBook(String instrument);
}
