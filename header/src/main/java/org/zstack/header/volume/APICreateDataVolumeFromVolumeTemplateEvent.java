package org.zstack.header.volume;

import org.zstack.header.message.APIEvent;

/**
 */
public class APICreateDataVolumeFromVolumeTemplateEvent extends APIEvent {
    private VolumeInventory inventory;

    public APICreateDataVolumeFromVolumeTemplateEvent() {
    }

    public APICreateDataVolumeFromVolumeTemplateEvent(String apiId) {
        super(apiId);
    }

    public VolumeInventory getInventory() {
        return inventory;
    }

    public void setInventory(VolumeInventory inventory) {
        this.inventory = inventory;
    }
}
