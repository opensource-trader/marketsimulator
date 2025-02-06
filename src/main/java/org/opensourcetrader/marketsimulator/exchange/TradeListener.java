package org.opensourcetrader.marketsimulator.exchange;

import java.util.List;

public interface TradeListener {
    void onTrades(List<Trade> trades);
}
