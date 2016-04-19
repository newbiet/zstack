package org.zstack.header.image;

import org.zstack.header.message.APIEvent;

public class APICreateRootVolumeTemplateFromRootVolumeEvent extends APIEvent {
    private ImageInventory inventory;

    public APICreateRootVolumeTemplateFromRootVolumeEvent(String apiId) {
        super(apiId);
    }
    
    public APICreateRootVolumeTemplateFromRootVolumeEvent() {
        super(null);
    }
    
    public ImageInventory getInventory() {
        return inventory;
    }

    public void setInventory(ImageInventory inventory) {
        this.inventory = inventory;
    }
}
