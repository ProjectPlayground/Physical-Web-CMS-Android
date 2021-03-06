package org.physical_web.cms.exhibits;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.physical_web.cms.beacons.Beacon;
import org.physical_web.cms.beacons.BeaconManager;
import org.physical_web.cms.beacons.MacAddress;
import org.physical_web.cms.sync.ContentSynchronizer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import util.MiscFile;

import util.MiscFile;

import static util.MiscFile.deleteDir;
import static util.MiscFile.readFile;
import static util.MiscFile.writeToFile;

/**
 * Represents an exhibition, or a set of content assigned to a number of beacons. These sets can
 * be composed, deployed and swapped from the app. Create new exhibits with
 * {@link #initializeIntoFolder(String, File)} and thereafter load them with
 * {@link #loadFromFolder(File)}
 */
public class Exhibit {
    private static final String TAG = Exhibit.class.getSimpleName();
    private static final String METADATA_FILE_NAME = "metadata.json";

    private long id;
    private Map<Beacon, File> contentFolderForBeacon;
    private Map<Beacon, List<ExhibitContent>> contentsForBeacon;
    private JSONObject metadata;
    private File exhibitFolder;

    /**
     * Load an exhibit and return it from an already created folder
     *
     * @param exhibitFolder folder that contains exhibit
     * @return loaded exhibit
     */
    public static Exhibit loadFromFolder(File exhibitFolder) {
        if (exhibitFolder.isFile())
            throw new IllegalArgumentException("Passed file, not folder");

        Exhibit loadedExhibit = new Exhibit();

        loadedExhibit.exhibitFolder = exhibitFolder;
        // exhibit folder name matches the unique ID of the exhibit
        loadedExhibit.id = Long.valueOf(exhibitFolder.getName());

        // load the metadata file
        File metadataFile = new File(exhibitFolder, METADATA_FILE_NAME);
        loadedExhibit.metadata = loadMetadataFile(metadataFile);

        // map of beacons to the folders their contents are stored
        loadedExhibit.contentFolderForBeacon = findFoldersForBeacons(exhibitFolder);
        // load exhibit contents per beacon into map
        loadedExhibit.contentsForBeacon =
                loadedExhibit.loadBeaconContentMap(loadedExhibit.contentFolderForBeacon);

        return loadedExhibit;
    }

    /**
     * Create a new exhibit, writing it to disk.
     *
     * @param exhibitName  name of the new exhibit
     * @param parentFolder location where the folder for the exhibit will be created
     * @return the new exhibit
     */
    public static Exhibit initializeIntoFolder(String exhibitName, File parentFolder) {
        if (!parentFolder.exists() || parentFolder.isFile())
            throw new IllegalArgumentException("Invalid parent folder");

        Exhibit exhibit = new Exhibit();
        // id is randomly generated to avoid collisions
        exhibit.id = new Random().nextLong();

        // folder name is same as id
        String exhibitFolderName = String.valueOf(exhibit.id);
        File exhibitFolder = new File(parentFolder, exhibitFolderName);

        Boolean createFolderSuccess = exhibitFolder.mkdir();
        if (!createFolderSuccess)
            throw new InternalError("Couldn't create exhibit folder");

        createBeaconContentFolders(exhibitFolder);
        createExhibitMetadataFile(exhibitName, exhibitFolder);

        return Exhibit.loadFromFolder(exhibitFolder);
    }

    // create folders to store contents for each beacon in
    private static void createBeaconContentFolders(File exhibitFolder) {
        if (!exhibitFolder.exists() || exhibitFolder.isFile())
            throw new IllegalArgumentException("Invalid exhibit folder");

        BeaconManager beaconManager = BeaconManager.getInstance();
        List<Beacon> beacons = beaconManager.getAllBeacons();

        for (Beacon beacon : beacons) {
            // content is stored inside folder with name that matches beacon id
            String beaconContentFolderName = String.valueOf(beacon.address.toString());
            File beaconContentFolder = new File(exhibitFolder, beaconContentFolderName);
            Boolean createContentFolderSuccess = beaconContentFolder.mkdir();
            if (!createContentFolderSuccess)
                throw new InternalError("Couldn't create folder");
        }
    }

    // initialize a JSON file that stores the basic metadata of this exhibit
    private static void createExhibitMetadataFile(String exhibitName, File exhibitFolder) {
        if (!exhibitFolder.exists() || exhibitFolder.isFile())
            throw new IllegalArgumentException("Invalid exhibit folder");

        File metadataFile = new File(exhibitFolder, METADATA_FILE_NAME);
        if (metadataFile.exists())
            throw new IllegalStateException("Metadata file already exists");

        try {
            JSONObject newMetadata = new JSONObject();
            // basic exhibit details stored here
            newMetadata.put("name", exhibitName);
            newMetadata.put("active", false);
            newMetadata.put("description", "");

            // an array of beacons and their contents, chiefly useful to maintain order of contents
            // within a beacon.
            BeaconManager beaconManager = BeaconManager.getInstance();
            List<Beacon> beaconList = beaconManager.getAllBeacons();
            JSONArray beacons = new JSONArray();
            for (Beacon beacon : beaconList) {
                JSONObject beaconJSONMetadata = new JSONObject();
                beaconJSONMetadata.put("address", beacon.address.toString());
                beaconJSONMetadata.put("contents", new JSONArray());
                beacons.put(beaconJSONMetadata);
            }
            newMetadata.put("beacons", beacons);

            writeToFile(metadataFile, newMetadata.toString());
        } catch (JSONException jsonException) {
            Log.e(TAG, "Couldn't create metadata JSON: " + jsonException);
        } catch (IOException iOException) {
            Log.e(TAG, "Couldn't create metadata file: " + iOException);
        }
    }

    private Exhibit() {
    }

    // returns a map from beacons to the files where their contents should be stored
    private static Map<Beacon, File> findFoldersForBeacons(File exhibitFolder) {
        Map<Beacon, File> beaconFileMap = new HashMap<>();

        if (!exhibitFolder.exists() || exhibitFolder.isFile())
            throw new IllegalArgumentException("Invalid exhibit folder");

        BeaconManager beaconManager = BeaconManager.getInstance();

        for (File folder : exhibitFolder.listFiles()) {
            if (!folder.isFile()) {
                // beacon folder names are the id's of the corresponding beacon
                try {
                    MacAddress targetAddress = MacAddress.fromString(folder.getName());
                    Beacon correspondingBeacon = beaconManager
                            .getBeaconByAddress(targetAddress);
                    beaconFileMap.put(correspondingBeacon, folder);
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Odd, no beacon for folder: " + folder.getAbsolutePath());
                }
            }
        }

        return beaconFileMap;
    }

    /**
     * Returns title of the exhibit
     */
    public String getTitle() {
        try {
            return metadata.getString("name");
        } catch (JSONException e) {
            Log.e(TAG, "Couldn't find name: " + e);
            return "Unknown";
        }
    }

    /**
     * Return a folder that contains all the data of this exhibit
     */
    public File getExhibitFolder() {
        return exhibitFolder;
    }

    /**
     * Get a unique identifier for this exhibit
     */
    public long getId() {
        return id;
    }

    /**
     * Return the description of the exhibit
     */
    public String getDescription() {
        try {
            return this.metadata.getString("description");
        } catch (JSONException e) {
            Log.e(TAG, "JSONEXCEPTION while getting description");
            return "Unknown";
        }
    }

    public void setTitle(String newTitle) {
        try {
            this.metadata.put("name", newTitle);
            saveMetadata();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Sets the description of the exhibit, storing it persistently
     *
     * @param newDescription the new description
     */
    public void setDescription(String newDescription) {
        try {
            this.metadata.put("description", newDescription);
            saveMetadata();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Write any modification in content order to disk
     *
     * @param beacon Beacon whose contents have been reordered
     */
    public void persistContentChanges(Beacon beacon) {
        List<ExhibitContent> changedContents = contentsForBeacon.get(beacon);
        JSONObject beaconMetadata = getBeaconMetadata(beacon);
        try {
            JSONArray changedContentList = new JSONArray();
            for (ExhibitContent exhibitContent : changedContents) {
                changedContentList.put(exhibitContent.getContentName());
            }
            beaconMetadata.put("contents", changedContentList);
            saveMetadata();
        } catch (JSONException e) {
            Log.e(TAG, "Error persisting content changes: " + e);
        } catch (IOException e) {
            Log.e(TAG, "Error writing new content order to JSON" + e);
        }
    }

    // returns a map between the provided beacons and a list of their contents
    private Map<Beacon, List<ExhibitContent>> loadBeaconContentMap
    (Map<Beacon, File> beaconFileMap) {
        Map<Beacon, List<ExhibitContent>> beaconContentMap = new HashMap<>();

        BeaconManager beaconManager = BeaconManager.getInstance();
        List<Beacon> beacons = beaconManager.getAllBeacons();

        for (Beacon beacon : beacons) {
            File beaconContentFolder = beaconFileMap.get(beacon);

            if (beaconContentFolder == null) {
                Log.e(TAG, "No folder for beacon: " + beacon.friendlyName);
            } else {
                List<ExhibitContent> beaconContents =
                        loadBeaconContents(beacon);
                beaconContentMap.put(beacon, beaconContents);
            }
        }

        return beaconContentMap;
    }

    // given a beacon, return a list of exhibit contents by loading the files found
    // in the metadata associated with that beacon.
    private List<ExhibitContent> loadBeaconContents(Beacon beacon) {
        List<ExhibitContent> beaconContents = new LinkedList<>();
        File beaconFolder = contentFolderForBeacon.get(beacon);

        try {
            JSONArray registeredContents = getBeaconMetadata(beacon).getJSONArray("contents");

            for (int i = 0; i < registeredContents.length(); i++) {
                String fileName = registeredContents.getString(i);
                File contentFile = new File(beaconFolder, fileName);

                if (!contentFile.exists())
                    throw new IllegalStateException("file referenced in JSON but doesn't exist: "
                            + fileName);

                beaconContents.add(ExhibitContent.fromFile(contentFile));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Trouble loading beacon contents from JSON file: " + e);
        }

        return beaconContents;
    }

    // load the exhibit metadata from a JSON file on disk
    private static JSONObject loadMetadataFile(File metadataFile) {
        if (!metadataFile.exists() || !metadataFile.isFile())
            throw new IllegalArgumentException("metadata file isn't valid");

        JSONObject readMetadata = null;

        try {
            String metadataContents = readFile(metadataFile);
            readMetadata = new JSONObject(metadataContents);
        } catch (IOException e) {
            Log.e(TAG, "Couldn't read metadata file: " + e);
        } catch (JSONException e) {
            Log.e(TAG, "Couldn't parse JSON for metadata file: " + e);
        }

        return readMetadata;
    }

    // write the metadata as a JSON file to disk
    private void saveMetadata() throws IOException {
        File target = new File(exhibitFolder, METADATA_FILE_NAME);
        writeToFile(target, this.metadata.toString());
    }

    /**
     * Inform the exhibit that there is a new beacon that must be supported. Must be called to
     * be able to store content for the new beacon.
     *
     * @param newBeacon beacon to add to exhibit
     */
    public void configureForAdditionalBeacon(Beacon newBeacon) {
        MacAddress beaconAddress = newBeacon.address;

        File beaconContentFolder = new File(exhibitFolder, beaconAddress.toString());
        beaconContentFolder.mkdir();

        if (getContentForBeacon(newBeacon) == null) {
            try {
                JSONObject beacon = new JSONObject();
                beacon.put("address", beaconAddress.toString());
                JSONArray contents = new JSONArray();
                beacon.put("contents", contents);

                metadata.getJSONArray("beacons").put(beacon);
                saveMetadata();
            } catch (Exception e) {
                Log.e(TAG, "Error editing JSON: " + e);
            }
        }

        contentFolderForBeacon.put(newBeacon, beaconContentFolder);
        contentsForBeacon.put(newBeacon, loadBeaconContents(newBeacon));
    }

    /**
     * Inform this exhibit that the passed beacon should be removed for the list of beacons
     * supported. This will also remove any content associated with that beacon.
     *
     * @param removedBeacon Beacon to remove
     */
    public void configureForRemovedBeacon(Beacon removedBeacon) {
        File beaconContentFolder = contentFolderForBeacon.get(removedBeacon);
        if (beaconContentFolder == null)
            throw new IllegalArgumentException("No such beacon to delete");

        try {
            JSONArray beacons = metadata.getJSONArray("beacons");
            int targetIndex = -1;

            for (int i = 0; i < beacons.length(); i++) {
                JSONObject currentBeacon = beacons.getJSONObject(i);

                // name is stored in first field
                if (currentBeacon.getString("address")
                        .equals(removedBeacon.address.toString())) {
                    targetIndex = i;
                }
            }

            if (targetIndex != -1) {
                beacons.remove(targetIndex);
                saveMetadata();
            } else {
                throw new IllegalArgumentException("No such beacon found to delete");
            }
        } catch (Exception e) {
            Log.e(TAG, "Removing beacon from metadata failed: " + e);
        }

        ContentSynchronizer.getInstance().deleteSyncedEquivalent(beaconContentFolder);
        deleteDir(beaconContentFolder);
    }

    /**
     * Returns an list of ExhibitContents given the name of a beacon
     *
     * @param beacon to find exhibits associated with
     * @return Exhibit contents for that beacon
     */
    public List<ExhibitContent> getContentForBeacon(Beacon beacon) {
        return contentsForBeacon.get(beacon);
    }

    /**
     * Provided a URI and a beacon, persistently inserts the content at the URI into the list of
     * content associated with the beacon.
     *
     * @param uri    points to multimedia that will be added to exhibit contents
     * @param beacon that content will be associated with
     * @param ctx    activity context
     */
    public void insertContent(Uri uri, Beacon beacon, Context ctx) {
        String displayName;
        InputStream inputStream;

            Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null, null);
            cursor.moveToFirst();
            displayName = cursor.getString(
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            cursor.close();

        File beaconFolder = contentFolderForBeacon.get(beacon);
        File localCopy = MiscFile.copyURIContentsToFolder(uri, displayName, beaconFolder, ctx);
        if (localCopy == null)
            throw new IllegalStateException("Couldn't make local copy of contents at URI");

        appendContentToMetadata(displayName, beacon);
        contentsForBeacon.get(beacon).add(ExhibitContent.fromFile(localCopy));
    }

    // add content at end of metadata for provided beacon
    private void appendContentToMetadata(String filename, Beacon beacon) {
        try {
            JSONObject beaconMetadata = getBeaconMetadata(beacon);
            JSONArray contents = beaconMetadata.getJSONArray("contents");
            contents.put(filename);
            saveMetadata();
        } catch (Exception e) {
            Log.e(TAG, "modifying metadata failed for content with filename: " + filename);
        }
    }

    /**
     * Remove the content from this exhibit permanently and persistently
     *
     * @param content to be removed
     * @param beacon  associated with content
     */
    public void removeContent(final ExhibitContent content, Beacon beacon) {
        removeContentMetadata(content, beacon);
        contentsForBeacon.get(beacon).remove(content);

        new Thread(new Runnable() {
            @Override
            public void run() {
                File contentFile = content.getContentFile();
                ContentSynchronizer.getInstance().deleteSyncedEquivalent(contentFile);
                contentFile.delete();
            }
        }).start();
    }

    // remove content at beacon from metadata, writing changes to disk
    private void removeContentMetadata(ExhibitContent content, Beacon beacon) {
        try {
            JSONArray beaconContents = getBeaconMetadata(beacon).getJSONArray("contents");
            for (int i = 0; i < beaconContents.length(); i++) {
                String contentName = beaconContents.getString(i);
                if (contentName.equals(content.getContentName())) {
                    beaconContents.remove(i);
                    saveMetadata();
                    return;
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Trouble removing content from metadata");
        } catch (IOException e) {
            Log.e(TAG, "Error updating metadata file while removing content");
        }

        throw new IllegalArgumentException("No such contents found");
    }

    // get a JSONObject that contains the metadata for the provided beacon
    private JSONObject getBeaconMetadata(Beacon beacon) {
        try {
            JSONArray beacons = metadata.getJSONArray("beacons");
            for (int i = 0; i < beacons.length(); i++) {
                JSONObject currentBeacon = beacons.getJSONObject(i);
                if (currentBeacon.getString("address").equals(beacon.address.toString()))
                {
                    return currentBeacon;
                }
            }

            throw new IllegalArgumentException("No such beacon found");
        } catch (JSONException e) {
            Log.e(TAG, "error getting beacon metadata for beacon " + beacon.friendlyName);
            return null;
        }
    }
}
