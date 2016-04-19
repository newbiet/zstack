package org.zstack.header.volume;

import org.zstack.header.message.APIEvent;

/**
 *@apiResult
 *
 * api event for message :ref:`APIChangeVolumeStateMsg`
 *
 *@since 0.1.0
 *
 *@example
 * {
"org.zstack.header.volume.APIChangeVolumeStateEvent": {
"inventory": {
"uuid": "f035366497994ef6bda20a45c4b3ee2e",
"name": "TestData",
"type": "Data",
"size": 10737418240,
"state": "Disabled",
"status": "NotInstantiated",
"createDate": "May 2, 2014 8:07:29 PM",
"lastOpDate": "May 2, 2014 8:07:29 PM",
"backupStorageRefs": []
},
"success": true
}
}
 */
public class APIChangeVolumeStateEvent extends APIEvent {
    /**
     * @desc
     * see :ref:`VolumeInventory`
     */
    private VolumeInventory inventory;

    public APIChangeVolumeStateEvent() {
        super(null);
    }

    public APIChangeVolumeStateEvent(String apiId) {
        super(apiId);
    }

    public VolumeInventory getInventory() {
        return inventory;
    }

    public void setInventory(VolumeInventory inventory) {
        this.inventory = inventory;
    }
}
