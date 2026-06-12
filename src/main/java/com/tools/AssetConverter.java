package com.tools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class AssetConverter {

    public static File getAssetAsFile(String assetPath) throws IOException {
        // 1. Open the stream from the JME assets folder
        // Remember, exclude "assets" from the path, just start with the subfolder
        InputStream is = AssetConverter.class.getResourceAsStream(assetPath);
        
        if (is == null) {
            throw new IOException("Could not find asset at path: " + assetPath);
        }
        var filePath = assetPath;
        // 2. Create a temporary file on the user's OS disk (.fnt extension preserved)
        File tempFile = File.createTempFile(filePath.substring(0, filePath.lastIndexOf('.')),filePath.substring(filePath.lastIndexOf('.') + 1));
        
        // 3. Mark it to delete automatically when the game closes
        tempFile.deleteOnExit();

        // 4. Stream the data directly into the temporary disk file
        Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        is.close();

        // 5. Return the real physical File object for your API
        return tempFile;
    }
}
