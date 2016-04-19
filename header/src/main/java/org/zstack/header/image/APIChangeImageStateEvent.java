package org.zstack.header.image;

import org.zstack.header.message.APIEvent;

/**
 * Created with IntelliJ IDEA.
 * User: frank
 * Time: 5:32 PM
 * To change this template use File | Settings | File Templates.
 */
public class APIChangeImageStateEvent extends APIEvent {
    private ImageInventory inventory;

    public  APIChangeImageStateEvent(String apiId) {
        super(apiId);
    }

    public  APIChangeImageStateEvent() {
        super(null);
    }

    public ImageInventory getInventory() {
        return inventory;
    }

    public void setInventory(ImageInventory inventory) {
        this.inventory = inventory;
    }
}
