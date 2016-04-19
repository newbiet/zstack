package org.zstack.network.service.portforwarding;

import org.zstack.header.message.APIEvent;
/**
 *@apiResult
 * api event for message :ref:`APIDeletePortForwardingRuleMsg`
 *
 *@category port forwarding
 *
 *@since 0.1.0
 *
 *@example
 * {
"org.zstack.network.service.portforwarding.APIDeletePortForwardingRuleEvent": {
"success": true
}
}
 */
public class APIDeletePortForwardingRuleEvent extends APIEvent {
    public APIDeletePortForwardingRuleEvent(String apiId) {
        super(apiId);
    }
    
    public APIDeletePortForwardingRuleEvent() {
        super(null);
    }
}
