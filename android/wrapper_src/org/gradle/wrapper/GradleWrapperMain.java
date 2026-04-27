package org.gradle.wrapper;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal Gradle wrapper implementation for this repository.
 * Downloads the Gradle distribution defined in gradle/wrapper/gradle-wrapper.properties,
 * caches it under ~/.gradle/wrapper/dists, and then launches org.gradle.launcher.GradleMain.
 */
public final class GradleWrapperMain {

    public static void main(String[] args) throws Exception {
        Path projectDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path propsPath = projectDir.resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.properties");
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(propsPath)) {
            props.load(in);
        }
        String distUrl = props.getProperty("distributionUrl");
        if (distUrl == null || distUrl.isBlank()) {
            System.err.println("distributionUrl missing in " + propsPath);
            System.exit(2);
        }

        Path gradleUserHome = Paths.get(System.getProperty("user.home"), ".gradle");
        Path distsRoot = gradleUserHome.resolve("wrapper").resolve("dists");
        Files.createDirectories(distsRoot);

        String fileName = distUrl.substring(distUrl.lastIndexOf('/') + 1);
        String baseName = fileName.replaceAll("\\.zip$", "");
        // Canonical cache key: baseName + hash of URL
        String urlHash = Integer.toHexString(distUrl.hashCode());
        Path distDir = distsRoot.resolve(baseName).resolve(urlHash);
        Path marker = distDir.resolve(".ok");

        if (!Files.exists(marker)) {
            Files.createDirectories(distDir);
            Path zipPath = distDir.resolve(fileName);
            if (!Files.exists(zipPath)) {
                System.out.println("Downloading Gradle distribution: " + distUrl);
                download(distUrl, zipPath);
            }
            System.out.println("Unpacking: " + zipPath);
            unpack(zipPath, distDir);
            Files.writeString(marker, "ok");
        }

        // Find gradle home directory inside distDir
        Path gradleHome = findGradleHome(distDir);
        if (gradleHome == null) {
            System.err.println("Could not locate Gradle home under " + distDir);
            System.exit(3);
        }

        // Build classpath: gradleHome/lib/gradle-launcher-*.jar plus all jars in lib
        Path libDir = gradleHome.resolve("lib");
        if (!Files.isDirectory(libDir)) {
            System.err.println("Invalid Gradle home (missing lib): " + gradleHome);
            System.exit(4);
        }
        String cp = buildClasspath(libDir);

        // Launch GradleMain in-process
        String mainClass = "org.gradle.launcher.GradleMain";
        ClassLoader cl = new URLClassLoader(toUrls(cp));
        Thread.currentThread().setContextClassLoader(cl);
        Class<?> c = Class.forName(mainClass, true, cl);
        c.getMethod("main", String[].class).invoke(null, (Object) args);
    }

    private static void download(String url, Path out) throws IOException {
        URL u = new URL(url);
        URLConnection conn = u.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        try (InputStream in = conn.getInputStream(); OutputStream os = Files.newOutputStream(out)) {
            in.transferTo(os);
        }
    }

    private static void unpack(Path zip, Path dest) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                Path out = dest.resolve(e.getName()).normalize();
                if (!out.startsWith(dest)) {
                    throw new IOException("Zip slip: " + e.getName());
                }
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(out)) {
                        zis.transferTo(os);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static Path findGradleHome(Path distDir) throws IOException {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(distDir)) {
            for (Path p : ds) {
                if (Files.isDirectory(p) && p.getFileName().toString().startsWith("gradle-")) {
                    return p;
                }
            }
        }
        // Sometimes nested one level deeper
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(distDir)) {
            for (Path p : ds) {
                if (Files.isDirectory(p)) {
                    try (DirectoryStream<Path> ds2 = Files.newDirectoryStream(p)) {
                        for (Path p2 : ds2) {
                            if (Files.isDirectory(p2) && p2.getFileName().toString().startsWith("gradle-")) {
                                return p2;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String buildClasspath(Path libDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(libDir, "*.jar")) {
            for (Path jar : ds) {
                if (sb.length() > 0) sb.append(File.pathSeparator);
                sb.append(jar.toAbsolutePath());
            }
        }
        return sb.toString();
    }

    private static URL[] toUrls(String cp) throws MalformedURLException {
        String[] parts = cp.split(java.util.regex.Pattern.quote(File.pathSeparator));
        URL[] urls = new URL[parts.length];
        for (int i = 0; i < parts.length; i++) {
            urls[i] = Paths.get(parts[i]).toUri().toURL();
        }
        return urls;
    }
}
