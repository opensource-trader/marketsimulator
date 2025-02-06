package org.opensourcetrader.marketsimulator.marketdataserver;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.fixprotocol.components.Common;
import org.fixprotocol.components.MarketData;
import org.opensourcetrader.marketsimulator.exchange.Exchange;
import org.opensourcetrader.marketsimulator.exchange.OrderBook;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class Connection {
    Map<String, MarketDataSubscription> subscriptions = new HashMap<>();

    Exchange exchange;
    StreamObserver<MarketData.MarketDataIncrementalRefresh> refreshObserver;

    Connection(Exchange exchange, StreamObserver<MarketData.MarketDataIncrementalRefresh> responseObserver, String subscriberId) {
        log.info("creating Connection for subscriber: {}", subscriberId);
        this.exchange = exchange;
        this.refreshObserver = responseObserver;
    }


    void close() {
        subscriptions.values().forEach(MarketDataSubscription::close);
    }

    void subscribe(MarketData.MarketDataRequest msg) {
        log.info("Received subscription request {}", msg);

        for (Common.InstrmtMDReqGrp mdReqGrp: msg.getInstrmtMdReqGrpList()) {
            String symbol = mdReqGrp.getInstrument().getSymbol();
            log.info("Received subcription request for symbol: {}", symbol);

            if(subscriptions.containsKey(symbol)) {
                var subscription = subscriptions.remove(symbol);
                subscription.close();
                return;
            }

            OrderBook book = exchange.getOrderBook(symbol);
            subscriptions.put(symbol, new MarketDataSubscription(this, book, msg.getMdReqId()));
        }
    }

    public void send(MarketData.MarketDataIncrementalRefresh refresh) {
        try {
            refreshObserver.onNext(refresh);
        } catch (Throwable t) {
            log.error("Failed to refresh the market data, closing connection",t);
            this.close();
            refreshObserver.onError(t);
        }
    }


}
