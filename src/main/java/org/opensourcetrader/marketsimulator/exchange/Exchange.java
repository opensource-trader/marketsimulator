package org.opensourcetrader.marketsimulator.exchange;

public interface Exchange {
    OrderBook getOrderBook(String instrument);
}
