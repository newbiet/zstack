package org.zstack.header.network.service;

import org.zstack.header.message.APIReply;

import java.util.List;
import java.util.Map;

/**
 *@apiResult
 * api reply for message :ref:`APIGetNetworkServiceTypesMsg`
 *
 *@category network service
 *
 *@since 0.1.0
 *
 *@example
 * {
"org.zstack.header.network.service.APIGetNetworkServiceTypesReply": {
"serviceAndProviderTypes": {
"SecurityGroup": [
"38bf6be45b33401eb20ca273e72b981f"
],
"DHCP": [
"66f4ce5d10a949e386f0c6da6d76052f"
],
"SNAT": [
"66f4ce5d10a949e386f0c6da6d76052f"
],
"DNS": [
"66f4ce5d10a949e386f0c6da6d76052f"
],
"PortForwarding": [
"66f4ce5d10a949e386f0c6da6d76052f"
]
},
"success": true
}
}
 */

public class APIGetNetworkServiceTypesReply extends APIReply {
    /**
     * @desc a map where key is network service type and value is a list of network service provider uuid which provide
     * that type of network service
     */
    private Map<String, List<String>> serviceAndProviderTypes;

    public Map<String, List<String>> getServiceAndProviderTypes() {
        return serviceAndProviderTypes;
    }

    public void setServiceAndProviderTypes(Map<String, List<String>> serviceAndProviderTypes) {
        this.serviceAndProviderTypes = serviceAndProviderTypes;
    }
}
