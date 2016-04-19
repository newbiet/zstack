package org.zstack.header.network.l2;

import org.zstack.header.message.APIEvent;
/**
 *@apiResult
 * api event for message :ref:`APIDetachL2NetworkFromClusterMsg`
 *
 *@category l2network
 *
 *@since 0.1.0
 *
 *@example
 * {
"org.zstack.header.network.l2.APIDetachL2NetworkFromClusterEvent": {
"inventory": {
"uuid": "409bbe05c1714d0a88ed9f4fff5bfe7e",
"name": "TestL2Network",
"description": "Test",
"zoneUuid": "b48555d7cf064dcc9267411c8e275a4b",
"physicalInterface": "eth0",
"type": "L2NoVlanNetwork",
"createDate": "May 4, 2014 12:08:12 AM",
"lastOpDate": "May 4, 2014 12:08:12 AM",
"attachedClusterUuids": []
},
"success": true
}
}
 */
public class APIDetachL2NetworkFromClusterEvent extends APIEvent {
    /**
     * @desc see :ref:`L2NetworkInventory`
     */
    private L2NetworkInventory inventory;

    public L2NetworkInventory getInventory() {
        return inventory;
    }

    public void setInventory(L2NetworkInventory inventory) {
        this.inventory = inventory;
    }

    public APIDetachL2NetworkFromClusterEvent(String apiId) {
        super(apiId);
    }
    
    public APIDetachL2NetworkFromClusterEvent() {
        super(null);
    }

}
