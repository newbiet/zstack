package org.zstack.header.host;

import org.zstack.header.message.APIEvent;

/**
 *@apiResult
 *
 * api event for '*AddHost*' message, depending on hypervisor the messages vary
 *
 * for example(:ref:`APIAddKVMHostMsg`)
 *
 *@since 0.1.0
 *
 *@example
 *

{
"org.zstack.header.host.APIAddHostEvent": {
"inventory": {
"zoneUuid": "2893ce85c43d4a3a8d78f414da39966e",
"name": "host1-192.168.0.203",
"uuid": "43673938584447b2a29ab3d53f9d88d3",
"clusterUuid": "8524072a4274403892bcc5b1972c2576",
"description": "Test",
"managementIp": "192.168.0.203",
"hypervisorType": "KVM",
"state": "Enabled",
"status": "Connected",
"createDate": "Feb 28, 2014 6:49:24 PM",
"lastOpDate": "Feb 28, 2014 6:49:24 PM"
},
"success": true
}
}
 */
public class APIAddHostEvent extends APIEvent {
    /**
     * @desc see :ref:`HostInventory`
     */
	private HostInventory inventory;
	
	public APIAddHostEvent() {
		super(null);
	}
	
	public APIAddHostEvent(String apiId) {
	    super(apiId);
    }

	public HostInventory getInventory() {
    	return inventory;
    }

	public void setInventory(HostInventory inventory) {
    	this.inventory = inventory;
    }
}
