package com.cloud.template;

import com.cloud.dc.DataCenterVO;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.StorageUnavailableException;
import com.cloud.storage.StoragePool;
import com.cloud.storage.VMTemplateHostVO;
import com.cloud.storage.VMTemplateStoragePoolVO;
import com.cloud.storage.VMTemplateVO;
import com.cloud.utils.Pair;
import com.cloud.vm.VirtualMachineProfile;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.TemplateInfo;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;

import java.util.List;

/**
 * TemplateManager manages the templates stored on secondary storage. It is responsible for creating private/public templates.
 */
public interface TemplateManager {
    static final String AllowPublicUserTemplatesCK = "allow.public.user.templates";
    static final ConfigKey<Boolean> AllowPublicUserTemplates = new ConfigKey<>("Advanced", Boolean.class, AllowPublicUserTemplatesCK, "true",
            "If false, users will not be able to create public templates.", true, ConfigKey.Scope.Account);
    public static final String MESSAGE_REGISTER_PUBLIC_TEMPLATE_EVENT = "Message.RegisterPublicTemplate.Event";
    public static final String MESSAGE_RESET_TEMPLATE_PERMISSION_EVENT = "Message.ResetTemplatePermission.Event";

    /**
     * Prepares a template for vm creation for a certain storage pool.
     *
     * @param template template to prepare
     * @param pool     pool to make sure the template is ready in.
     * @return VMTemplateStoragePoolVO if preparation is complete; null if not.
     */
    VMTemplateStoragePoolVO prepareTemplateForCreate(VMTemplateVO template, StoragePool pool);

    boolean resetTemplateDownloadStateOnPool(long templateStoragePoolRefId);

    /**
     * Copies a template from its current secondary storage server to the secondary storage server in the specified zone.
     *
     * @param template
     * @param srcSecStore
     * @param destZone
     * @return true if success
     * @throws StorageUnavailableException
     * @throws ResourceAllocationException
     */
    boolean copy(long userId, VMTemplateVO template, DataStore srcSecStore, DataCenterVO dstZone) throws StorageUnavailableException, ResourceAllocationException;

    /**
     * Deletes a template from secondary storage servers
     *
     * @param userId
     * @param templateId
     * @param zoneId     - optional. If specified, will only delete the template from the specified zone's secondary storage server.
     * @return true if success
     */
    boolean delete(long userId, long templateId, Long zoneId);

    /**
     * Lists templates in the specified storage pool that are not being used by any VM.
     *
     * @param pool
     * @return list of VMTemplateStoragePoolVO
     */
    List<VMTemplateStoragePoolVO> getUnusedTemplatesInPool(StoragePoolVO pool);

    /**
     * Deletes a template in the specified storage pool.
     *
     * @param templatePoolVO
     */
    void evictTemplateFromStoragePool(VMTemplateStoragePoolVO templatePoolVO);

    boolean templateIsDeleteable(VMTemplateHostVO templateHostRef);

    boolean templateIsDeleteable(long templateId);

    Pair<String, String> getAbsoluteIsoPath(long templateId, long dataCenterId);

    String getSecondaryStorageURL(long zoneId);

    DataStore getImageStore(long zoneId, long tmpltId);

    DataStore getImageStore(long tmpltId);

    Long getTemplateSize(long templateId, long zoneId);

    DataStore getImageStore(String storeUuid, Long zoneId);

    String getChecksum(DataStore store, String templatePath);

    List<DataStore> getImageStoreByTemplate(long templateId, Long zoneId);

    TemplateInfo prepareIso(long isoId, long dcId);

    /**
     * Adds ISO definition to given vm profile
     *
     * @param VirtualMachineProfile
     */
    void prepareIsoForVmProfile(VirtualMachineProfile profile);
}
