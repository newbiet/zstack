package org.zstack.header.network.l3;

import org.zstack.header.message.APIEvent;
/**
 *@apiResult
 * api event for :ref:`APIRemoveDnsFromL3NetworkMsg`
 *
 *@category l3network
 *
 *@since 0.1.0
 *
 *@example
 * {
"org.zstack.header.network.l3.APIRemoveDnsFromL3NetworkEvent": {
"inventory": {
"uuid": "f14fd6ff593a41dd8c6caafc1f5448f9",
"name": "TestL3Network3",
"description": "Test",
"type": "L3BasicNetwork",
"zoneUuid": "6ce4eae2414245318292dc54ea78f8d2",
"l2NetworkUuid": "f540fb719683484483470153fc42788a",
"state": "Enabled",
"createDate": "May 4, 2014 4:28:41 PM",
"lastOpDate": "May 4, 2014 4:28:41 PM",
"dns": [],
"ipRanges": [
{
"uuid": "75f560ed8d11417ea278ffb35ddf7e23",
"l3NetworkUuid": "f14fd6ff593a41dd8c6caafc1f5448f9",
"name": "TestIpRange",
"description": "Test",
"startIp": "10.20.3.100",
"endIp": "10.30.3.200",
"netmask": "255.0.0.0",
"gateway": "10.20.3.1",
"createDate": "May 4, 2014 4:28:41 PM",
"lastOpDate": "May 4, 2014 4:28:41 PM"
}
],
"networkServices": []
},
"success": true
}
}
 */
public class APIRemoveDnsFromL3NetworkEvent extends APIEvent {
    /**
     * @desc
     * see :ref:`L3NetworkInventory`
     */
    private L3NetworkInventory inventory;
    
    public APIRemoveDnsFromL3NetworkEvent() {
        super(null);
    }
    
    public APIRemoveDnsFromL3NetworkEvent(String apiId) {
        super(apiId);
    }

    public L3NetworkInventory getInventory() {
        return inventory;
    }

    public void setInventory(L3NetworkInventory inventory) {
        this.inventory = inventory;
    }
}
