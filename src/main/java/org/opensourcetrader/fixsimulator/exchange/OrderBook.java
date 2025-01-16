package org.opensourcetrader.fixsimulator.exchange;

import java.math.BigDecimal;

public interface OrderBook {
    void addTradeListenerIfNotRegistered(TradeListener listener);

    void addMdEntryListener(MdEntryListener listener);

    void removeMdEntryListener(MdEntryListener listener);

    String addOrder(Side side, int qty, BigDecimal price, String clOrderId);

    OrderState deleteOrder(String orderId) throws OrderDeletionException;

    Order[] getBuyOrders();

    Order[] getSellOrders();

    Trade getLastTrade();

    double getTotalTradedVolume();

    OrderState modifyOrder(String orderId, int newQty, BigDecimal newPrice ) throws OrderModificationException;

    String getInstrument();
}
