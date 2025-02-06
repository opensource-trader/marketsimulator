package org.opensourcetrader.marketsimulator.fix;

import lombok.extern.slf4j.Slf4j;
import org.opensourcetrader.marketsimulator.exchange.*;
import org.springframework.beans.factory.annotation.Autowired;
import quickfix.*;
import quickfix.Message;
import quickfix.MessageCracker;
import quickfix.field.*;
import quickfix.field.Side;
import quickfix.fix50sp2.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Slf4j
public class ApplicationImpl extends MessageCracker implements Application, TradeListener {

    public static final Symbol SYMBOL = new Symbol();
    public static final Price PRICE = new Price();
    public static final Side SIDE = new Side();
    public static final ClOrdID CL_ORD_ID = new ClOrdID();
    public static final OrderQty ORDER_QTY = new OrderQty();
    public static final StringField PRICE_FIELD_AS_STRING = new StringField(44);


    Map<String, String> clOrderIdToOrderId = new HashMap<>();
    Map<String, String> clOrderIdToSymbol = new HashMap<>();
    Map<String, SessionID> clOrderIdToSession = new HashMap<>();


    Exchange exchange;

    ApplicationImpl(@Autowired Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public void onCreate(SessionID sessionID) {
        throw new UnsupportedOperationException("Method not implemented");
    }

    @Override
    public void onLogon(SessionID sessionID) {
        throw new UnsupportedOperationException("Method not implemented");
    }

    @Override
    public void onLogout(SessionID sessionID) {
        throw new UnsupportedOperationException("Method not implemented");
    }

    @Override
    public void toAdmin(Message message, SessionID sessionID) {
        throw new UnsupportedOperationException("Method not implemented");
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        throw new UnsupportedOperationException("Method not implemented");
    }

    @Override
    public void toApp(Message message, SessionID sessionID) throws DoNotSend {
        throw new UnsupportedOperationException("Method not implemented");
    }

    @Override
    public void fromApp(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        log.info("Received: {}", message);
        crack(message, sessionID);
    }


    @Override
    public void onTrades(List<Trade> trades) {
        for (var trade: trades){
            if (clOrderIdToSession.containsKey(trade.getClOrderId())) {
                ExecutionReport executionReport = new ExecutionReport(new OrderID(trade.getOrderId()),
                        new ExecID(trade.getTradeId()),
                        new ExecType(ExecType.TRADE),
                        new OrdStatus(trade.getLeavesQty() > 0 ? OrdStatus.PARTIALLY_FILLED:OrdStatus.FILLED),
                        new Side(trade.getOrderSide()== org.opensourcetrader.marketsimulator.exchange.Side.BUY? Side.BUY:Side.SELL),
                        new LeavesQty(trade.getLeavesQty()),
                        new CumQty(trade.getCumQty()));

                executionReport.set(new ClOrdID(trade.getClOrderId()));
                executionReport.set(new LastQty(trade.getQuantity()));
                executionReport.set(new LastPx(trade.getPrice().doubleValue()));

                try {
                    Session.sendToTarget(executionReport, clOrderIdToSession.get(trade.getClOrderId()));
                } catch (SessionNotFound e){
                    log.error("Unable to send trade report for client order id {} as the session not found", trade.getClOrderId());
                }
            }
        }
    }


    public void onMessage(OrderCancelReplaceRequest replaceRequest, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        try {
            String clOrdId = replaceRequest.get(CL_ORD_ID).getValue();
            Side side = replaceRequest.get(SIDE);
            OrderQty qty = replaceRequest.get(ORDER_QTY);
            BigDecimal newPrice = null;

            if (replaceRequest.isSet(PRICE)) {
                var priceStr = replaceRequest.getField(PRICE_FIELD_AS_STRING);
                var priceString = priceStr.getValue();
                newPrice = new BigDecimal(priceString);
            }

            OrderState orderState;
            String orderId = clOrderIdToOrderId.get(clOrdId);
            var symbol = clOrderIdToSymbol.get(clOrdId);

            try {
                if (orderId == null) {
                    throw new OrderModificationException("No order found for the given client order id: " + clOrdId);
                }

                OrderBook orderBook = exchange.getOrderBook(symbol);
                orderState = orderBook.modifyOrder(orderId,  (int) qty.getValue(),newPrice);
            } catch (OrderModificationException e) {
                OrderCancelReject reject = new OrderCancelReject();
                reject.set(new OrderID("NONE"));
                reject.set(new ClOrdID(clOrdId));
                reject.set(new OrdStatus(OrdStatus.REJECTED));
                reject.set(new CxlRejResponseTo(CxlRejResponseTo.ORDER_CANCEL_REPLACE_REQUEST));
                Session.sendToTarget(reject, sessionID);
                return;
            }

            ExecutionReport executionReport = new ExecutionReport();
            executionReport.set(replaceRequest.get(CL_ORD_ID));
            executionReport.set(new OrderID(orderId));
            executionReport.set(new ExecID(UUID.randomUUID().toString()));
            executionReport.set(new ExecType(ExecType.REPLACED));
            executionReport.set(new OrdStatus(OrdStatus.REPLACED));
            executionReport.set(new Symbol(symbol));
            executionReport.set(side);

            executionReport.set(new LeavesQty(orderState.getLeavesQty()));
            executionReport.set(new CumQty(orderState.getQty() - orderState.getLeavesQty()));
            Session.sendToTarget(executionReport, sessionID);
        } catch (Exception e) {
            log.error("Failed to process order replace request:" + replaceRequest, e);
        }
    }


    public void onMessage(OrderCancelRequest cancelRequest, SessionID sessionID) {
        try {
            String clOdrId = cancelRequest.get(CL_ORD_ID).getValue();
            String orderId = clOrderIdToOrderId.get(clOdrId);

            if (orderId == null) {
                rejectCancelRequest(sessionID, clOdrId, "No order found for client order Id:" + clOdrId , "NONE");
                return;
            }

            var symbol = clOrderIdToSymbol.get(clOdrId);
            OrderBook orderBook = exchange.getOrderBook(symbol);

            OrderState orderState;

            try {
                orderState = orderBook.deleteOrder(orderId);
            } catch (OrderDeletionException e) {
                rejectCancelRequest(sessionID, clOdrId, e.getMessage(), orderId);
                return;
            }

            ExecutionReport executionReport = new ExecutionReport(new OrderID(orderState.getOrderId()),
                    new ExecID(UUID.randomUUID().toString()),
                    new ExecType(ExecType.CANCELED),
                    new OrdStatus(OrdStatus.CANCELED),
                    cancelRequest.getSide(),
                    new LeavesQty(orderState.getLeavesQty()),
                    new CumQty(orderState.getQty() - orderState.getLeavesQty()));

            executionReport.set(cancelRequest.get(CL_ORD_ID));
            executionReport.set(new Symbol(symbol));

            Session.sendToTarget(executionReport, sessionID);

        } catch (Exception e) {
            log.error("Failed to process order cancel request: "+ cancelRequest, e);
        }
    }


    private void rejectCancelRequest(SessionID sessionID, String clOrdId, String message,
                                     String rejectOrderId) throws SessionNotFound {
        OrderCancelReject reject = new OrderCancelReject();
        reject.set(new OrderID(rejectOrderId));
        reject.set(new ClOrdID(clOrdId));
        reject.set(new OrdStatus(OrdStatus.REJECTED));
        reject.set(new CxlRejResponseTo(CxlRejResponseTo.ORDER_CANCEL_REQUEST));
        reject.set(new Text(message));
        Session.sendToTarget(reject,sessionID);
    }


    public  void onMessage(NewOrderSingle order, SessionID sessionID) {
        try {
            handleNewOrderSingle(order, sessionID);
        } catch (RejectOrderException e) {
             rejectNewOrder(order, sessionID, e.getMessage());
        }
    }


    private void handleNewOrderSingle(NewOrderSingle order, SessionID sessionID) throws RejectOrderException{
        try {
            OrderQty orderQty = order.get(ORDER_QTY);

            if (orderQty.getValue() <= 0) {
                throw new RejectOrderException("Quantity must be greater than 0");
            }

            ClOrdID clOrdID = order.get(CL_ORD_ID);
            Symbol symbol = order.get(SYMBOL);

            clOrderIdToSession.put(clOrdID.getValue(), sessionID);

            if (!order.isSet(PRICE)) {
                throw new RejectOrderException("Price must be set");
            }

            Side side = order.get(SIDE);
            org.opensourcetrader.marketsimulator.exchange.Side exSide = null;

            if (side.getValue() == Side.BUY) {
                exSide = org.opensourcetrader.marketsimulator.exchange.Side.SELL;
            } else if (side.getValue() == Side.SELL) {
                exSide = org.opensourcetrader.marketsimulator.exchange.Side.BUY;
            } else {
                throw new RejectOrderException("Side not supported: " + side);
            }

            OrderBook orderBook = exchange.getOrderBook(symbol.getValue());
            orderBook.addTradeListenerIfNotRegistered(this);

            var priceStr = order.getField(PRICE_FIELD_AS_STRING);
            var priceString = priceStr.getValue();

            String orderId = orderBook.addOrder(exSide, (int) orderQty.getValue(), new BigDecimal(priceString), clOrdID.getValue());

            if (clOrderIdToOrderId.containsKey(clOrdID)) {
                throw new RejectOrderException("Already recieved clOrdId: " + clOrdID);
            }

            clOrderIdToOrderId.put(clOrdID.getValue(), orderId);
            clOrderIdToSymbol.put(clOrdID.getValue(), symbol.getValue());

            ExecutionReport executionReport = new ExecutionReport();
            executionReport.set(clOrdID);
            executionReport.set(new OrderID(orderId));
            executionReport.set(new ExecID(UUID.randomUUID().toString()));
            executionReport.set(new ExecType(ExecType.NEW));
            executionReport.set(new OrdStatus(OrdStatus.NEW));
            executionReport.set(symbol);
            executionReport.set(side);
            executionReport.set(new LeavesQty(orderQty.getValue()));
            executionReport.set(new CumQty(0));
            executionReport.set(new AvgPx(0));

            Session.sendToTarget(executionReport, sessionID);
        } catch (Exception e) {
            log.error("Failed to process new single order: " + order, e);
        }
    }

    public void rejectNewOrder(NewOrderSingle order, SessionID sessionID, String reason){
        try {
            ExecutionReport executionReport = new ExecutionReport();
            executionReport.set(order.get(CL_ORD_ID));
            executionReport.set(new OrderID(""));
            executionReport.set(new ExecID(UUID.randomUUID().toString()));
            executionReport.set(new ExecType(ExecType.REJECTED));
            executionReport.set(new OrdStatus(OrdStatus.REJECTED));
            executionReport.set(order.get(SYMBOL));
            executionReport.set(order.get(SIDE));
            executionReport.set(new LeavesQty(0));
            executionReport.set(new CumQty(0));
            executionReport.set(new AvgPx(0));
            executionReport.set(new OrdRejReason(OrdRejReason.OTHER));
            executionReport.set(new Text(reason));

            Session.sendToTarget(executionReport, sessionID);
        } catch (Exception e) {
            log.error("Failed to reject order new single order: "+ order, e);
        }
    }
}
