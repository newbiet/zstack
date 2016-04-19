package org.zstack.core.config;

import org.zstack.header.message.APIEvent;

public class APIUpdateGlobalConfigEvent extends APIEvent {
	private GlobalConfigInventory inventory;
	
	public APIUpdateGlobalConfigEvent(String apiId) {
	    super(apiId);
    }
	public APIUpdateGlobalConfigEvent() {
		super(null);
	}
	public GlobalConfigInventory getInventory() {
    	return inventory;
    }
	public void setInventory(GlobalConfigInventory inventory) {
    	this.inventory = inventory;
    }
}
