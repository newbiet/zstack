package org.zstack.header.vm;

import org.zstack.header.message.APIEvent;

/**
 * Created by frank on 2/26/2016.
 */
public class APISetVmStaticIpEvent extends APIEvent {
    public APISetVmStaticIpEvent() {
    }

    public APISetVmStaticIpEvent(String apiId) {
        super(apiId);
    }
}
