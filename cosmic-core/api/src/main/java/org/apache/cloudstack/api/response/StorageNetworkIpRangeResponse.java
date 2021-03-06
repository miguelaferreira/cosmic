package org.apache.cloudstack.api.response;

import com.cloud.dc.StorageNetworkIpRange;
import com.cloud.serializer.Param;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

import com.google.gson.annotations.SerializedName;

@EntityReference(value = StorageNetworkIpRange.class)
public class StorageNetworkIpRangeResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "the uuid of storage network IP range.")
    private String uuid;

    @SerializedName(ApiConstants.VLAN)
    @Param(description = "the ID or VID of the VLAN.")
    private Integer vlan;

    @SerializedName(ApiConstants.POD_ID)
    @Param(description = "the Pod uuid for the storage network IP range")
    private String podUuid;

    @SerializedName(ApiConstants.START_IP)
    @Param(description = "the start ip of the storage network IP range")
    private String startIp;

    @SerializedName(ApiConstants.END_IP)
    @Param(description = "the end ip of the storage network IP range")
    private String endIp;

    @SerializedName(ApiConstants.GATEWAY)
    @Param(description = "the gateway of the storage network IP range")
    private String gateway;

    @SerializedName(ApiConstants.NETWORK_ID)
    @Param(description = "the network uuid of storage network IP range")
    private String networkUuid;

    @SerializedName(ApiConstants.ZONE_ID)
    @Param(description = "the Zone uuid of the storage network IP range")
    private String zoneUuid;

    @SerializedName(ApiConstants.NETMASK)
    @Param(description = "the netmask of the storage network IP range")
    private String netmask;

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    public void setZoneUuid(final String zoneUuid) {
        this.zoneUuid = zoneUuid;
    }

    public void setVlan(final Integer vlan) {
        this.vlan = vlan;
    }

    public void setPodUuid(final String podUuid) {
        this.podUuid = podUuid;
    }

    public void setStartIp(final String startIp) {
        this.startIp = startIp;
    }

    public void setEndIp(final String endIp) {
        this.endIp = endIp;
    }

    public void setNetworkUuid(final String networkUuid) {
        this.networkUuid = networkUuid;
    }

    public void setNetmask(final String netmask) {
        this.netmask = netmask;
    }

    public void setGateway(final String gateway) {
        this.gateway = gateway;
    }
}
