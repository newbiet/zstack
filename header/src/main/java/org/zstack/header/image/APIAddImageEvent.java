package org.zstack.header.image;

import org.zstack.header.message.APIEvent;

public class APIAddImageEvent extends APIEvent {
	public APIAddImageEvent(String apiId) {
	    super(apiId);
    }
	
	public APIAddImageEvent() {
		super(null);
	}

	private ImageInventory inventory;

	public ImageInventory getInventory() {
    	return inventory;
    }

	public void setInventory(ImageInventory inventory) {
    	this.inventory = inventory;
    }
}
