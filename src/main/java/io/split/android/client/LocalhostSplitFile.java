package io.split.android.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import timber.log.Timber;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

public class LocalhostSplitFile {

    private final LocalhostSplitFactory _splitFactory;
    private final File _file;

    public LocalhostSplitFile(LocalhostSplitFactory splitFactory, String directory, String fileName) throws IOException {
        Preconditions.checkNotNull(directory);
        Preconditions.checkNotNull(fileName);

        _splitFactory = Preconditions.checkNotNull(splitFactory);
        _file = new File(directory, fileName);
    }

    public Map<String, String> readOnSplits() throws IOException {
        Map<String, String> onSplits = Maps.newHashMap();

        try (BufferedReader reader = new BufferedReader(new FileReader(_file))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] feature_treatment = line.split("\\s+");

                if (feature_treatment.length != 2) {
                    Timber.d("Ignoring line since it does not have exactly two columns: %s", line);
                    continue;
                }

                onSplits.put(feature_treatment[0], feature_treatment[1]);
                Timber.d("100%% of keys will see %s for %s", feature_treatment[1], feature_treatment[0]);
            }
        } catch (FileNotFoundException e) {
            Timber.w(e, "There was no file named %s found. " +
                    "We created a split client that returns default " +
                    "treatments for all features for all of your users. " +
                    "If you wish to return a specific treatment for a feature, " +
                    "enter the name of that feature name and treatment name separated " +
                    "by whitespace in %s; one pair per line. Empty lines or lines " +
                    "starting with '#' are considered comments", _file.getPath(), _file.getPath());
        }

        return onSplits;
    }
}