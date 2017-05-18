package org.georchestra.geonetwork;

import static org.fao.geonet.repository.specification.MetadataSpecs.isHarvested;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.fao.geonet.domain.Metadata;
import org.fao.geonet.kernel.DataManager;
import org.fao.geonet.repository.MetadataRepository;
import org.fao.geonet.repository.MetadataRepositoryCustom;
import org.fao.geonet.utils.Log;
import org.jdom.Document;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

public class SchematronJob extends QuartzJobBean {

    private String modulename = this.getClass().getName();
    private AtomicBoolean started = new AtomicBoolean(false);
    
    @Autowired
    private ConfigurableApplicationContext applicationContext;
    @Autowired
    private DataManager _dataManager;

    private String defaultLanguage = "eng";
    
    @Override
    protected void executeInternal(final JobExecutionContext jobExecContext)
            throws JobExecutionException {
    	if (started.get() == true) {
    		Log.info(modulename,
    				"Job already running, skipping execution"
    				);
    		return;
    	}
        try {
        	started.set(true);
        	if ((applicationContext == null) || (_dataManager == null)) {
        		Log.error(modulename, "applicationContext or _dataManager is null, skipping execution");
        	}
        	MetadataRepositoryCustom mdrepocustom = applicationContext.getBean(MetadataRepositoryCustom.class);
        	MetadataRepository mdrepo = applicationContext.getBean(MetadataRepository.class);
        	List<Integer> mdToValidate = mdrepocustom.findAllIdsBy(isHarvested(false));
        	for (Integer mdId : mdToValidate) {
        		Metadata record = mdrepo.findOne(mdId);
        		try {
					_dataManager.doValidate(record.getDataInfo().getSchemaId(),
							mdId.toString(),
					        new Document(record.getXmlData(false)), defaultLanguage);
				} catch (Exception e) {
	        		Log.error(modulename, "Error validating metadata id " + record.getUuid());
				}
        	}
        } finally {
        	Log.info(modulename, "Finished validation job");
        	started.set(false);
        }
    }
}