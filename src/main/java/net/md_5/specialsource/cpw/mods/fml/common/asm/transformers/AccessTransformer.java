package net.md_5.specialsource.cpw.mods.fml.common.asm.transformers;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PROTECTED;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.io.LineProcessor;
import com.google.common.io.Resources;

public class AccessTransformer
{
    private static final boolean DEBUG = false;
    private class Modifier
    {
        public String name = "";
        public String desc = "";
        public int oldAccess = 0;
        public int newAccess = 0;
        public int targetAccess = 0;
        public boolean changeFinal = false;
        public boolean markFinal = false;
        protected boolean modifyClassVisibility;

        private void setTargetAccess(String name)
        {
            if (name.startsWith("public")) targetAccess = ACC_PUBLIC;
            else if (name.startsWith("private")) targetAccess = ACC_PRIVATE;
            else if (name.startsWith("protected")) targetAccess = ACC_PROTECTED;

            if (name.endsWith("-f"))
            {
                changeFinal = true;
                markFinal = false;
            }
            else if (name.endsWith("+f"))
            {
                changeFinal = true;
                markFinal = true;
            }
        }
    }

    private Multimap<String, Modifier> modifiers = ArrayListMultimap.create();

    public AccessTransformer(File rulesFile) throws IOException
    {
        readMapFile(rulesFile);
    }

    private void readMapFile(File rulesFile) throws IOException
    {
        URL rulesResource = rulesFile.toURI().toURL();
        Resources.readLines(rulesResource, Charsets.UTF_8, new LineProcessor<Void>()
        {
            @Override
            public Void getResult()
            {
                return null;
            }

            @Override
            public boolean processLine(String input) throws IOException
            {
                String line = Iterables.getFirst(Splitter.on('#').limit(2).split(input), "").trim();
                if (line.length()==0)
                {
                    return true;
                }
                List<String> parts = Lists.newArrayList(Splitter.on(" ").trimResults().split(line));
                if (parts.size()>2)
                {
                    throw new RuntimeException("Invalid config file line "+ input);
                }
                Modifier m = new Modifier();
                m.setTargetAccess(parts.get(0));
                List<String> descriptor = Lists.newArrayList(Splitter.on(".").trimResults().split(parts.get(1)));
                if (descriptor.size() == 1)
                {
                    m.modifyClassVisibility = true;
                }
                else
                {
                    String nameReference = descriptor.get(1);
                    int parenIdx = nameReference.indexOf('(');
                    if (parenIdx>0)
                    {
                        m.desc = nameReference.substring(parenIdx);
                        m.name = nameReference.substring(0,parenIdx);
                    }
                    else
                    {
                        m.name = nameReference;
                    }
                }
                modifiers.put(descriptor.get(0).replace('/', '.'), m);
                return true;
            }
        });
    }

    @SuppressWarnings("unchecked")
    public byte[] transform(String name, byte[] bytes)
    {
        if (bytes == null) { return null; }
        if (!modifiers.containsKey(name)) { return bytes; }

        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(bytes);
        classReader.accept(classNode, 0);

        Collection<Modifier> mods = modifiers.get(name);
        for (Modifier m : mods)
        {
            if (m.modifyClassVisibility)
            {
                classNode.access = getFixedAccess(classNode.access, m);
                if (DEBUG)
                {
                    System.out.println(String.format("Class: %s %s -> %s", name, toBinary(m.oldAccess), toBinary(m.newAccess)));
                }
                continue;
            }
            if (m.desc.isEmpty())
            {
                for (FieldNode n : (List<FieldNode>) classNode.fields)
                {
                    if (n.name.equals(m.name) || m.name.equals("*"))
                    {
                        n.access = getFixedAccess(n.access, m);
                        if (DEBUG)
                        {
                            System.out.println(String.format("Field: %s.%s %s -> %s", name, n.name, toBinary(m.oldAccess), toBinary(m.newAccess)));
                        }

                        if (!m.name.equals("*"))
                        {
                            break;
                        }
                    }
                }
            }
            else
            {
                for (MethodNode n : (List<MethodNode>) classNode.methods)
                {
                    if ((n.name.equals(m.name) && n.desc.equals(m.desc)) || m.name.equals("*"))
                    {
                        n.access = getFixedAccess(n.access, m);
                        if (DEBUG)
                        {
                            System.out.println(String.format("Method: %s.%s%s %s -> %s", name, n.name, n.desc, toBinary(m.oldAccess), toBinary(m.newAccess)));
                        }

                        if (!m.name.equals("*"))
                        {
                            break;
                        }
                    }
                }
            }
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private String toBinary(int num)
    {
        return String.format("%16s", Integer.toBinaryString(num)).replace(' ', '0');
    }

    private int getFixedAccess(int access, Modifier target)
    {
        target.oldAccess = access;
        int t = target.targetAccess;
        int ret = (access & ~7);

        switch (access & 7)
        {
        case ACC_PRIVATE:
            ret |= t;
            break;
        case 0: // default
            ret |= (t != ACC_PRIVATE ? t : 0 /* default */);
            break;
        case ACC_PROTECTED:
            ret |= (t != ACC_PRIVATE && t != 0 /* default */? t : ACC_PROTECTED);
            break;
        case ACC_PUBLIC:
            ret |= (t != ACC_PRIVATE && t != 0 /* default */&& t != ACC_PROTECTED ? t : ACC_PUBLIC);
            break;
        default:
            throw new RuntimeException("Illegal access: " + (access & 7));
        }

        // Clear the "final" marker on fields only if specified in control field
        if (target.changeFinal && target.desc == "")
        {
            if (target.markFinal)
            {
                ret |= ACC_FINAL;
            }
            else
            {
                ret &= ~ACC_FINAL;
            }
        }
        target.newAccess = ret;
        return ret;
    }
}