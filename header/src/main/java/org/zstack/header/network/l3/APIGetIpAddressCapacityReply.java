package org.zstack.header.network.l3;

import org.zstack.header.message.APIReply;

/**
 */
public class APIGetIpAddressCapacityReply extends APIReply {
    private long totalCapacity;
    private long availableCapacity;

    public long getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(long totalCapacity) {
        this.totalCapacity = totalCapacity;
    }

    public long getAvailableCapacity() {
        return availableCapacity;
    }

    public void setAvailableCapacity(long availableCapacity) {
        this.availableCapacity = availableCapacity;
    }
}
