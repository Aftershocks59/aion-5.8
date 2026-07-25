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
package com.aionemu.commons.configs;

import com.aionemu.commons.configuration.Property;

import java.io.File;

/**
 * This class holds all configuration of database
 *
 * @author SoulKeeper
 */
public class DatabaseConfig {

    /**
     * Default database url.
     */
    @Property(key = "database.url", defaultValue = "jdbc:mysql://localhost:3306/aion_uni")
    public static String DATABASE_URL;

    /**
     * Name of database Driver
     */
    @Property(key = "database.driver", defaultValue = "org.mariadb.jdbc.Driver")
    public static Class<?> DATABASE_DRIVER;

    /**
     * Default database user
     */
    @Property(key = "database.user", defaultValue = "root")
    public static String DATABASE_USER;

    /**
     * Default database password
     */
    /*
	 * Defaults to no password. A blank value in the file counts as absent, so a
	 * hard-coded default here would be sent whenever the environment supplies
	 * nothing, and the server would report a password it was never given.
	 */
	@Property(key = "database.password", defaultValue = "")
    public static String DATABASE_PASSWORD;

    /**
     * Brings the schema up to date when the server starts.
     * <p>
     * Turn it off to run the migrations by hand, for instance when several servers
     * share one database and only one of them should apply them.
     */
    @Property(key = "database.migration.enable", defaultValue = "true")
    public static boolean DATABASE_MIGRATION_ENABLE;

    /**
     * Holds the migrations to apply, read from the server working directory.
     */
    @Property(key = "database.migration.path", defaultValue = "./sql/migration")
    public static String DATABASE_MIGRATION_PATH;

    /**
     * Caps how many connections the pool opens. Defaults to 10, matching the two
     * partitions of five that the previous pool was configured with.
     */
    @Property(key = "database.pool.size.max", defaultValue = "10")
    public static int DATABASE_POOL_SIZE_MAX;

    /**
     * Sets how many connections stay open while idle, so a burst of queries does
     * not pay for opening them.
     */
    @Property(key = "database.pool.size.min", defaultValue = "4")
    public static int DATABASE_POOL_SIZE_MIN;

    /**
     * Location of database script context descriptor
     */
    @Property(key = "database.scriptcontext.descriptor", defaultValue = "./data/scripts/system/database/database.xml")
    public static File DATABASE_SCRIPTCONTEXT_DESCRIPTOR;

}
