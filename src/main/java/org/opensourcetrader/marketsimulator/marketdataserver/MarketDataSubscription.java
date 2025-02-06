package org.opensourcetrader.marketsimulator.marketdataserver;

import lombok.extern.slf4j.Slf4j;
import org.fixprotocol.components.Common;
import org.fixprotocol.components.Fix;
import org.fixprotocol.components.MarketData;
import org.opensourcetrader.marketsimulator.exchange.*;
import org.opensourcetrader.marketsimulator.exchange.impl.OrderBookImpl;

import java.io.Closeable;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;


@Slf4j
public class MarketDataSubscription implements Closeable, MdEntryListener {

    Connection connection;
    OrderBook book;
    String requestId;

    MarketDataSubscription(Connection connection, OrderBook book, String requestId){
        this.connection = connection;
        this.book = book;
        this.requestId = requestId;


        MarketData.MarketDataIncrementalRefresh.Builder incrementalRefreshBuilder = MarketData.MarketDataIncrementalRefresh.newBuilder();
        incrementalRefreshBuilder.setMdReqId(requestId);

        var updateType = MarketData.MDUpdateActionEnum.MD_UPDATE_ACTION_NEW;

        for (Order order: book.getBuyOrders()){
            incrementalRefreshBuilder.addMdIncGrp(getMdEntryFromOrder(book, order, updateType, Side.BUY));
        }

        for (Order order: book.getSellOrders()) {
            incrementalRefreshBuilder.addMdIncGrp(getMdEntryFromOrder(book, order, updateType, Side.SELL));
        }

        var lastTrade = book.getLastTrade();
        if (lastTrade != null) {
            MarketData.MDIncGrp.Builder mdEntryBuilder = MarketData.MDIncGrp.newBuilder();
            mdEntryBuilder.setMdUpdateAction(updateType);
            mdEntryBuilder.setMdEntryId(lastTrade.getTradeId());
            setPrice(mdEntryBuilder, lastTrade.getPrice());

            setQuantity(mdEntryBuilder, (long) lastTrade.getQuantity());

            mdEntryBuilder.setMdEntryType(getMdEntryType(MdEntryType.TRADE));

            var instrument = Common.Instrument.newBuilder()
                    .setSymbol(book.getInstrument());
            mdEntryBuilder.setInstrument(instrument);
            incrementalRefreshBuilder.addMdIncGrp(mdEntryBuilder.build());
        }

        MarketData.MDIncGrp.Builder mdTradeVol = MarketData.MDIncGrp.newBuilder();
        mdTradeVol.setMdUpdateAction(updateType);
        mdTradeVol.setMdEntryId(UUID.randomUUID().toString());
        setQuantity(mdTradeVol, (long) book.getTotalTradedVolume());
        mdTradeVol.setMdEntryType(getMdEntryType(MdEntryType.TRADE_VOLUME));
        var instrument = Common.Instrument.newBuilder().setSymbol(book.getInstrument());
        mdTradeVol.setInstrument(instrument.build());

        incrementalRefreshBuilder.addMdIncGrp(mdTradeVol.build());

        book.addMdEntryListener(this);

        var incRefresh = incrementalRefreshBuilder.build();
        connection.send(incRefresh);
    }

    private MarketData.MDIncGrp getMdEntryFromOrder(OrderBook book, Order order, MarketData.MDUpdateActionEnum updateTye, Side side) {
        MarketData.MDIncGrp.Builder mdEntrybuilder = MarketData.MDIncGrp.newBuilder();
        mdEntrybuilder.setMdUpdateAction(updateTye);
        mdEntrybuilder.setMdEntryId(order.getOrderId());
        BigDecimal price = order.getPrice();
        setPrice(mdEntrybuilder, price);
        setQuantity(mdEntrybuilder, (long) order.getRemainingQty());
        mdEntrybuilder.setMdEntryType(getMdEntryType(OrderBookImpl.getMdEntryTypeFromSide(side)));
        var instrument = Common.Instrument.newBuilder().setSymbol(book.getInstrument());
        mdEntrybuilder.setInstrument(instrument.build());
        return mdEntrybuilder.build();
    }


    private void setQuantity(MarketData.MDIncGrp.Builder builder, long qntAsDouble) {
        long quantity = qntAsDouble;
        var qntbuilder = Fix.Decimal64.newBuilder();
        qntbuilder.setMantissa(quantity);
        qntbuilder.setExponent(0);
        builder.setMdEntrySize(qntbuilder.build());
    }

    private void setPrice(MarketData.MDIncGrp.Builder builder, BigDecimal price) {
        Fix.Decimal64 fixPrice = getFixDecimal64(price);
        builder.setMdEntryPx(fixPrice);
    }

    static Fix.Decimal64 getFixDecimal64(BigDecimal price) {
        var str = price.toPlainString();
        var idx = str.indexOf('.');
        int exp = 0;
        if (idx > -1) {
            str = str.replace(".", "");
            exp = -(str.length() -idx);
        }

        long mantissa = Long.parseLong(str);
        var priceBuilder = Fix.Decimal64.newBuilder();
        priceBuilder.setExponent(exp);
        priceBuilder.setMantissa(mantissa);
        return  priceBuilder.build();
    }

    public void close(){
        book.removeMdEntryListener(this);
    }

    @Override
    public void onMdEntries(List<MDEntry> mdEntries) {
        MarketData.MarketDataIncrementalRefresh.Builder incRefreshBuilder = MarketData.MarketDataIncrementalRefresh.newBuilder();
        for (MDEntry entry: mdEntries){
            var mdEntrybuilder = MarketData.MDIncGrp.newBuilder();
            mdEntrybuilder.setMdUpdateAction(getMDUpdateActrionEnum(entry.getMdUpdateAction()));
            mdEntrybuilder.setInstrument(Common.Instrument.newBuilder().setSymbol(entry.getInstrument()).build());
            mdEntrybuilder.setMdEntryId(entry.getId());
            mdEntrybuilder.setMdEntryType(getMdEntryType(entry.getMdEntryType()));
            setPrice(mdEntrybuilder, entry.getPrice());
            setQuantity(mdEntrybuilder, (long) entry.getQuantity());
            incRefreshBuilder.addMdIncGrp(mdEntrybuilder.build());
        }

        var refresh = incRefreshBuilder.build();
        connection.send(refresh);
    }

    public static MarketData.MDEntryTypeEnum getMdEntryType(MdEntryType actionType) {
        switch (actionType) {
            case BID -> {
                return MarketData.MDEntryTypeEnum.MD_ENTRY_TYPE_BID;
            }
            case OFFER -> {
                return MarketData.MDEntryTypeEnum.MD_ENTRY_TYPE_OFFER;
            }
            case TRADE -> {
                return MarketData.MDEntryTypeEnum.MD_ENTRY_TYPE_TRADE;
            }
            case TRADE_VOLUME -> {
                return MarketData.MDEntryTypeEnum.MD_ENTRY_TYPE_TRADE_VOLUME;
            }
            default -> {
                throw new RuntimeException("MdEntry type is not supported: " + actionType);
            }
        }
    }

    public MarketData.MDUpdateActionEnum getMDUpdateActrionEnum(MdUpdateActionType entryType) {
        switch (entryType) {
            case ADD -> {
                return MarketData.MDUpdateActionEnum.MD_UPDATE_ACTION_NEW;
            }
            case MODIFY -> {
                return MarketData.MDUpdateActionEnum.MD_UPDATE_ACTION_CHANGE;
            }
            case REMOVE -> {
                return MarketData.MDUpdateActionEnum.MD_UPDATE_ACTION_DELETE;
            }
            default -> {
                throw new RuntimeException("Unexpected entry type: " + entryType);
            }
        }
    }


}
