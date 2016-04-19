package org.zstack.header.vm;

import org.zstack.header.message.APIEvent;

/**
 * Created by frank on 10/17/2015.
 */
public class APIAttachIsoToVmInstanceEvent extends APIEvent {
    private VmInstanceInventory inventory;

    public APIAttachIsoToVmInstanceEvent() {
    }

    public APIAttachIsoToVmInstanceEvent(String apiId) {
        super(apiId);
    }

    public VmInstanceInventory getInventory() {
        return inventory;
    }

    public void setInventory(VmInstanceInventory inventory) {
        this.inventory = inventory;
    }
}
