package com.cloud.storage.upload;

import com.cloud.storage.Storage.ImageFormat;
import com.cloud.storage.Upload.Mode;
import com.cloud.storage.Upload.Status;
import com.cloud.storage.Upload.Type;
import com.cloud.storage.UploadVO;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.VolumeVO;
import com.cloud.utils.component.Manager;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.framework.jobs.AsyncJobManager;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreVO;

/**
 * Monitor upload progress of all entities.
 */
public interface UploadMonitor extends Manager {

    public void cancelAllUploads(Long templateId);

    public Long extractTemplate(VMTemplateVO template, String url, TemplateDataStoreVO tmpltStoreRef, Long dataCenterId, long eventId, long asyncJobId,
                                AsyncJobManager asyncMgr);

    boolean isTypeUploadInProgress(Long typeId, Type type);

    void handleUploadSync(long sserverId);

    UploadVO createNewUploadEntry(Long hostId, Long typeId, Status uploadState, Type type, String errorString, Mode extractMode);

    void extractVolume(UploadVO uploadVolumeObj, DataStore secStore, VolumeVO volume, String url, Long dataCenterId, String installPath, long eventId, long asyncJobId,
                       AsyncJobManager asyncMgr);

    UploadVO createEntityDownloadURL(VMTemplateVO template, TemplateDataStoreVO vmTemplateStore, Long dataCenterId, long eventId);

    void createVolumeDownloadURL(Long entityId, String path, Type type, Long dataCenterId, Long uploadId, ImageFormat format);
}
