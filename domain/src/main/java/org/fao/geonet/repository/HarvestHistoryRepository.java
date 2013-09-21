package org.fao.geonet.repository;

import org.fao.geonet.domain.HarvestHistory;
import org.fao.geonet.domain.MetadataCategory;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Data Access object for accessing {@link MetadataCategory} entities.
 *
 * @author Jesse
 */
public interface HarvestHistoryRepository extends GeonetRepository<HarvestHistory, Integer>, JpaSpecificationExecutor<HarvestHistory> {
    /**
     * Find all the HarvestHistory objects of the given type.
     *
     * @param harvesterType the harvester type
     */
    @Nonnull
    List<HarvestHistory> findAllByHarvesterType(@Nonnull String harvesterType);

    /**
     * Look up a harvester by its uuid.
     *
     * @param uuid the uuid of the harvester
     */
    @Nonnull
    List<HarvestHistory> findAllByHarvesterUuid(@Nonnull String uuid);

    /**
     * Set the deleted flag to true in all history entities for the given uuid.
     *
     * @param harvesterUuid the harvester uuid.
     */
    @Modifying(clearAutomatically=true)
    @Query("UPDATE HarvestHistory SET deleted='y' WHERE harvesterUuid=:uuid")
    void markAllAsDeleted(@Param("uuid") @Nonnull String harvesterUuid);
}