package org.opensourcetrader.marketsimulator.exchange.impl;

import org.opensourcetrader.marketsimulator.exchange.Exchange;
import org.opensourcetrader.marketsimulator.exchange.OrderBook;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ExchangeImpl implements Exchange {

    Map<String, OrderBook> instrumentToOrderbook = new HashMap<>();

    @Override
    public synchronized OrderBook getOrderBook(String instrument) {
        return instrumentToOrderbook.computeIfAbsent(instrument, OrderBookImpl::new);
    }
}
