package net.md_5.specialsource;

import com.google.common.collect.BiMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lookup class inheritance from classes at runtime, remapped through a JarMapping
 */
public class RemappedRuntimeInheritanceProvider extends RuntimeInheritanceProvider {
    private final JarMapping jarMapping;

    private final BiMap<String, String> inversePackageMapping;
    private final BiMap<String, String> inverseClassMapping;

    public RemappedRuntimeInheritanceProvider(JarMapping jarMapping) {
        this.jarMapping = jarMapping;

        inversePackageMapping = jarMapping.packages.inverse();
        inverseClassMapping = jarMapping.classes.inverse();
    }

    @Override
    public List<String> getParents(String before) {
        // Remap the input (example: cb -> obf)
        String after = JarRemapper.mapTypeName(before, jarMapping.packages, jarMapping.classes);

        List<String> beforeParents = super.getParents(after);
        if (beforeParents == null) {
            return null;
        }

        // Un-remap the output (example: obf -> cb)
        List<String> afterParents = new ArrayList<String>();
        for (String beforeParent : beforeParents) {
            afterParents.add(JarRemapper.mapTypeName(beforeParent, inversePackageMapping, inverseClassMapping));
        }

        return afterParents;
    }
}
