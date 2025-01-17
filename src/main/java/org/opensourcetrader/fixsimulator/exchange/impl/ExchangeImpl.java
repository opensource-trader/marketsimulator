package org.opensourcetrader.fixsimulator.exchange.impl;

import org.opensourcetrader.fixsimulator.exchange.Exchange;
import org.opensourcetrader.fixsimulator.exchange.OrderBook;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class ExchangeImpl implements Exchange {

    Map<String, OrderBook> instrumentToOrderbook = new HashMap<>();

    @Override
    public synchronized OrderBook getOrderBook(String instrument) {
        return instrumentToOrderbook.computeIfAbsent(instrument, OrderBookImpl::new);
    }
}
