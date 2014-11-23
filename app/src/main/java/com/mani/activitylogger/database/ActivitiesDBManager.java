package com.mani.activitylogger.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.mani.activitylogger.app.ActivitiesLoggerApplication;
import com.mani.activitylogger.model.ActivityLocation;
import com.mani.activitylogger.model.DetectedActivity;
import com.mani.activitylogger.model.UserActivity;
import com.mani.activitylogger.util.DateTimeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by manikandan.selvaraju on 10/3/14.
 */
public class ActivitiesDBManager {

    // SQLite DB handle for trips.db
    private static SQLiteDatabase tripsDB;

    private static ActivitiesLoggerDatabase tripsDBCreator;

    // Singleton instance
    private static ActivitiesDBManager instance;

    // Dictionary of activity table ids and TripActivity
    private HashMap<Integer, DetectedActivity> activityIds = new HashMap<Integer, DetectedActivity>();

    public static ActivitiesDBManager getInstance() {
        // Double locking pattern in multi threading scenario
        if( instance == null ) {
            synchronized (ActivitiesDBManager.class) {
                if (instance == null) {
                    instance = new ActivitiesDBManager();
                }
            }
        }
        return instance;
    }

    public ActivitiesDBManager() {
        tripsDBCreator = new ActivitiesLoggerDatabase(ActivitiesLoggerApplication.getContext());
        open();
    }

    public void open() throws SQLException {
        tripsDB = tripsDBCreator.getWritableDatabase();
        fillActivityTable();
    }

    public static void release() {
        close();
    }

    public static void close() {
        if( tripsDB != null) {
            tripsDB.close();
            tripsDB = null;
        }
        if(tripsDBCreator != null) {
            tripsDBCreator.close();
            tripsDBCreator = null;
        }
    }

    private void fillActivityTable() {
        Cursor cursor = tripsDB.query(ActivitiesConstants.ACTIVITY_TABLE,
                null,null,null, null, null, null);
        if(cursor != null && cursor.moveToFirst() == false ) {
            ContentValues values = new ContentValues();
            for(DetectedActivity activity: DetectedActivity.values()) {
                values.put(ActivitiesConstants.ACTIVITY.ACTIVITY_NAME, activity.getName());
                long id = tripsDB.insert(ActivitiesConstants.ACTIVITY_TABLE, null, values);
                // Fill the dictionary
                activityIds.put((int) id, activity);
            }
        } else if(cursor.getCount() > 0) {
            //Fill the activityIds dictionary.
            activityIds.clear();
            do {
                String activity = cursor.getString(cursor.getColumnIndex(ActivitiesConstants.ACTIVITY.ACTIVITY_NAME));
                long id  = cursor.getLong(cursor.getColumnIndex(ActivitiesConstants.ACTIVITY.ACTIVITY_ID));
                activityIds.put((int) id, DetectedActivity.getActivity(activity));
            } while (cursor.moveToNext());
        }
    }

    private int getTripActivityId(DetectedActivity activity) {
        Set<Integer> keys = activityIds.keySet();
        int unknownId = 0;
        for(Integer key: keys) {
            DetectedActivity value = activityIds.get(key);
            if (activity == value) {
                return key.intValue();
            } else if ( activity == DetectedActivity.UNKNOWN) {
                //If there is not match set the unknown.
                unknownId = key.intValue();
            }
        }
        return unknownId;
    }

    public long addTrip(DetectedActivity activity) throws SQLException {
        long insertId = 0;
        ContentValues values = new ContentValues();
        values.put(ActivitiesConstants.TRIP.START_TIME, System.currentTimeMillis()/1000);
        values.put(ActivitiesConstants.TRIP.ACTIVITY_TYPE, getTripActivityId(activity));

        try {
            insertId = tripsDB.insertOrThrow(ActivitiesConstants.TRIP_TABLE, null,values);
        } catch (SQLException ex) {
            throw ex;
        }
        return insertId;
    }

    public long endTrip(long tripId, long endTime) throws SQLException {
        String WHERE = ActivitiesConstants.TRIP.TRIP_ID+"=?";
        String args[] = { Long.toString(tripId)};

        long insertId = 0;
        ContentValues values = new ContentValues();
        values.put(ActivitiesConstants.TRIP.END_TIME,endTime);

        try {
            insertId = tripsDB.update(ActivitiesConstants.TRIP_TABLE, values, WHERE, args);
        } catch (SQLException ex) {
            throw ex;
        }

        return insertId;
    }

    public long updateTripStartLocation(long tripId, long startLocationId) throws SQLException {
        String WHERE = ActivitiesConstants.TRIP.TRIP_ID+"=?";
        String args[] = { Long.toString(tripId)};

        long insertId = 0;
        ContentValues values = new ContentValues();
        values.put(ActivitiesConstants.TRIP.START_LOCATION, startLocationId);

        try {
            insertId = tripsDB.update(ActivitiesConstants.TRIP_TABLE, values, WHERE, args);
        } catch (SQLException ex) {
            throw ex;
        }

        return insertId;
    }

    public long updateTripEndLocation(long tripId, long endLocationId) throws SQLException {
        String WHERE = ActivitiesConstants.TRIP.TRIP_ID+"=?";
        String args[] = { Long.toString(tripId)};

        long insertId = 0;
        ContentValues values = new ContentValues();
        values.put(ActivitiesConstants.TRIP.END_LOCATION, endLocationId);

        try {
            insertId = tripsDB.update(ActivitiesConstants.TRIP_TABLE, values, WHERE, args);
        } catch (SQLException ex) {
            throw ex;
        }

        return insertId;
    }

    public boolean isTripStartLocationSet(long tripId) {
        String WHERE = ActivitiesConstants.TRIP.TRIP_ID + "=?";
        String args[] = {Long.toString(tripId)};

        Cursor cursor = tripsDB.query(ActivitiesConstants.TRIP_TABLE,
                null, WHERE, args, null, null, null);

        boolean startLocationSet = false;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                long tripStartLocationId = cursor.getLong(cursor.getColumnIndex(ActivitiesConstants.TRIP.START_LOCATION));
                startLocationSet = (tripStartLocationId <= 0) ? false: true;
            }
        }

        return startLocationSet;
    }

    // Deleting a trip also deletes on references table.
    public long deleteTrip(long tripId) throws SQLException {

        String WHERE = ActivitiesConstants.TRIP.TRIP_ID + "=?";
        String args[] = {Long.toString(tripId)};

        long insertId = 0;
        try{
            insertId = tripsDB.delete(ActivitiesConstants.TRIP_TABLE, WHERE, args);

        } catch (SQLException ex) {
            ex.printStackTrace();
            throw ex;
        }
        return insertId;
    }

    public DetectedActivity getTripActivity(long tripId) {
        String WHERE = ActivitiesConstants.TRIP.TRIP_ID + "=?";
        String args[] = { Long.toString(tripId)};

        Cursor cursor = tripsDB.query(ActivitiesConstants.TRIP_TABLE,
                null, WHERE, args, null, null, null);

        DetectedActivity activity = DetectedActivity.UNKNOWN;
        if (cursor != null ) {
            if (cursor.moveToFirst()) {
                activity = activityIds.get(cursor.getInt(
                        cursor.getColumnIndex(ActivitiesConstants.TRIP.ACTIVITY_TYPE)));
            }
        }

        if ( cursor != null) {
            cursor.close();
        }
        return activity;
    }

    /*
     * Add a new location with ( lat,long).
     */
    public long addLocation(double latitude, double longitude) throws SQLException {
        long insertId = 0;
        ContentValues values = new ContentValues();
        values.put(ActivitiesConstants.LOCATION.LATITUDE, latitude);
        values.put(ActivitiesConstants.LOCATION.LONGITUDE, longitude);

        try {
            insertId = tripsDB.insertOrThrow(ActivitiesConstants.LOCATION_TABLE, null,values);
        } catch (SQLException ex) {
            throw ex;
        }
        return insertId;
    }

    /**
     * Update the location address.
     * @param locationId
     * @param address
     * @return
     * @throws SQLException
     */
    public long updateLocationAddress(long locationId, String address) throws SQLException {
        long insertId = 0;

        String WHERE = ActivitiesConstants.LOCATION.LOCATION_ID+"=?";
        String args[] = { Long.toString(locationId)};

        ContentValues values = new ContentValues();
        values.put(ActivitiesConstants.LOCATION.ADDRESS, address);

        try {
            insertId = tripsDB.update(ActivitiesConstants.LOCATION_TABLE, values, WHERE, args);
        } catch (SQLException ex) {
            throw ex;
        }
        return insertId;
    }

    public List<UserActivity> getTrips() {
        return getTrips(0, System.currentTimeMillis()/1000);
    }

    /**
     * Get all trip between start time and end time.
     * Uses left join on location table to get start location ( lat,long, address) and
     * end location information.
     *
     * @param startTime
     * @param endTime
     * @return
     *
     * Actual query for reference:
     *
     * select t.trip_id, t.start_time, t.end_time, t.activity_type, l1.latitude as start_latitude,
     * l1.longitude as start_longitude, l1.address as start_address, l2.latitude as end_latitude,
     * l2.longitude as end_longitude, l2.address as end_address from trips as t
     * left join location as l1 on t.start_location = l1.location_id
     * left join location as l2 on t.end_location = l2.location_id
     * where t.start_time between 1412459824 and 0 and t.end_time > 0
     */
    public List<UserActivity> getTrips(long startTime, long endTime) {
        List<UserActivity> tripsList = new ArrayList<UserActivity>();
        Map<String,Integer> headerMap = new HashMap<String,Integer>();
        int headerId = 0;

        String rawQuery = "select t."+ ActivitiesConstants.TRIP.TRIP_ID+", "+
                "t."+ ActivitiesConstants.TRIP.START_TIME+", "+
                "t."+ ActivitiesConstants.TRIP.END_TIME+", "+
                "t."+ ActivitiesConstants.TRIP.ACTIVITY_TYPE+", "+

                "l1."+ ActivitiesConstants.LOCATION.LATITUDE+" as "+
                    ActivitiesConstants.TRIP.START_LATITUDE+", "+
                "l1."+ ActivitiesConstants.LOCATION.LONGITUDE+" as "+
                    ActivitiesConstants.TRIP.START_LONGITUDE+", "+
                "l1."+ ActivitiesConstants.LOCATION.ADDRESS+" as "+
                    ActivitiesConstants.TRIP.START_ADDRESS+", "+

                "l2."+ ActivitiesConstants.LOCATION.LATITUDE+" as "+
                    ActivitiesConstants.TRIP.END_LATITUDE+", "+
                "l2."+ ActivitiesConstants.LOCATION.LONGITUDE+" as "+
                    ActivitiesConstants.TRIP.END_LONGITUDE+", "+
                "l2."+ ActivitiesConstants.LOCATION.ADDRESS+" as "+
                    ActivitiesConstants.TRIP.END_ADDRESS+

                " from "+ ActivitiesConstants.TRIP_TABLE +" as t " +

                "left join "+ ActivitiesConstants.LOCATION_TABLE +" as l1 on t."+
                ActivitiesConstants.TRIP.START_LOCATION+"=l1."+ ActivitiesConstants.LOCATION.LOCATION_ID +

                " left join "+ ActivitiesConstants.LOCATION_TABLE +" as l2 on t."+
                ActivitiesConstants.TRIP.END_LOCATION+"=l2."+ ActivitiesConstants.LOCATION.LOCATION_ID +

                " where t."+ ActivitiesConstants.TRIP.START_TIME+ " > "+ startTime+" and "+
                "t."+ ActivitiesConstants.TRIP.START_TIME+" < "+ endTime +
                " and t."+ ActivitiesConstants.TRIP.END_TIME+" > 0";

        Cursor cursor = tripsDB.rawQuery(rawQuery, null);

        if (cursor != null ) {
            if  (cursor.moveToFirst()) {
                do {

                    UserActivity userActivity = new UserActivity();
                    long id = cursor.getLong(cursor.getColumnIndex(ActivitiesConstants.TRIP.TRIP_ID));
                    long tripStartTime = cursor.getLong(cursor.getColumnIndex(ActivitiesConstants.TRIP.START_TIME));
                    long tripEndTime = cursor.getLong(cursor.getColumnIndex(ActivitiesConstants.TRIP.END_TIME));
                    userActivity.setId(id);
                    userActivity.setStartTime(tripStartTime);
                    userActivity.setEndTime(tripEndTime);

                    ActivityLocation startLocation = new ActivityLocation();
                    String address = cursor.getString(cursor.getColumnIndex(ActivitiesConstants.TRIP.START_ADDRESS));
                    double latitude = cursor.getDouble(cursor.getColumnIndex(ActivitiesConstants.TRIP.START_LATITUDE));
                    double longitude = cursor.getDouble(cursor.getColumnIndex(ActivitiesConstants.TRIP.START_LONGITUDE));
                    startLocation.setAddress(address);
                    startLocation.setLatitude(latitude);
                    startLocation.setLongitude(longitude);
                    userActivity.setStartLocation(startLocation);

                    ActivityLocation endLocation = new ActivityLocation();
                    address = cursor.getString(cursor.getColumnIndex(ActivitiesConstants.TRIP.END_ADDRESS));
                    latitude = cursor.getDouble(cursor.getColumnIndex(ActivitiesConstants.TRIP.END_LATITUDE));
                    longitude = cursor.getDouble(cursor.getColumnIndex(ActivitiesConstants.TRIP.END_LONGITUDE));
                    endLocation.setAddress(address);
                    endLocation.setLatitude(latitude);
                    endLocation.setLongitude(longitude);
                    userActivity.setEndLocation(endLocation);

                    int activity = cursor.getInt(cursor.getColumnIndex(ActivitiesConstants.TRIP.ACTIVITY_TYPE));
                    userActivity.setActivity(activityIds.get(activity));

                    //Set the header for this trip.
                    userActivity.setHeaderTxt(DateTimeUtil.getTripHeaderText(userActivity.getStartTime()));
                    if( !headerMap.containsKey(userActivity.getHeaderTxt())) {
                        headerMap.put(userActivity.getHeaderTxt(), Integer.valueOf(headerId));
                        userActivity.setHeaderId(headerId);
                        headerId++;
                    } else {
                        Integer headerIdInteger = headerMap.get(userActivity.getHeaderTxt());
                        userActivity.setHeaderId(headerIdInteger.intValue());
                    }

                    tripsList.add(userActivity);
                } while (cursor.moveToNext());
            }
        }

        if(cursor != null)
            cursor.close();


        return tripsList;
    }

}
