package org.opensourcetrader.fixsimulator.exchange.impl;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.opensourcetrader.fixsimulator.exchange.*;

import java.math.BigDecimal;
import java.util.*;


@Slf4j
public class OrderBookImpl implements OrderBook {
    @Getter
    private String instrument;

    List<LimitOrderImpl> buyOrders = new ArrayList<>();
    List<LimitOrderImpl> sellOrders = new ArrayList<>();

    List<TradeListener> tradeListeners = new ArrayList<>();
    List<MdEntryListener> mdEntryListeners = new ArrayList<>();

    @Getter
    Trade lastTrade;
    @Getter
    double totalTradedVolume;

    OrderBookImpl(String instrument){
        this.instrument = instrument;
    }

    @Override
    public synchronized void addTradeListenerIfNotRegistered(TradeListener listener) {
       if (!tradeListeners.contains(listener)) {
           tradeListeners.add(listener);
       }

    }

    @Override
    public synchronized void addMdEntryListener(MdEntryListener listener) {
        mdEntryListeners.add(listener);
    }

    @Override
    public void removeMdEntryListener(MdEntryListener listener) {
        mdEntryListeners.remove(listener);
    }

    @Override
    public synchronized Order[] getBuyOrders() {
        return buyOrders.toArray(new Order[]{});
    }

    @Override
    public synchronized Order[] getSellOrders() {
        return sellOrders.toArray(new Order[]{});
    }

    @Override
    public OrderState modifyOrder(String orderId, int newQty, BigDecimal newPrice) throws OrderModificationException {
        LimitOrderImpl originalOrder = getOrder(orderId);

        if (newPrice ==null){
            newPrice = originalOrder.getPrice();
        }

        if (originalOrder.getQuantity() > newQty) {
            double qtyReduction = originalOrder.getQuantity() - newQty;
            if (qtyReduction >= originalOrder.getRemainingQty()) {
                try {
                    deleteOrder(orderId);
                } catch (OrderDeletionException e) {
                    //TODO should raise orderModification Exception
                    throw  new RuntimeException("Unexpected faliure to delete order during order modification,e");
                }
                return new OrderDeleteImpl(originalOrder.getOrderId(), 0, newQty);
            }
        }

        if (originalOrder.getPrice().compareTo(newPrice) == 0) {
            double qntChange = newQty - originalOrder.getQuantity();
            originalOrder.setQuantity(originalOrder.getQuantity() + qntChange);
            originalOrder.setRemainingQty(originalOrder.getRemainingQty() + qntChange);

            sendMdEntry(originalOrder, MdUpdateActionType.MODIFY);
        } else {
            try {
                deleteOrder(orderId);
                addOrderWithId(originalOrder.getSide(), newQty, newPrice, originalOrder.getClOrdId(),
                        originalOrder.getOrderId(), true);
            } catch (OrderDeletionException e) {
                //TODO should raise orderModification Exception
                throw  new RuntimeException("Unexpected faliue to delete order during order modification",e);
            }
        }

        return new OrderDeleteImpl(originalOrder.getClOrdId(),originalOrder.getRemainingQty(), originalOrder.getQuantity());
    }

    private LimitOrderImpl getOrder(String orderId) throws OrderModificationException{
        Optional<LimitOrderImpl> optFoundOrder = buyOrders.stream()
                .filter(o -> o.getOrderId().equals(orderId)).findFirst();

        if (optFoundOrder.isEmpty()) {
            optFoundOrder = sellOrders.stream()
                    .filter(o -> o.getOrderId().equals(orderId)).findFirst();
        }

        if (optFoundOrder.isEmpty()) {
            throw new OrderModificationException(String.format("Order modification failed, as no order exists with id: %s", orderId));
        }

        return optFoundOrder.get();
    }

    @Override
    public OrderState deleteOrder(String orderId) throws OrderDeletionException {
        Order deletedOrder = deleteAndReturnOrder(orderId);

        if (deletedOrder != null){
            sendMdEntry(deletedOrder, MdUpdateActionType.REMOVE);
        } else {
            throw new OrderDeletionException(String.format("Order deletion failed, as no order exists with id: %s", orderId));
        }
        return new OrderDeleteImpl(deletedOrder.getOrderId(),
                deletedOrder.getRemainingQty(), deletedOrder.getQuantity());
    }

    private void sendMdEntry(Order order, MdUpdateActionType mdUpdateActionType){
        MDEntryImpl entry = createMdEntry(order, mdUpdateActionType);
        List<MDEntry> entries = new ArrayList<>();
        entries.add(entry);
        dispatchMdEntries(entries);
    }


    Order deleteAndReturnOrder(String orderId) {
        Order removeorder = removeFromOrders(buyOrders, orderId);
        if (removeorder == null) {
            removeorder = removeFromOrders(sellOrders,orderId);
        }

        return removeorder;
    }

    Order removeFromOrders(List<LimitOrderImpl> orders, String orderId) {
        Optional<LimitOrderImpl> optFoundorder = orders.stream()
                .filter(o -> o.getOrderId().equals(orderId))
                .findFirst();

        if (optFoundorder.isEmpty()) {
            return null;
        }

        Order order = optFoundorder.get();
        orders.remove(order);

        return order;
    }

    @Override
    public synchronized String addOrder(Side side, int qty, BigDecimal price, String clOrderId) {
        log.debug("Add order   symbol:{} qty:{} price:{} side:{} clOrderId:{}",
                this.instrument, qty, price,side,clOrderId);

        String orderId = UUID.randomUUID().toString();

        return addOrderWithId(side,qty, price,clOrderId, orderId, false).getOrderId();
    }

    private Order addOrderWithId(Side side, int qty, BigDecimal price, String clorderId,
                                 String orderId, boolean fromModification) {
        LimitOrderImpl newOrder = new LimitOrderImpl(qty,price,clorderId,side, orderId);

        checkForCrosses(newOrder , fromModification);

        if (newOrder.getRemainingQty() > 0){
            addOrderToBook(newOrder);
        }

        return newOrder;
    }

    private void checkForCrosses(LimitOrderImpl newOrder, boolean fromModifyOperation) {
        List<MDEntry> mdEntries = new ArrayList<>();
        List<Trade> trades = new ArrayList<>();

        List<LimitOrderImpl> oppSideOrders = newOrder.getSide() == Side.BUY? sellOrders: buyOrders;
        List<Integer> oppSideIndexOfOrdersToRemove = new ArrayList<>();

        for (int i=0;i<oppSideOrders.size();i++) {
            LimitOrderImpl oppSideOrder = oppSideOrders.get(i);

            if (newOrder.getRemainingQty() > 0 &&
                    ((newOrder.getSide() == Side.BUY && newOrder.getPrice().compareTo(oppSideOrder.getPrice()) != -1) ||
                    (newOrder.getSide() == Side.SELL && newOrder.getPrice().compareTo(oppSideOrder.getPrice()) != 1))) {
                double quantity = newOrder.getRemainingQty() > oppSideOrder.getRemainingQty() ?
                        oppSideOrder.getRemainingQty(): newOrder.getRemainingQty();
                BigDecimal price = oppSideOrder.getPrice();

                newOrder.setRemainingQty(newOrder.getRemainingQty() - quantity);

                if (oppSideOrder.getRemainingQty() - quantity == 0){
                    oppSideIndexOfOrdersToRemove.add(i);
                    mdEntries.add(createMdEntry(oppSideOrder, MdUpdateActionType.REMOVE));
                    // The remove entry requires the entries quantity, hence the adjustment to the remaining qty
                    // is done after the creation of mdEntry
                    oppSideOrder.setRemainingQty(oppSideOrder.getRemainingQty() - quantity);
                } else {
                    oppSideOrder.setRemainingQty(oppSideOrder.getRemainingQty() -quantity);
                    mdEntries.add(createMdEntry(oppSideOrder, MdUpdateActionType.MODIFY));
                }

                String tradeId = UUID.randomUUID().toString();

                trades.add(new TradeImpl(tradeId, oppSideOrder.getClOrdId(), price, quantity, instrument,
                        oppSideOrder.getSide(), oppSideOrder.getOrderId(), oppSideOrder.getRemainingQty(),
                        oppSideOrder.getQuantity() - oppSideOrder.getRemainingQty()));
                trades.add(new TradeImpl(tradeId, newOrder.getClOrdId(), price, quantity, instrument,
                        newOrder.getSide(), newOrder.getOrderId(), newOrder.getRemainingQty(),
                        newOrder.getQuantity()- newOrder.getRemainingQty()));
            } else {
                break;
            }
        }

        if (!oppSideIndexOfOrdersToRemove.isEmpty()){
            List<LimitOrderImpl> updatedOrders = new ArrayList<>();
            for (int i=0;i<oppSideOrders.size();i++){
                if (!oppSideIndexOfOrdersToRemove.contains(i)){
                    updatedOrders.add(oppSideOrders.get(i));
                }
            }

            if (newOrder.getSide() == Side.BUY) {
                sellOrders = updatedOrders;
            } else {
                buyOrders = updatedOrders;
            }
        }

        if (newOrder.getRemainingQty() >0){
            mdEntries.add(createMdEntry(newOrder,
                    fromModifyOperation? MdUpdateActionType.MODIFY:
                    MdUpdateActionType.ADD));
        }

        List<Trade> uniqueTades = new ArrayList<>();
        Set<String> encountered = new HashSet<>();

        for (var trade: trades ) {
            if (!encountered.contains(trade.getTradeId())){
                encountered.add(trade.getTradeId());
                uniqueTades.add(trade);
            }
        }

        for (var trade: uniqueTades){
            lastTrade = trade;
            totalTradedVolume += trade.getQuantity();

            var mdTradeEntry = new MDEntryImpl(MdUpdateActionType.MODIFY,
                    trade.getTradeId(), trade.getPrice(),
                    trade.getQuantity(), instrument, MdEntryType.TRADE, "");
            mdEntries.add(mdTradeEntry);
        }


        if (uniqueTades.size() >0){
            var mdTotalTradeEntry = new MDEntryImpl(MdUpdateActionType.MODIFY, UUID.randomUUID().toString(),
                    lastTrade.getPrice(), totalTradedVolume, instrument, MdEntryType.TRADE_VOLUME, "");
            mdEntries.add(mdTotalTradeEntry);
        }

        dispatchTrades(trades);
        dispatchMdEntries(mdEntries);
    }

    private MDEntryImpl createMdEntry(Order order, MdUpdateActionType mdUpdateActionType) {
        return new MDEntryImpl(mdUpdateActionType, order.getOrderId(),
                order.getPrice(),order.getRemainingQty(), instrument,
                getMdEntryTypeFromSide(order.getSide()), order.getClOrdId());
    }

    public static MdEntryType getMdEntryTypeFromSide(Side side){
        return side == Side.BUY ? MdEntryType.BID: MdEntryType.OFFER;
    }

    private void addOrderToBook(LimitOrderImpl newOrder){
        if (newOrder.getSide() == Side.BUY) {
            int insertionindex = 0;
            for (;insertionindex<buyOrders.size();insertionindex++){
                if (buyOrders.get(insertionindex).getPrice().compareTo(newOrder.getPrice())== -1){
                    break;
                }
            }

            buyOrders.add(insertionindex,newOrder);
        } else {
            int insertionindex =0;
            for (; insertionindex<sellOrders.size();insertionindex++){
                if (sellOrders.get(insertionindex).getPrice().compareTo(newOrder.getPrice())==1){
                    break;
                }
            }
            sellOrders.add(insertionindex, newOrder);
        }
    }

    void dispatchMdEntries(List<MDEntry> mdEntries){
        mdEntryListeners.forEach(l->l.onMdEntries(mdEntries));
    }

    void dispatchTrades(List<Trade> trades ){
        tradeListeners.forEach(t -> t.onTrades(trades));
    }





}
