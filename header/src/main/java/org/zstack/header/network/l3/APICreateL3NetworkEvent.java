package org.zstack.header.network.l3;

import org.zstack.header.message.APIEvent;
/**
 *@apiResult
 *
 * api event for :ref:`APICreateL3NetworkMsg`
 *
 *@category l3Network
 *
 *@since 0.1.0
 *
 *@example
 * {
"org.zstack.header.network.l3.APICreateL3NetworkEvent": {
"inventory": {
"uuid": "4ea8c07361314a058a261a9daae1a414",
"name": "PublicNetwork",
"description": "Test",
"type": "L3BasicNetwork",
"trafficType": "NotSpecified",
"zoneUuid": "48c5febd96024e33809cc98035d79277",
"l2NetworkUuid": "a766f7dec6e5477f9842289950b51e63",
"state": "Enabled",
"createDate": "May 3, 2014 9:19:08 PM",
"lastOpDate": "May 3, 2014 9:19:08 PM",
"dns": [],
"ipRanges": [],
"networkServices": []
},
"success": true
}
}
 */
public class APICreateL3NetworkEvent extends APIEvent {
    /**
     * @desc see :ref:`L3NetworkInventory`
     */
    private L3NetworkInventory inventory;
    
    public APICreateL3NetworkEvent() {
        super(null);
    }
    
    public APICreateL3NetworkEvent(String apiId) {
        super(apiId);
    }

    public L3NetworkInventory getInventory() {
        return inventory;
    }

    public void setInventory(L3NetworkInventory inventory) {
        this.inventory = inventory;
    }
}
