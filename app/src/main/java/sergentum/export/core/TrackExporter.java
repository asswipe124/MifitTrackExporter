package sergentum.export.core;

import sergentum.export.Starter;
import sergentum.export.core.Model.Track;
import sergentum.export.core.Model.TrackPoint;
import sergentum.export.core.RawData.QueryData;
import sergentum.export.core.RawData.RawTrackData;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static sergentum.export.core.Model.formatTimestampHumanReadable;


public class TrackExporter {

    // todo add settings
    public static boolean debug = false;

    static final String COMMA = ",";
    static final String SEMICOLON = ";";
    static final String EMPTY_VALUE = "-";
    static final String CSV_COLUMN_DELIMITER = ";";

    public static String DEVICE_PATH = "";
    public static String FILE_FORMAT;
    // I HATE YOU DEVELOPER WHEN YOU PUT YOUR F*CKING FILES INTO THE ROOT OF MY STORAGE
    private static String EXPORT_PATH = "Android/Mifit/";
    public static final String DEBUG_OUT_PATH = "debug/";
    public static final String DEBUG_LOG_FILE = "log.txt";
    private static final String RAW_CSV = "-raw.csv";
    private static final String TCX_EXT = ".tcx";
    private static final String GPX_EXT = ".gpx";

    private Starter starter;

    public TrackExporter(Starter starter) {
        this.starter = starter;
    }

    public static String getFullPath() {
        return DEVICE_PATH + EXPORT_PATH;
    }

    public static String getDebugPath() {
        return DEVICE_PATH + EXPORT_PATH + DEBUG_OUT_PATH;
    }

    public void launchExport(ArrayList<QueryData> rawTrackDataList) {
        for (QueryData queryData : rawTrackDataList) {
            RawData rawData = new RawData(starter, queryData);
            long start = System.currentTimeMillis();
            boolean successfull;
            String filePath;
            String message = "";

            RawTrackData rawTrackData = rawData.parseRawData();
            if (debug) {
                String filename = rawTrackData.startTime + RAW_CSV;
                filePath = EXPORT_PATH + DEBUG_OUT_PATH + filename;
                String fullPath = getFullPath() + filename;
                boolean b = starter.writeStringToFile(rawTrackData.toString(), fullPath);
                if (b) {
                    starter.log(filePath + " saved");
                } else {
                    starter.log(filePath + " can't save");
                }
            }

            Track track = compileDataToTrack(rawTrackData);
            starter.log("FileFormat: " + FILE_FORMAT);
            switch (FILE_FORMAT) {
                case ".tcx": {
                    message += exportTCX(track);
                    break;
                }
                case ".gpx": {
                    message += exportGPX(track);
                    break;
                }
                default: {
                    message += exportTCX(track);
                    message += "\n" + exportGPX(track);
                    break;
                }
            }

            long stop = System.currentTimeMillis();
            String successMessage = message + "\n" +
                    "saved to " +  EXPORT_PATH + " in " + (stop - start) + " ms ";

            starter.log(successMessage);
            starter.showToast(successMessage, 1);
        }
    }

    private String exportGPX(Track track) {
        String fileName = generateFileName(track);
        String gpx = Printer.printGpx(track);
        String gpxFullPath = getFullPath() + fileName + GPX_EXT;
        starter.writeStringToFile(gpx, gpxFullPath);
        return fileName + GPX_EXT;
    }

    private String exportTCX(Track track) {
        String fileName = generateFileName(track);
        String tcx = Printer.printTcx(track);
        String tcxFullPath = getFullPath() + fileName + TCX_EXT;
        starter.writeStringToFile(tcx, tcxFullPath);
        return fileName + TCX_EXT;
    }

    private static String generateFileName(Track track) {
        return String.format(Locale.US, "%s_%d",
                formatTimestampHumanReadable(track.startTime),
                track.distance);
    }

    private Track compileDataToTrack(RawTrackData rawTrackData) {
        Track track = new Track();
        long timestamp = rawTrackData.startTime;

        track.size = (rawTrackData.size);
        track.startTime = (timestamp);
        track.endTime = (rawTrackData.endTime);
        track.duration = (rawTrackData.costTime);
        track.distance = rawTrackData.distance;
        track.activityType = (rawTrackData.activityType);

        // HR points is a base for track export
        // additional info will be added to hr points
        timestamp = rawTrackData.startTime;
        ArrayList<TrackPoint> hrTrackPoints = new ArrayList<>();
        ArrayList<Integer> hrPoints = rawTrackData.hrPoints;
        for (Integer hrPoint : hrPoints) {
            TrackPoint trackPoint = new TrackPoint();
            trackPoint.timestamp = timestamp;
            trackPoint.heartRate = hrPoint;
            hrTrackPoints.add(trackPoint);
            timestamp += 1;
        }

        timestamp = rawTrackData.startTime;
        Map<Long, TrackPoint> coordTrackPointMap = new TreeMap<>();
        for (int i = 0; i < rawTrackData.size; i++) {
            TrackPoint trackPoint = new TrackPoint();
            Integer integer = rawTrackData.times.get(i);
            if (integer == 0) {
                integer = 1;
            }
            trackPoint.altitude = rawTrackData.coordinates.get(i).altitude;
            trackPoint.latitude = rawTrackData.coordinates.get(i).latitude;
            trackPoint.longitude = rawTrackData.coordinates.get(i).longitude;
            timestamp += integer;
            trackPoint.timestamp = timestamp;
            coordTrackPointMap.put(timestamp, trackPoint);
        }


        timestamp = rawTrackData.startTime;
        Map<Long, TrackPoint> stepTrackPointsMap = new TreeMap<>();
        ArrayList<Model.Step> steps = rawTrackData.steps;
        for (Model.Step step : steps) {
            TrackPoint trackPoint = new TrackPoint();

            trackPoint.cadence = step.cadence;
            trackPoint.stride = step.stride;
            timestamp = timestamp + step.first;
            trackPoint.timestamp = timestamp;
            stepTrackPointsMap.put(timestamp, trackPoint);
        }

        if (debug) {
            String debugPoints = Printer.printRawPoints(hrTrackPoints, coordTrackPointMap, stepTrackPointsMap);
            starter.writeStringToFile(debugPoints, getDebugPath() + rawTrackData.startTime + "-points.csv");
        }

        track.trackPoints = joinPointArrays(hrTrackPoints, coordTrackPointMap, stepTrackPointsMap);
        return track;
    }

    private ArrayList<TrackPoint> joinPointArrays(
            ArrayList<TrackPoint> hrPoints,
            Map<Long, TrackPoint> coordPointsMap,
            Map<Long, TrackPoint> stepPointsMap
    ) {
        if (debug) {
            starter.log("Coord points map before join:" + coordPointsMap.size());
        }
        ArrayList<TrackPoint> resultPoints = new ArrayList<>();
        long timestamp;
        TrackPoint lastHrPoint = null;
        for (TrackPoint hrPoint : hrPoints) {
            timestamp = hrPoint.timestamp;
            TrackPoint coordPoint = coordPointsMap.get(timestamp);
            joinPoints(hrPoint, coordPoint);
            coordPointsMap.remove(timestamp);

            if (stepPointsMap.size() > 0) {
                TrackPoint stepPoint = stepPointsMap.get(timestamp);
                joinPoints(hrPoint, stepPoint);
                stepPointsMap.remove(timestamp);
            }
            resultPoints.add(hrPoint);
            lastHrPoint = hrPoint;
        }

        for (Map.Entry<Long, TrackPoint> coordTrackPointEntry : coordPointsMap.entrySet()) {
            timestamp = coordTrackPointEntry.getKey();
            TrackPoint coordPoint = coordTrackPointEntry.getValue();
            TrackPoint stepPoint = stepPointsMap.get(timestamp);
            joinPoints(coordPoint, stepPoint);
            joinPoints(coordPoint, lastHrPoint);
            resultPoints.add(coordPoint);
        }
        return resultPoints;
    }

    private static void joinPoints(TrackPoint p1, TrackPoint p2) {
        if (p2 != null) {
            p1.latitude = p2.latitude != null ? p2.latitude : p1.latitude;
            p1.longitude = p2.longitude != null ? p2.longitude : p1.longitude;
            p1.altitude = p2.altitude != null ? p2.altitude : p1.altitude;
            p1.pace = p2.pace != null ? p2.pace : p1.pace;
            p1.cadence = p2.cadence != null ? p2.cadence : p1.cadence;
            p1.stride = p2.stride != null ? p2.stride : p1.stride;
        }
    }
}
