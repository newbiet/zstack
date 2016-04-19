package org.zstack.header.identity;

import org.zstack.header.message.APIEvent;

public class APICreateUserEvent extends APIEvent {
    private UserInventory inventory;
    
    public APICreateUserEvent(String apiId) {
        super(apiId);
    }
    
    public APICreateUserEvent() {
        super(null);
    }

    public UserInventory getInventory() {
        return inventory;
    }

    public void setInventory(UserInventory inventory) {
        this.inventory = inventory;
    }
}
