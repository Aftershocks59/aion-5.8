/**
 * This file is part of Aion-Lightning <aion-lightning.org>.
 *
 *  Aion-Lightning is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Aion-Lightning is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details. *
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Aion-Lightning.
 *  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * Credits goes to all Open Source Core Developer Groups listed below
 * Please do not change here something, regarding the developer credits, except the "developed by XXXX".
 * Even if you edit a lot of files in this source, you still have no rights to call it as "your Core".
 * Everybody knows that this Emulator Core was developed by Aion Lightning 
 * @-Aion-Unique-
 * @-Aion-Lightning
 * @Aion-Engine
 * @Aion-Extreme
 * @Aion-NextGen
 * @Aion-Core Dev.
 */
package com.aionemu.commons.versionning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * @author lord_rex
 */
public class Version {

    private static final Logger log = LoggerFactory.getLogger(Version.class);

    /** Stands in when the build information cannot be read, so nothing prints null. */
    private static final String UNKNOWN = "Unknown";

    private String revision = UNKNOWN;
    private String date = UNKNOWN;
    private String branch = UNKNOWN;
    private String commitTime = UNKNOWN;

    public Version() {
    }

    public Version(Class<?> c) {
        loadInformation(c);
    }

    /**
     * Reads the build information from the manifest of the archive a class came
     * from.
     * <p>
     * Leaves the fields on their unknown default when the class was not loaded from
     * an archive. Running from a directory of compiled classes is what every Gradle
     * run does, and there is simply no manifest to read: reporting it as an error,
     * with a stack trace, said the server had a problem when it did not.
     */
    public void loadInformation(Class<?> c) {
        File jarName = Locator.getClassSource(c);
        if (jarName == null || jarName.isDirectory()) {
            log.debug("Skipping build information: " + jarName + " is not an archive.");
            return;
        }

        try (JarFile jarFile = new JarFile(jarName)) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                log.debug("Skipping build information: " + jarName + " carries no manifest.");
                return;
            }

            Attributes attrs = manifest.getMainAttributes();
            this.revision = getAttribute("Revision", attrs);
            this.date = getAttribute("Date", attrs);
            this.branch = getAttribute("Branch", attrs);
            this.commitTime = getAttribute("CommitTime", attrs);
        } catch (IOException e) {
            log.error("Unable to read the build information from '" + jarName.getAbsolutePath() + "'", e);
        }
    }

    public void transferInfo(String jarName, String type, File fileToWrite) {
        try {
            if (!fileToWrite.exists()) {
                log.error("Unable to Find File :" + fileToWrite.getName() + " Please Update your " + type);
                return;
            }
            // Close both on the way out, including when writing throws: this used to
            // leak the archive handle, which on Windows keeps the file locked.
            try (JarFile jarFile = new JarFile("./" + jarName);
                    OutputStream fos = new FileOutputStream(fileToWrite)) {
                Manifest manifest = jarFile.getManifest();
                if (manifest == null) {
                    log.warn("Unable to copy the manifest: " + jarName + " carries none.");
                    return;
                }
                manifest.write(fos);
            }
        } catch (IOException e) {
            log.error("Error, " + e);
        }
    }

    public final String getRevision() {
        return revision;
    }

    public final String getDate() {
        return date;
    }

    public final String getBranch() {
        return branch;
    }

    public final String getCommitTime() {
        return commitTime;
    }

    private final String getAttribute(String attribute, Attributes attrs) {
        String date = attrs.getValue(attribute);
        return date != null ? date : "Unknown " + attribute;
    }
}
