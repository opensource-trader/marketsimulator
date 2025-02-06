package org.opensourcetrader.marketsimulator.exchange.impl;

import lombok.Getter;
import org.opensourcetrader.marketsimulator.exchange.MDEntry;
import org.opensourcetrader.marketsimulator.exchange.MdEntryType;
import org.opensourcetrader.marketsimulator.exchange.MdUpdateActionType;

import java.math.BigDecimal;

@Getter
public class MDEntryImpl implements MDEntry {
    private final String instrument;
    private final MdUpdateActionType mdUpdateActionType;
    private final String id;
    private final BigDecimal price;
    private final double quantity;
    private final String clOrderId;
    private final MdEntryType mdEntryType;

    public MDEntryImpl(MdUpdateActionType mdUpdateActionType, String id, BigDecimal price,
                       double quantity, String instrument, MdEntryType mdEntryType,
                       String clOrderId){
        this.mdUpdateActionType = mdUpdateActionType;
        this.id = id;
        this.price = price;
        this.quantity = quantity;
        this.instrument = instrument;
        this.mdEntryType = mdEntryType;
        this.clOrderId = clOrderId;


    }

    @Override
    public MdUpdateActionType getMdUpdateAction() {
        return mdUpdateActionType;
    }
}
