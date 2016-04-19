package org.zstack.network.securitygroup;

import org.zstack.header.message.APIEvent;
/**
 *@apiResult
 *
 * api event for :ref:`APIAddVmNicToSecurityGroupMsg`
 *
 *@category security group
 *
 *@since 0.1.0
 *
 *@example
 * {
"org.zstack.network.securitygroup.APIAddVmNicToSecurityGroupEvent": {
"success": true
}
}
 */
public class APIAddVmNicToSecurityGroupEvent extends APIEvent {
    public APIAddVmNicToSecurityGroupEvent(String apiId) {
        super(apiId);
    }
    
    public APIAddVmNicToSecurityGroupEvent() {
    }
}
