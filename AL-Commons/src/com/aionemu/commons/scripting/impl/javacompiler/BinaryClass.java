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
package com.aionemu.commons.scripting.impl.javacompiler;

import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;

/**
 * Holds the bytecode a compilation run produces for a single class, and hands it
 * back to the compiler and to the classloader on demand.
 * <p>
 * Extends {@link SimpleJavaFileObject} rather than {@code com.sun.tools.javac.file.BaseFileObject}:
 * that internal javac type was removed in JDK 9. Only the constructor was ever
 * inherited from it, so the public API of this class is unchanged.
 *
 * @author SoulKeeper
 */
public class BinaryClass extends SimpleJavaFileObject {

    /**
     * ClassName
     */
    private final String name;

    /**
     * Class data will be written here
     */
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

    /**
     * Locaded class will be set here
     */
    private Class<?> definedClass;

    /**
     * Constructor that accepts class name as parameter
     *
     * @param name class name
     */
    protected BinaryClass(String name) {
        // Build a synthetic URI: the bytecode lives in memory, not on disk.
        super(URI.create("bytes:///" + name.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        this.name = name;
    }

    /**
     * Returns name of this class with ".class" suffix
     *
     * @return name of this class with ".class" suffix
     */
    @Override
    public String getName() {
        return name + Kind.CLASS.extension;
    }

    /**
     * Creates new ByteArrayInputStream, it just wraps class binary data
     *
     * @return input stream for class data
     * @throws IOException never thrown
     */
    @Override
    public InputStream openInputStream() throws IOException {
        return new ByteArrayInputStream(baos.toByteArray());
    }

    /**
     * Opens ByteArrayOutputStream for class data
     *
     * @return output stream
     * @throws IOException never thrown
     */
    @Override
    public OutputStream openOutputStream() throws IOException {
        return baos;
    }

    /**
     * Throws {@link UnsupportedOperationException}
     *
     * @return nothing
     */
    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws {@link UnsupportedOperationException}
     *
     * @return nothing
     */
    @Override
    public Writer openWriter() throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Unsupported operation, always reutrns 0
     *
     * @return 0
     */
    @Override
    public long getLastModified() {
        return 0;
    }

    /**
     * Unsupported operation, returns false
     *
     * @return false
     */
    @Override
    public boolean delete() {
        return false;
    }

    /**
     * Returns class name
     *
     * @param path doesn't matter
     * @return class name
     */
    protected String inferBinaryName(Iterable<? extends File> path) {
        return name;
    }

    /**
     * Returns true if {@link javax.tools.JavaFileObject.Kind#CLASS}
     *
     * @param simpleName doesn't matter
     * @param kind       kind to compare
     * @return true if Kind is {@link javax.tools.JavaFileObject.Kind#CLASS}
     */
    @Override
    public boolean isNameCompatible(String simpleName, Kind kind) {
        return Kind.CLASS.equals(kind);
    }

    /**
     * Returns bytes of class
     *
     * @return bytes of class
     */
    public byte[] getBytes() {
        return baos.toByteArray();
    }

    /**
     * Returns class that was loaded from binary data of this object
     *
     * @return loaded class
     */
    public Class<?> getDefinedClass() {
        return definedClass;
    }

    /**
     * Sets class that was loaded by this object
     *
     * @param definedClass class that was loaded
     */
    public void setDefinedClass(Class<?> definedClass) {
        this.definedClass = definedClass;
    }

    /* (non-Javadoc)
     * @see javax.tools.JavaFileObject#getKind()
     */
    @Override
    public Kind getKind() {
        return Kind.CLASS;
    }

}
