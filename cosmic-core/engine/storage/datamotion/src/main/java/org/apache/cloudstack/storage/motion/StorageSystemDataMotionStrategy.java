package org.apache.cloudstack.storage.motion;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.to.DiskTO;
import com.cloud.agent.api.to.VirtualMachineTO;
import com.cloud.configuration.Config;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.org.Cluster;
import com.cloud.org.Grouping.AllocationState;
import com.cloud.resource.ResourceState;
import com.cloud.server.ManagementService;
import com.cloud.storage.DataStoreRole;
import com.cloud.storage.DiskOfferingVO;
import com.cloud.storage.SnapshotVO;
import com.cloud.storage.Storage.ImageFormat;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.DiskOfferingDao;
import com.cloud.storage.dao.SnapshotDao;
import com.cloud.storage.dao.SnapshotDetailsDao;
import com.cloud.storage.dao.SnapshotDetailsVO;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VirtualMachineManager;
import org.apache.cloudstack.engine.subsystem.api.storage.ChapInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.CopyCommandResult;
import org.apache.cloudstack.engine.subsystem.api.storage.DataMotionStrategy;
import org.apache.cloudstack.engine.subsystem.api.storage.DataObject;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreCapabilities;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine.Event;
import org.apache.cloudstack.engine.subsystem.api.storage.SnapshotInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.StrategyPriority;
import org.apache.cloudstack.engine.subsystem.api.storage.TemplateInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeDataFactory;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeService;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeService.VolumeApiResult;
import org.apache.cloudstack.framework.async.AsyncCallFuture;
import org.apache.cloudstack.framework.async.AsyncCompletionCallback;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.storage.command.CopyCmdAnswer;
import org.apache.cloudstack.storage.command.CopyCommand;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StorageSystemDataMotionStrategy implements DataMotionStrategy {
    private static final Logger s_logger = LoggerFactory.getLogger(StorageSystemDataMotionStrategy.class);

    @Inject
    private AgentManager _agentMgr;
    @Inject
    private ConfigurationDao _configDao;
    @Inject
    private DiskOfferingDao _diskOfferingDao;
    @Inject
    private HostDao _hostDao;
    @Inject
    private ManagementService _mgr;
    @Inject
    private PrimaryDataStoreDao _storagePoolDao;
    @Inject
    private SnapshotDao _snapshotDao;
    @Inject
    private SnapshotDetailsDao _snapshotDetailsDao;
    @Inject
    private VolumeDao _volumeDao;
    @Inject
    private VolumeDataFactory _volumeDataFactory;
    @Inject
    private VolumeService _volumeService;

    @Override
    public StrategyPriority canHandle(final DataObject srcData, final DataObject destData) {
        if (srcData instanceof SnapshotInfo) {
            if (canHandle(srcData.getDataStore()) || canHandle(destData.getDataStore())) {
                return StrategyPriority.HIGHEST;
            }
        }

        return StrategyPriority.CANT_HANDLE;
    }

    private boolean canHandle(final DataStore dataStore) {
        if (dataStore.getRole() == DataStoreRole.Primary) {
            final Map<String, String> mapCapabilities = dataStore.getDriver().getCapabilities();

            if (mapCapabilities != null) {
                final String value = mapCapabilities.get(DataStoreCapabilities.STORAGE_SYSTEM_SNAPSHOT.toString());
                final Boolean supportsStorageSystemSnapshots = new Boolean(value);

                if (supportsStorageSystemSnapshots) {
                    s_logger.info("Using 'StorageSystemDataMotionStrategy'");

                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public StrategyPriority canHandle(final Map<VolumeInfo, DataStore> volumeMap, final Host srcHost, final Host destHost) {
        return StrategyPriority.CANT_HANDLE;
    }

    @Override
    public Void copyAsync(final DataObject srcData, final DataObject destData, final Host destHost, final AsyncCompletionCallback<CopyCommandResult> callback) {
        if (srcData instanceof SnapshotInfo) {
            final SnapshotInfo snapshotInfo = (SnapshotInfo) srcData;

            validate(snapshotInfo);

            final boolean canHandleSrc = canHandle(srcData.getDataStore());

            if (canHandleSrc && destData instanceof TemplateInfo &&
                    (destData.getDataStore().getRole() == DataStoreRole.Image || destData.getDataStore().getRole() == DataStoreRole.ImageCache)) {
                return handleCreateTemplateFromSnapshot(snapshotInfo, (TemplateInfo) destData, callback);
            }

            if (destData instanceof VolumeInfo) {
                final VolumeInfo volumeInfo = (VolumeInfo) destData;

                final boolean canHandleDest = canHandle(destData.getDataStore());

                if (canHandleSrc && canHandleDest) {
                    return handleCreateVolumeFromSnapshotBothOnStorageSystem(snapshotInfo, volumeInfo, callback);
                }

                if (canHandleSrc) {
                    throw new UnsupportedOperationException("This operation is not supported (DataStoreCapabilities.STORAGE_SYSTEM_SNAPSHOT " +
                            "not supported by destination storage plug-in).");
                }

                if (canHandleDest) {
                    throw new UnsupportedOperationException("This operation is not supported (DataStoreCapabilities.STORAGE_SYSTEM_SNAPSHOT " +
                            "not supported by source storage plug-in).");
                }
            }
        }

        throw new UnsupportedOperationException("This operation is not supported.");
    }

    @Override
    public Void copyAsync(final DataObject srcData, final DataObject destData, final AsyncCompletionCallback<CopyCommandResult> callback) {
        return copyAsync(srcData, destData, null, callback);
    }

    @Override
    public Void copyAsync(final Map<VolumeInfo, DataStore> volumeMap, final VirtualMachineTO vmTo, final Host srcHost, final Host destHost, final
    AsyncCompletionCallback<CopyCommandResult> callback) {
        final CopyCommandResult result = new CopyCommandResult(null, null);

        result.setResult("Unsupported operation requested for copying data.");

        callback.complete(result);

        return null;
    }

    private void validate(final SnapshotInfo snapshotInfo) {
        final long volumeId = snapshotInfo.getVolumeId();

        final VolumeVO volumeVO = _volumeDao.findByIdIncludingRemoved(volumeId);

        if (volumeVO.getFormat() != ImageFormat.VHD) {
            throw new CloudRuntimeException("Only the " + ImageFormat.VHD.toString() + " image type is currently supported.");
        }
    }

    private Void handleCreateTemplateFromSnapshot(final SnapshotInfo snapshotInfo, final TemplateInfo templateInfo, final AsyncCompletionCallback<CopyCommandResult> callback) {
        try {
            snapshotInfo.processEvent(Event.CopyingRequested);
        } catch (final Exception ex) {
            throw new CloudRuntimeException("This snapshot is not currently in a state where it can be used to create a template.");
        }

        final HostVO hostVO = getHost(snapshotInfo.getDataStore().getId());
        final DataStore srcDataStore = snapshotInfo.getDataStore();

        final String value = _configDao.getValue(Config.PrimaryStorageDownloadWait.toString());
        final int primaryStorageDownloadWait = NumbersUtil.parseInt(value, Integer.parseInt(Config.PrimaryStorageDownloadWait.getDefaultValue()));
        final CopyCommand copyCommand = new CopyCommand(snapshotInfo.getTO(), templateInfo.getTO(), primaryStorageDownloadWait, VirtualMachineManager.ExecuteInSequence.value());

        String errMsg = null;

        CopyCmdAnswer copyCmdAnswer = null;

        try {
            _volumeService.grantAccess(snapshotInfo, hostVO, srcDataStore);

            final Map<String, String> srcDetails = getSnapshotDetails(_storagePoolDao.findById(srcDataStore.getId()), snapshotInfo);

            copyCommand.setOptions(srcDetails);

            copyCmdAnswer = (CopyCmdAnswer) _agentMgr.send(hostVO.getId(), copyCommand);
        } catch (final Exception ex) {
            throw new CloudRuntimeException(ex.getMessage());
        } finally {
            try {
                _volumeService.revokeAccess(snapshotInfo, hostVO, srcDataStore);
            } catch (final Exception ex) {
                s_logger.debug(ex.getMessage(), ex);
            }

            if (copyCmdAnswer == null || !copyCmdAnswer.getResult()) {
                if (copyCmdAnswer != null && copyCmdAnswer.getDetails() != null && !copyCmdAnswer.getDetails().isEmpty()) {
                    errMsg = copyCmdAnswer.getDetails();
                } else {
                    errMsg = "Unable to perform host-side operation";
                }
            }

            try {
                if (errMsg == null) {
                    snapshotInfo.processEvent(Event.OperationSuccessed);
                } else {
                    snapshotInfo.processEvent(Event.OperationFailed);
                }
            } catch (final Exception ex) {
                s_logger.debug(ex.getMessage(), ex);
            }
        }

        final CopyCommandResult result = new CopyCommandResult(null, copyCmdAnswer);

        result.setResult(errMsg);

        callback.complete(result);

        return null;
    }

    private Void handleCreateVolumeFromSnapshotBothOnStorageSystem(final SnapshotInfo snapshotInfo, VolumeInfo volumeInfo, final AsyncCompletionCallback<CopyCommandResult>
            callback) {
        try {
            // at this point, the snapshotInfo and volumeInfo should have the same disk offering ID (so either one should be OK to get a DiskOfferingVO instance)
            final DiskOfferingVO diskOffering = _diskOfferingDao.findByIdIncludingRemoved(volumeInfo.getDiskOfferingId());
            final SnapshotVO snapshot = _snapshotDao.findById(snapshotInfo.getId());

            // update the volume's hv_ss_reserve (hypervisor snapshot reserve) from a disk offering (used for managed storage)
            _volumeService.updateHypervisorSnapshotReserveForVolume(diskOffering, volumeInfo.getId(), snapshot.getHypervisorType());

            final AsyncCallFuture<VolumeApiResult> future = _volumeService.createVolumeAsync(volumeInfo, volumeInfo.getDataStore());

            final VolumeApiResult result = future.get();

            if (result.isFailed()) {
                s_logger.debug("Failed to create a volume: " + result.getResult());

                throw new CloudRuntimeException(result.getResult());
            }
        } catch (final Exception ex) {
            throw new CloudRuntimeException(ex.getMessage());
        }

        volumeInfo = _volumeDataFactory.getVolume(volumeInfo.getId(), volumeInfo.getDataStore());

        volumeInfo.processEvent(Event.MigrationRequested);

        volumeInfo = _volumeDataFactory.getVolume(volumeInfo.getId(), volumeInfo.getDataStore());

        final HostVO hostVO = getHost(snapshotInfo.getDataStore().getId());

        final String value = _configDao.getValue(Config.PrimaryStorageDownloadWait.toString());
        final int primaryStorageDownloadWait = NumbersUtil.parseInt(value, Integer.parseInt(Config.PrimaryStorageDownloadWait.getDefaultValue()));
        final CopyCommand copyCommand = new CopyCommand(snapshotInfo.getTO(), volumeInfo.getTO(), primaryStorageDownloadWait, VirtualMachineManager.ExecuteInSequence.value());

        CopyCmdAnswer copyCmdAnswer = null;

        try {
            _volumeService.grantAccess(snapshotInfo, hostVO, snapshotInfo.getDataStore());
            _volumeService.grantAccess(volumeInfo, hostVO, volumeInfo.getDataStore());

            final Map<String, String> srcDetails = getSnapshotDetails(_storagePoolDao.findById(snapshotInfo.getDataStore().getId()), snapshotInfo);

            copyCommand.setOptions(srcDetails);

            final Map<String, String> destDetails = getVolumeDetails(volumeInfo);

            copyCommand.setOptions2(destDetails);

            copyCmdAnswer = (CopyCmdAnswer) _agentMgr.send(hostVO.getId(), copyCommand);
        } catch (final Exception ex) {
            throw new CloudRuntimeException(ex.getMessage());
        } finally {
            try {
                _volumeService.revokeAccess(snapshotInfo, hostVO, snapshotInfo.getDataStore());
            } catch (final Exception ex) {
                s_logger.debug(ex.getMessage(), ex);
            }

            try {
                _volumeService.revokeAccess(volumeInfo, hostVO, volumeInfo.getDataStore());
            } catch (final Exception ex) {
                s_logger.debug(ex.getMessage(), ex);
            }
        }

        String errMsg = null;

        if (copyCmdAnswer == null || !copyCmdAnswer.getResult()) {
            if (copyCmdAnswer != null && copyCmdAnswer.getDetails() != null && !copyCmdAnswer.getDetails().isEmpty()) {
                errMsg = copyCmdAnswer.getDetails();
            } else {
                errMsg = "Unable to perform host-side operation";
            }
        }

        final CopyCommandResult result = new CopyCommandResult(null, copyCmdAnswer);

        result.setResult(errMsg);

        callback.complete(result);

        return null;
    }

    private Map<String, String> getSnapshotDetails(final StoragePoolVO storagePoolVO, final SnapshotInfo snapshotInfo) {
        final Map<String, String> details = new HashMap<>();

        details.put(DiskTO.STORAGE_HOST, storagePoolVO.getHostAddress());
        details.put(DiskTO.STORAGE_PORT, String.valueOf(storagePoolVO.getPort()));

        final long snapshotId = snapshotInfo.getId();

        details.put(DiskTO.IQN, getProperty(snapshotId, DiskTO.IQN));

        details.put(DiskTO.CHAP_INITIATOR_USERNAME, getProperty(snapshotId, DiskTO.CHAP_INITIATOR_USERNAME));
        details.put(DiskTO.CHAP_INITIATOR_SECRET, getProperty(snapshotId, DiskTO.CHAP_INITIATOR_SECRET));
        details.put(DiskTO.CHAP_TARGET_USERNAME, getProperty(snapshotId, DiskTO.CHAP_TARGET_USERNAME));
        details.put(DiskTO.CHAP_TARGET_SECRET, getProperty(snapshotId, DiskTO.CHAP_TARGET_SECRET));

        return details;
    }

    private String getProperty(final long snapshotId, final String property) {
        final SnapshotDetailsVO snapshotDetails = _snapshotDetailsDao.findDetail(snapshotId, property);

        if (snapshotDetails != null) {
            return snapshotDetails.getValue();
        }

        return null;
    }

    private Map<String, String> getVolumeDetails(final VolumeInfo volumeInfo) {
        final Map<String, String> sourceDetails = new HashMap<>();

        final VolumeVO volumeVO = _volumeDao.findById(volumeInfo.getId());

        final long storagePoolId = volumeVO.getPoolId();
        final StoragePoolVO storagePoolVO = _storagePoolDao.findById(storagePoolId);

        sourceDetails.put(DiskTO.STORAGE_HOST, storagePoolVO.getHostAddress());
        sourceDetails.put(DiskTO.STORAGE_PORT, String.valueOf(storagePoolVO.getPort()));
        sourceDetails.put(DiskTO.IQN, volumeVO.get_iScsiName());

        final ChapInfo chapInfo = _volumeService.getChapInfo(volumeInfo, volumeInfo.getDataStore());

        if (chapInfo != null) {
            sourceDetails.put(DiskTO.CHAP_INITIATOR_USERNAME, chapInfo.getInitiatorUsername());
            sourceDetails.put(DiskTO.CHAP_INITIATOR_SECRET, chapInfo.getInitiatorSecret());
            sourceDetails.put(DiskTO.CHAP_TARGET_USERNAME, chapInfo.getTargetUsername());
            sourceDetails.put(DiskTO.CHAP_TARGET_SECRET, chapInfo.getTargetSecret());
        }

        return sourceDetails;
    }

    public HostVO getHost(final long dataStoreId) {
        final StoragePoolVO storagePoolVO = _storagePoolDao.findById(dataStoreId);

        final List<? extends Cluster> clusters = _mgr.searchForClusters(storagePoolVO.getDataCenterId(), new Long(0), Long.MAX_VALUE, HypervisorType.XenServer.toString());

        if (clusters == null) {
            throw new CloudRuntimeException("Unable to locate an applicable cluster");
        }

        for (final Cluster cluster : clusters) {
            if (cluster.getAllocationState() == AllocationState.Enabled) {
                final List<HostVO> hosts = _hostDao.findByClusterId(cluster.getId());

                if (hosts != null) {
                    for (final HostVO host : hosts) {
                        if (host.getResourceState() == ResourceState.Enabled) {
                            return host;
                        }
                    }
                }
            }
        }

        throw new CloudRuntimeException("Unable to locate an applicable cluster");
    }
}
