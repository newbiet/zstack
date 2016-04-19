package org.zstack.network.service.eip;

import org.zstack.header.message.APIEvent;

/**
 */
public class APIChangeEipStateEvent extends APIEvent {
    private EipInventory inventory;

    public APIChangeEipStateEvent() {
        super(null);
    }

    public APIChangeEipStateEvent(String apiId) {
        super(apiId);
    }

    public EipInventory getInventory() {
        return inventory;
    }

    public void setInventory(EipInventory inventory) {
        this.inventory = inventory;
    }
}
