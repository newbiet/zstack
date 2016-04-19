package org.zstack.header.image;

import org.zstack.header.message.APIEvent;

/**
 * Created by frank on 11/15/2015.
 */
public class APIRecoverImageEvent extends APIEvent {
    private ImageInventory inventory;

    public APIRecoverImageEvent() {
    }

    public APIRecoverImageEvent(String apiId) {
        super(apiId);
    }

    public ImageInventory getInventory() {
        return inventory;
    }

    public void setInventory(ImageInventory inventory) {
        this.inventory = inventory;
    }
}
