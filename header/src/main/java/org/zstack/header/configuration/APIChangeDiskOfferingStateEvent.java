package org.zstack.header.configuration;

import org.zstack.header.message.APIEvent;

/**
 * Created with IntelliJ IDEA.
 * User: frank
 * Time: 2:37 PM
 * To change this template use File | Settings | File Templates.
 */
public class APIChangeDiskOfferingStateEvent extends APIEvent {
    private DiskOfferingInventory inventory;

    public APIChangeDiskOfferingStateEvent() {
        super(null);
    }

    public APIChangeDiskOfferingStateEvent(String apiId) {
        super(apiId);
    }

    public DiskOfferingInventory getInventory() {
        return inventory;
    }

    public void setInventory(DiskOfferingInventory inventory) {
        this.inventory = inventory;
    }
}
