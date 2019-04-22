package cn.com.smartdevices.bracelet.gps.ui.sport.detail.export;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import cn.com.smartdevices.bracelet.gps.ui.sport.detail.export.core.RawQueryData;
import cn.com.smartdevices.bracelet.gps.ui.sport.detail.export.core.TrackExporter;
import cn.com.smartdevices.bracelet.gps.ui.sport.detail.export.core.TrackHeader;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


public class TrackExportStarter {
    static final String TAG = "mifit";
    private Activity activity;
    private String dbPath;
    static FileHelper FILE_HELPER;

    private static final String TRACK_ID_QUERY = "   SELECT TRACKRECORD.TRACKID," +
            "       TRACKDATA.TYPE," +
            "       TRACKRECORD.DISTANCE," +
            "       TRACKRECORD.COSTTIME" +
            "       FROM TRACKDATA, TRACKRECORD" +
            "       WHERE TRACKDATA.TRACKID = TRACKRECORD.TRACKID ;";

    private static final String TRACK_DATA_QUERY =
            "SELECT " +
                    "TRACKDATA.TRACKID," +
                    "TRACKDATA.SIZE," +
                    "TRACKDATA.BULKLL," +
                    "TRACKDATA.BULKGAIT," +
                    "TRACKDATA.BULKAL," +
                    "TRACKDATA.BULKTIME," +
                    "TRACKDATA.BULKHR," +
                    "TRACKDATA.BULKPACE," +
                    "TRACKDATA.BULKPAUSE," +
                    "TRACKDATA.BULKSPEED," +
                    "TRACKDATA.TYPE," +
                    "TRACKDATA.BULKFLAG," +
                    "TRACKRECORD.COSTTIME," +
                    "TRACKRECORD.ENDTIME, " +
                    "TRACKRECORD.DISTANCE " +
                    "FROM TRACKDATA, TRACKRECORD " +
                    "WHERE TRACKDATA.TRACKID = TRACKRECORD.TRACKID " +
                    "AND TRACKDATA.TRACKID = ";

    public TrackExportStarter(Activity activity) {
        this.activity = activity;
        TrackExporter.DEVICE_PATH = Environment.getExternalStorageDirectory().getPath() + "/";

        FILE_HELPER = new FileHelper(activity);

        if (FILE_HELPER.logWriter != null) {
            DBHelper dbHelper = new DBHelper(activity);
            dbPath = dbHelper.getDbPath();
        } else {
            Toast.makeText(activity, "fileHelper wasn't created", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "fileHelper wasn't created");
        }
    }

    public void showTracks() {
        if (dbPath == null) {
            Toast.makeText(activity, "database not found", Toast.LENGTH_SHORT).show();
        } else {
            try (
                    SQLiteDatabase sqLiteDatabase = activity.openOrCreateDatabase(dbPath, Context.MODE_PRIVATE, null);
                    Cursor cursor = sqLiteDatabase.rawQuery(TRACK_ID_QUERY, null)
            ) {
                cursor.moveToFirst();
                Map<Long, TrackHeader> trackHeaderMap = new TreeMap<>();
                StringBuilder stringBuilder = new StringBuilder();
                while (!cursor.isAfterLast()) {
                    for (int i = 0; i < cursor.getColumnCount(); i++) {
                        stringBuilder.append(cursor.getString(i)).append(" ");
                        if (i == 0) {
                            String string = cursor.getString(i);
                            long l = Long.parseLong(string);
                            Date date = new Date(l * 1000);
                            stringBuilder.append(date).append(" ");
                        }
                    }
                    stringBuilder.append("\n");

                    TrackHeader trackHeader = new TrackHeader();
                    long trackId = cursor.getLong(0);
                    trackHeader.id = trackId;
                    trackHeader.type = cursor.getInt(1);
                    trackHeader.distance = cursor.getInt(2);
                    trackHeader.duration = cursor.getInt(3);
                    trackHeaderMap.put(trackId, trackHeader);
                    cursor.moveToNext();
                }
                cursor.close();
                sqLiteDatabase.close();

                FILE_HELPER.log(stringBuilder.toString());

                ArrayList<Long> trackIds = new ArrayList<>();
                String[] trackDesc = new String[trackHeaderMap.size()];
                Set<Map.Entry<Long, TrackHeader>> entries =
                        ((TreeMap<Long, TrackHeader>) trackHeaderMap).descendingMap().entrySet();
                int i = 0;
                for (Map.Entry<Long, TrackHeader> entry : entries) {
                    trackIds.add(entry.getKey());
                    trackDesc[i] = entry.getValue().toString();
                    i++;
                }

                ChooseTrackClickListener trackChooseListener = new ChooseTrackClickListener(this, trackIds);

                AlertDialog.Builder alert = new AlertDialog.Builder(activity);
                alert.setTitle("Choose track to export:");
                alert.setItems(trackDesc, trackChooseListener);
                alert.create().show();
            } catch (Exception e) {
                FILE_HELPER.log("showTracks():" + e.getMessage());
            }
        }
    }

    void readRawDataWithId(long id) {
        try (SQLiteDatabase sqLiteDatabase = activity.openOrCreateDatabase(dbPath, Context.MODE_PRIVATE, null);
             Cursor cursor = sqLiteDatabase.rawQuery(TRACK_DATA_QUERY + id, null)
             ) {
            cursor.moveToFirst();
            ArrayList<RawQueryData> rawQueryDataArrayList = new ArrayList<>();
            if (!cursor.isAfterLast()) {
                RawQueryData rawQueryData = new RawQueryData();
                for (int i = 0; i < cursor.getColumnCount(); i++){
                    String columnValue = cursor.getString(i);
                    String columnName = cursor.getColumnName(i);
                    if (columnValue != null) {
                        if (columnName.equalsIgnoreCase("TRACKID")) {
                            rawQueryData.startTime = columnValue;
                        } else if (columnName.equalsIgnoreCase("ENDTIME")) {
                            rawQueryData.endTime = columnValue;
                        } else if (columnName.equalsIgnoreCase("COSTTIME")) {
                            rawQueryData.costTime = columnValue;
                        } else if (columnName.equalsIgnoreCase("SIZE")) {
                            rawQueryData.size = columnValue;
                        } else if (columnName.equalsIgnoreCase("TYPE")) {
                            rawQueryData.activityType = columnValue;
                        } else if (columnName.equalsIgnoreCase("DISTANCE")) {
                            rawQueryData.distance = columnValue;
                        } else if (columnName.equalsIgnoreCase("BULKLL")) {
                            rawQueryData.BULKLL = columnValue;
                        } else if (columnName.equalsIgnoreCase("BULKGAIT")) {
                            rawQueryData.BULKGAIT = columnValue;
                        } else if (columnName.equalsIgnoreCase("BULKAL")) {
                            rawQueryData.BULKAL = columnValue;
                        } else if (columnName.equalsIgnoreCase("BULKTIME")) {
                            rawQueryData.BULKTIME = columnValue;
                        } else if (columnName.equalsIgnoreCase("BULKHR")) {
                            rawQueryData.BULKHR = columnValue.replace(";,", ";1,");
                        } else if (columnName.equalsIgnoreCase("BULKPACE")) {
                            rawQueryData.BULKPACE = columnValue;
                        } else if (columnName.equalsIgnoreCase("BULKFLAG")) {
                            rawQueryData.BULKFLAG = columnValue;
                        }
                    }
                }
                rawQueryDataArrayList.add(rawQueryData);
            }
            TrackExporter trackExporter = new TrackExporter(FILE_HELPER);
            trackExporter.launchExport(rawQueryDataArrayList);

        } catch (Exception e) {
            FILE_HELPER.log("readRawDataWithId(" + id + "):" + e.getMessage());
        }
    }
}
