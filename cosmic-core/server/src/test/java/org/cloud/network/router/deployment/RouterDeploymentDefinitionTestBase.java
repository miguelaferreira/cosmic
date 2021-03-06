package org.cloud.network.router.deployment;

import static org.mockito.Mockito.when;

import com.cloud.dc.DataCenter;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.Pod;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.network.IpAddressManager;
import com.cloud.network.NetworkModel;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.PhysicalNetworkServiceProviderDao;
import com.cloud.network.dao.VirtualRouterProviderDao;
import com.cloud.network.router.NetworkHelper;
import com.cloud.network.router.VpcNetworkHelperImpl;
import com.cloud.offerings.NetworkOfferingVO;
import com.cloud.offerings.dao.NetworkOfferingDao;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.vm.VirtualMachineProfile.Param;
import com.cloud.vm.dao.DomainRouterDao;
import com.cloud.vm.dao.VMInstanceDao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RouterDeploymentDefinitionTestBase {

    protected static final String LOCK_NOT_CORRECTLY_GOT = "Lock not correctly got";
    protected static final String NUMBER_OF_ROUTERS_TO_DEPLOY_IS_NOT_THE_EXPECTED = "Number of routers to deploy is not the expected";
    protected static final String ONLY_THE_PROVIDED_AS_DEFAULT_DESTINATION_WAS_EXPECTED = "Only the provided as default destination was expected";

    protected static final long OFFERING_ID = 16L;
    protected static final long DEFAULT_OFFERING_ID = 17L;
    protected static final Long DATA_CENTER_ID = 100l;
    protected static final Long NW_ID_1 = 101l;
    protected static final Long NW_ID_2 = 102l;
    protected static final Long POD_ID1 = 111l;
    protected static final Long POD_ID2 = 112l;
    protected static final Long POD_ID3 = 113l;
    protected static final Long ROUTER1_ID = 121l;
    protected static final Long ROUTER2_ID = 122l;
    protected static final long PROVIDER_ID = 131L;
    protected static final long PHYSICAL_NW_ID = 141L;

    // General delegates (Daos, Mgrs...)
    @Mock
    protected NetworkDao mockNwDao;
    @Mock
    protected DomainRouterDao mockRouterDao;
    @Mock
    protected NetworkHelper mockNetworkHelper;
    @Mock
    protected VpcNetworkHelperImpl vpcNwHelper;
    @Mock
    protected VMInstanceDao mockVmDao;
    @Mock
    protected HostPodDao mockPodDao;
    @Mock
    protected VirtualRouterProviderDao mockVrProviderDao;
    @Mock
    protected PhysicalNetworkServiceProviderDao physicalProviderDao;
    @Mock
    protected NetworkModel mockNetworkModel;
    @Mock
    protected IpAddressManager mockIpAddrMgr;
    @Mock
    protected NetworkOfferingDao mockNetworkOfferingDao;
    @Mock
    protected ServiceOfferingDao mockServiceOfferingDao;
    @Mock
    protected AccountManager mockAccountMgr;

    // Instance specific parameters to use during build
    @Mock
    protected DeployDestination mockDestination;
    @Mock
    protected DataCenter mockDataCenter;
    @Mock
    protected Pod mockPod;
    @Mock
    protected HostPodVO mockHostPodVO1;
    @Mock
    protected HostPodVO mockHostPodVO2;
    @Mock
    protected HostPodVO mockHostPodVO3;
    @Mock
    protected NetworkVO mockNw;
    @Mock
    protected Account mockOwner;
    protected List<HostPodVO> mockPods = new ArrayList<>();
    protected Map<Param, Object> params = new HashMap<>();
    @InjectMocks
    protected RouterDeploymentDefinitionBuilder builder = new RouterDeploymentDefinitionBuilder();
    @Mock
    NetworkOfferingVO mockNwOfferingVO;
    @Mock
    ServiceOfferingVO mockSvcOfferingVO;

    protected void initMocks() {
        when(mockDestination.getDataCenter()).thenReturn(mockDataCenter);
        when(mockDataCenter.getId()).thenReturn(DATA_CENTER_ID);
        when(mockPod.getId()).thenReturn(POD_ID1);
        when(mockHostPodVO1.getId()).thenReturn(POD_ID1);
        when(mockHostPodVO2.getId()).thenReturn(POD_ID2);
        when(mockHostPodVO3.getId()).thenReturn(POD_ID3);
        when(mockNw.getId()).thenReturn(NW_ID_1);
    }
}
