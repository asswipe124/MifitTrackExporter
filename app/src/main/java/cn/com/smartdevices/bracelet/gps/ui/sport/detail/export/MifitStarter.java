package cn.com.smartdevices.bracelet.gps.ui.sport.detail.export;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import cn.com.smartdevices.bracelet.gps.ui.sport.detail.export.core.Model.TrackHeader;
import cn.com.smartdevices.bracelet.gps.ui.sport.detail.export.core.RawData.QueryData;
import cn.com.smartdevices.bracelet.gps.ui.sport.detail.export.core.TrackExporter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;


public class MifitStarter extends Starter {
    public static final String TAG = "mifit";
    public static final String EXT_DB_NAME = "origin.db";
    private Activity activity;
    private String dbPath;
    private String logFilePath;

    private static final String TMP_DB_QUERY = "" +
            "CREATE TABLE IF NOT EXISTS dummy " +
            "(\"_id\"  INTEGER primary key autoincrement, \n" +
            "  \"CALENDAR\" INTEGER )";

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

    public MifitStarter(Activity activity) {
        this.activity = activity;
        TrackExporter.DEVICE_PATH = Environment.getExternalStorageDirectory().getPath() + "/";

        // TODO: 2019-04-23 add ru lang
        String language = Locale.getDefault().getLanguage();

        if (checkFilePath()) {
            dbPath = getDbPath();
        } else {
            Toast.makeText(activity, "can't get access to filesystem", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "can't get access to filesystem");
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

                log(stringBuilder.toString());

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
                log("showTracks():" + e.getMessage());
            }
        }
    }

    public class ChooseTrackClickListener implements DialogInterface.OnClickListener {
        private MifitStarter starter;
        private ArrayList<Long> trackIds;

        ChooseTrackClickListener(MifitStarter starter, ArrayList<Long> trackIds) {
            this.starter = starter;
            this.trackIds = trackIds;
        }

        public void onClick(DialogInterface dialogInterface, int i) {
            starter.readRawDataWithId(trackIds.get(i));
        }
    }

    private void readRawDataWithId(long id) {
        try (
                SQLiteDatabase sqLiteDatabase = activity.openOrCreateDatabase(dbPath, Context.MODE_PRIVATE, null);
                Cursor cursor = sqLiteDatabase.rawQuery(TRACK_DATA_QUERY + id, null)
        ) {
            cursor.moveToFirst();
            ArrayList<QueryData> queryDataArrayList = new ArrayList<>();
            if (!cursor.isAfterLast()) {
                QueryData queryData = new QueryData();
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    String columnValue = cursor.getString(i);
                    String columnName = cursor.getColumnName(i);
                    mapRawDataToQueryData(queryData, columnName, columnValue);
                }
                queryDataArrayList.add(queryData);
            }
            TrackExporter trackExporter = new TrackExporter(this);
            trackExporter.launchExport(queryDataArrayList);

        } catch (Exception e) {
            log("readRawDataWithId(" + id + "):" + e.getMessage());
        }
    }

    private String getDbPath() {
        String result = checkExtDb();
        if (result == null) {
            result = findOriginDb();
        }
        return result;
    }

    private String checkExtDb() {
        String result;
        String mifit_dir_path = TrackExporter.getFullPath();
        checkIfPathExistAndCreate(mifit_dir_path);
        File mifit_dir = new File(mifit_dir_path);
        log("search for ext db in:" + mifit_dir_path);
        if (mifit_dir.exists()) {
            try {
                String[] list = mifit_dir.list();
                for (String fileName : list) {
                    File curFile = new File(mifit_dir_path, fileName);
                    if (!curFile.isDirectory() && curFile.getName().contains(EXT_DB_NAME)) {
                        result = curFile.getPath();
                        log("ext db found:" + result);
                        showToast("ext db found:" + result, 0);
                        return result;
                    }
                }
            } catch (Exception ex) {
                log("checkExtDb():" + ex.getMessage());
            }
        }
        log("ext db not found");
        return null;
    }

    private String findOriginDb() {
        Pattern pattern = Pattern.compile("^origin_db_[A-Za-z0-9]*$");

        String pathToDb = dbPathFinder();
        String pathToOriginDb;
        File directory = new File(pathToDb);
        String[] list = directory.list();

        for (String file : list) {
            boolean dbFound = pattern.matcher(file).find();
            log("file:" + file + " matches:" + dbFound);
            if (dbFound) {
                File dbFile = new File(pathToDb, file);
                pathToOriginDb = dbFile.getPath();
                log("origin db found: " + pathToOriginDb);
                return pathToOriginDb;
            }
        }

        log("origin db not found");
        return null;
    }

    private String dbPathFinder() {
        SQLiteDatabase sqLiteDatabase = activity.openOrCreateDatabase("tmp.db", Context.MODE_PRIVATE, null);
        sqLiteDatabase.execSQL(TMP_DB_QUERY);
        String tmpDbPath = sqLiteDatabase.getPath();
        sqLiteDatabase.close();
        File tmpDb = new File(tmpDbPath);
        log("origin db path:" + tmpDb.getParent());
        return tmpDb.getParent();
    }

    private boolean checkFilePath() {
        String filePath = TrackExporter.getDebugPath();
        if (checkIfPathExistAndCreate(filePath)) {
            logFilePath = filePath + TrackExporter.DEBUG_LOG_FILE;
            return log(".");
        } else {
            return false;
        }
    }

    public boolean log(String... args) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(new Date()).append("\r\n");
        for (String arg : args) {
            stringBuilder.append(arg).append("\r\n");
        }
        try (FileWriter fileWriter = new FileWriter(logFilePath, true)) {
            Log.d(TAG, stringBuilder.toString());
            fileWriter.write(stringBuilder.toString());
            fileWriter.flush();
        } catch (Exception e) {
            Log.e(TAG, "ex while logging:" + e.getMessage());
            return false;
        }
        return true;
    }



    public void showToast(String string, int length) {
        Toast.makeText(activity, string, length).show();
    }

    public static void mapRawDataToQueryData(QueryData queryData, String columnName, String columnValue) {
        if (columnValue != null) {
            if (columnName.equalsIgnoreCase("TRACKID")) {
                queryData.startTime = columnValue;
            } else if (columnName.equalsIgnoreCase("ENDTIME")) {
                queryData.endTime = columnValue;
            } else if (columnName.equalsIgnoreCase("COSTTIME")) {
                queryData.costTime = columnValue;
            } else if (columnName.equalsIgnoreCase("SIZE")) {
                queryData.size = columnValue;
            } else if (columnName.equalsIgnoreCase("TYPE")) {
                queryData.activityType = columnValue;
            } else if (columnName.equalsIgnoreCase("DISTANCE")) {
                queryData.distance = columnValue;
            } else if (columnName.equalsIgnoreCase("BULKLL")) {
                queryData.BULKLL = columnValue;
            } else if (columnName.equalsIgnoreCase("BULKGAIT")) {
                queryData.BULKGAIT = columnValue;
            } else if (columnName.equalsIgnoreCase("BULKAL")) {
                queryData.BULKAL = columnValue;
            } else if (columnName.equalsIgnoreCase("BULKTIME")) {
                queryData.BULKTIME = columnValue;
            } else if (columnName.equalsIgnoreCase("BULKHR")) {
                queryData.BULKHR = columnValue.replace(";,", ";1,");
            } else if (columnName.equalsIgnoreCase("BULKPACE")) {
                queryData.BULKPACE = columnValue;
            } else if (columnName.equalsIgnoreCase("BULKFLAG")) {
                queryData.BULKFLAG = columnValue;
            }
        }
    }
}
