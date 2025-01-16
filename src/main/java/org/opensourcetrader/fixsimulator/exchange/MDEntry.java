package org.opensourcetrader.fixsimulator.exchange;

import java.math.BigDecimal;

public interface MDEntry {
    String getInstrument();

    MdUpdateActionType getMdUpdateAction();

    MdEntryType getMdEntryType();

    String getId();

    String getClOrderId();

    BigDecimal getPrice();

    double getQuantity();
}
