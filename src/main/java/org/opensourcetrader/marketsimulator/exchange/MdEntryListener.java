package org.opensourcetrader.marketsimulator.exchange;

import java.util.List;

public interface MdEntryListener {

    void onMdEntries(List<MDEntry> mdEntries);

}
