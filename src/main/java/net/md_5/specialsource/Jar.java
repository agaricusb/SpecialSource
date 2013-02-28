/**
 * Copyright (c) 2012, md_5. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * The name of the author may not be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.md_5.specialsource;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * This class wraps a {@link JarFile} or {@link JarInputStream} enabling quick
 * access to the jar's main class, as well as the ability to get the
 * {@link InputStream} of a class file, and speedy lookups to see if the
 * jar contains the specified class.
 */
@ToString
@EqualsAndHashCode
public class Jar {

    private final JarFile file;
    private final String main;
    private final Set<String> contains = new HashSet<String>();
    private final Map<String, ClassNode> classes = new HashMap<String, ClassNode>();
    private final Map<String, byte[]> classesData = new HashMap<String, byte[]>();

    public Jar(File file) throws IOException {
        JarFile jarFile = new JarFile(file);
        String main = null;

        Manifest manifest = jarFile.getManifest();
        if (manifest != null) {
            Attributes attributes = manifest.getMainAttributes();
            if (attributes != null) {
                String mainClassName = attributes.getValue("Main-Class");
                if (mainClassName != null) {
                    main = mainClassName.replace('.', '/');
                }
            }
        }

        this.main = main;
        this.file = jarFile;
    }

    public Jar(JarInputStream inputStream) throws IOException {
        JarInputStream jarInputStream = new JarInputStream(inputStream);
        String main = null;

        // Find main class
        Manifest manifest = jarInputStream.getManifest();
        if (manifest != null) {
            Attributes attributes = manifest.getMainAttributes();
            if (attributes != null) {
                String mainClassName = attributes.getValue("Main-Class");
                if (mainClassName != null) {
                    main = mainClassName.replace('.', '/');
                }
            }
        }

        // Cache all classes
        ZipEntry entry = null;
        while ((entry = jarInputStream.getNextEntry()) != null) {
            String clazz = entry.getName();
            int n;
            byte[] b = new byte[4096];
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            while ((n = jarInputStream.read(b, 0, b.length)) != -1) {
                byteArrayOutputStream.write(b);
            }

            byte[] data = byteArrayOutputStream.toByteArray();

            ClassReader cr = new ClassReader(data);
            ClassNode node = new ClassNode();
            cr.accept(node, 0);

            contains.add(clazz);
            classesData.put(clazz, data);
            classes.put(clazz, node);
        }

        this.main = main;
        this.file = null;
    }

    public boolean containsClass(String clazz) {
        return contains.contains(clazz) ? true : getClass(clazz) != null;
    }

    public InputStream getClass(String clazz) {
        try {
            ZipEntry e = file.getEntry(clazz + ".class");
            if (e != null) {
                contains.add(clazz);
            }
            return e == null ? null : file.getInputStream(e);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public ClassNode getNode(String clazz) {
        try {
            ClassNode cache = classes.get(clazz);
            if (cache != null) {
                return cache;
            }
            InputStream is = getClass(clazz);
            if (is != null) {
                ClassReader cr = new ClassReader(getClass(clazz));
                ClassNode node = new ClassNode();
                cr.accept(node, 0);
                classes.put(clazz, node);
                return node;
            } else {
                return null;
            }
        } catch (IOException ex) {
            System.out.println(clazz);
            throw new RuntimeException(ex);
        }
    }

    public String getMain() {
        return main;
    }

    public String getFilename() {
        return file != null ? file.getName() : "<stream>";
    }

    public InputStream getInputStream(ZipEntry zipEntry) throws IOException {
        if (file != null) {
            return file.getInputStream(zipEntry);
        } else {
            //ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(classesData.getka
            // TODO:
            return null;
        }
    }

    public Enumeration<JarEntry> getEntries() {
        // TODO
        return file.entries();
    }

    public static Jar init(String jar) throws IOException {
        File file = new File(jar);
        return init(file);
    }

    public static Jar init(File file) throws IOException {
        return new Jar(file);
    }
}
