package org.apache.cloudstack.api.response;

import com.cloud.network.vpc.StaticRoute;
import com.cloud.serializer.Param;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

import java.util.List;

import com.google.gson.annotations.SerializedName;

@EntityReference(value = StaticRoute.class)
public class StaticRouteResponse extends BaseResponse implements ControlledEntityResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "the ID of static route")
    private String id;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "the state of the static route")
    private String state;

    @SerializedName(ApiConstants.VPC_ID)
    @Param(description = "VPC the static route belongs to")
    private String vpcId;

    @SerializedName(ApiConstants.NEXT_HOP)
    @Param(description = "Gateway ip address the CIDR is routed to")
    private String gwIpAddress;

    @SerializedName(ApiConstants.CIDR)
    @Param(description = "The CIDR to route")
    private String cidr;

    @SerializedName(ApiConstants.ACCOUNT)
    @Param(description = "the account associated with the static route")
    private String accountName;

    @SerializedName(ApiConstants.PROJECT_ID)
    @Param(description = "the project id of the static route")
    private String projectId;

    @SerializedName(ApiConstants.PROJECT)
    @Param(description = "the project name of the static route")
    private String projectName;

    @SerializedName(ApiConstants.DOMAIN_ID)
    @Param(description = "the ID of the domain associated with the static route")
    private String domainId;

    @SerializedName(ApiConstants.DOMAIN)
    @Param(description = "the domain associated with the static route")
    private String domainName;

    @SerializedName(ApiConstants.TAGS)
    @Param(description = "the list of resource tags associated with static route", responseObject = ResourceTagResponse.class)
    private List<ResourceTagResponse> tags;

    @Override
    public String getObjectId() {
        return this.id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public void setState(final String state) {
        this.state = state;
    }

    public void setVpcId(final String vpcId) {
        this.vpcId = vpcId;
    }

    public void setGwIpAddress(final String gwIpAddress) {
        this.gwIpAddress = gwIpAddress;
    }

    public void setCidr(final String cidr) {
        this.cidr = cidr;
    }

    @Override
    public void setAccountName(final String accountName) {
        this.accountName = accountName;
    }

    @Override
    public void setProjectId(final String projectId) {
        this.projectId = projectId;
    }

    @Override
    public void setProjectName(final String projectName) {
        this.projectName = projectName;
    }

    @Override
    public void setDomainId(final String domainId) {
        this.domainId = domainId;
    }

    @Override
    public void setDomainName(final String domainName) {
        this.domainName = domainName;
    }

    public void setTags(final List<ResourceTagResponse> tags) {
        this.tags = tags;
    }
}
