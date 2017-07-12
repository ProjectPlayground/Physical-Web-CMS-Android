package com.physical_web.cms.physicalwebcms.beacons;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public interface BeaconDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertBeacons(Beacon... beacons);

    @Update
    void updateBeacons(Beacon... beacons);

    @Query("select * from com.physical_web.cms.physicalwebcms.beacons")
    List<Beacon> getAllBeacons();

    @Query("select * from com.physical_web.cms.physicalwebcms.beacons where id = :id")
    Beacon getBeaconById(long id);

    @Delete
    void deleteBeacons(Beacon... beacons);
}