package io.github.projectunified.faststats.build;

import net.raphimc.javadowngrader.transformer.j15.RecordReplacer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * Converts records in a jar to final classes so JvmDowngrader does not need
 * the {@code java.lang.Record} runtime stub.
 *
 * <p>Usage: {@code java -jar faststats-record-converter.jar <input.jar>}</p>
 */
public final class RecordConverter {
    private RecordConverter() {
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar record-converter.jar <input.jar>");
            System.exit(1);
        }
        final File inputFile = new File(args[0]);
        if (!inputFile.isFile()) return;

        final File output = new File(inputFile.getParentFile(), inputFile.getName() + ".records");
        try (final JarInputStream in = new JarInputStream(new FileInputStream(inputFile));
             final JarOutputStream out = new JarOutputStream(new FileOutputStream(output), in.getManifest())) {
            JarEntry entry;
            while ((entry = in.getNextJarEntry()) != null) {
                final byte[] bytes = entry.getName().endsWith(".class") ? transform(readAll(in)) : readAll(in);
                final JarEntry copy = new JarEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(bytes);
                out.closeEntry();
            }
        }
        Files.move(output.toPath(), inputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Converted records in " + inputFile.getName());
    }

    private static byte[] transform(final byte[] bytes) {
        final ClassReader reader = new ClassReader(bytes);
        final ClassNode node = new ClassNode();
        reader.accept(node, 0);
        if (!"java/lang/Record".equals(node.superName)) return bytes;

        RecordReplacer.replace(node);
        node.superName = "java/lang/Object";

        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(final String type1, final String type2) {
                return "java/lang/Object";
            }
        };
        node.accept(new ClassRemapper(writer, new SimpleRemapper("java/lang/Record", "java/lang/Object")));
        return writer.toByteArray();
    }

    private static byte[] readAll(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toByteArray();
    }
}
