package de.dakror.modding.agent.boot;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.function.Function;
import java.util.jar.Manifest;

import jdk.internal.loader.Resource;

public abstract class UcpResource {
    abstract public String getName();
    abstract public URL getURL();
    abstract public URL getCodeSourceURL();
    abstract public InputStream getInputStream() throws IOException;
    abstract public int getContentLength() throws IOException;
    abstract public ByteBuffer getByteBuffer() throws IOException;
    abstract public byte[] getBytes() throws IOException;
    abstract public Certificate[] getCertificates();
    abstract public CodeSigner[] getCodeSigners();
    abstract public Manifest getManifest() throws IOException;
    public static interface Metadata {
        Certificate[] getCertificates();
        CodeSigner[] getCodeSigners();
        Manifest getManifest() throws IOException;
        public static final Metadata EMPTY = new Metadata() {
            @Override public Certificate[] getCertificates() {
                return null;
            }
            @Override public CodeSigner[] getCodeSigners() {
                return null;
            }
            @Override public Manifest getManifest() throws IOException {
                return null;
            }
        };
        public static Metadata of(Certificate[] certificates, CodeSigner[] codeSigners, Manifest manifest) {
            return new Metadata() {
                @Override public Certificate[] getCertificates() {
                    return certificates;
                }
                @Override public CodeSigner[] getCodeSigners() {
                    return codeSigners;
                }
                @Override public Manifest getManifest() throws IOException {
                    return manifest;
                }
            };
        }
        public static Metadata of(UcpResource origResource) {
            return new Metadata() {
                @Override public Certificate[] getCertificates() {
                    return origResource.getCertificates();
                }
                @Override public CodeSigner[] getCodeSigners() {
                    return origResource.getCodeSigners();
                }
                @Override public Manifest getManifest() throws IOException {
                    return origResource.getManifest();
                }
            };
        }
        public static Metadata of(CodeSource codeSource) {
            return new Metadata() {
                @Override public Certificate[] getCertificates() {
                    return codeSource.getCertificates();
                }
                @Override public CodeSigner[] getCodeSigners() {
                    return codeSource.getCodeSigners();
                }
                @Override public Manifest getManifest() throws IOException {
                    var connection = urlFor(codeSource).openConnection();
                    if (connection instanceof JarURLConnection) {
                        return ((JarURLConnection)connection).getManifest();
                    }
                    return null;
                }
            };
        }
    }

    static final class Proxy extends UcpResource {
        private final Resource target;

        static UcpResource of(Resource target) {
            return target instanceof ReverseProxy ? ((ReverseProxy)target).target : new Proxy(target);
        }
        public Proxy(Resource target) {
            this.target = target;
        }
        @Override
        public String getName() {
            return target.getName();
        }
        @Override
        public URL getURL() {
            return target.getURL();
        }
        @Override
        public URL getCodeSourceURL() {
            return target.getCodeSourceURL();
        }
        @Override
        public InputStream getInputStream() throws IOException {
            return target.getInputStream();
        }
        @Override
        public int getContentLength() throws IOException {
            return target.getContentLength();
        }
        @Override
        public ByteBuffer getByteBuffer() throws IOException {
            return target.getByteBuffer();
        }
        @Override
        public byte[] getBytes() throws IOException {
            return target.getBytes();
        }
        @Override
        public Certificate[] getCertificates() {
            return target.getCertificates();
        }
        @Override
        public CodeSigner[] getCodeSigners() {
            return target.getCodeSigners();
        }
        @Override
        public Manifest getManifest() throws IOException {
            return target.getManifest();
        }
    }

    static final class ReverseProxy extends Resource {
        private final UcpResource target;

        static Resource of(UcpResource target) {
            return target instanceof Proxy ? ((Proxy)target).target : new ReverseProxy(target);
        }

        public ReverseProxy(UcpResource target) {
            this.target = target;
        }

        @Override
        public String getName() {
            return target.getName();
        }
        @Override
        public URL getURL() {
            return target.getURL();
        }
        @Override
        public URL getCodeSourceURL() {
            return target.getCodeSourceURL();
        }
        @Override
        public InputStream getInputStream() throws IOException {
            return target.getInputStream();
        }
        @Override
        public int getContentLength() throws IOException {
            return target.getContentLength();
        }
        @Override
        public ByteBuffer getByteBuffer() throws IOException {
            return target.getByteBuffer();
        }
        @Override
        public byte[] getBytes() throws IOException {
            return target.getBytes();
        }
        @Override
        public Certificate[] getCertificates() {
            return target.getCertificates();
        }
        @Override
        public CodeSigner[] getCodeSigners() {
            return target.getCodeSigners();
        }
        @Override
        public Manifest getManifest() throws IOException {
            return target.getManifest();
        }

    }

    @FunctionalInterface
    public static interface UrlFunction<R> {
        R apply(URL url) throws IOException;
    }
    @FunctionalInterface
    public static interface UrlSupplier<R> {
        R get() throws IOException;
    }
    static class UrlProxy extends UcpResource {
        private final String name;
        private final URL url;
        private final UrlFunction<InputStream> getInputStream;
        private final UrlFunction<Integer> getContentLength;
        private URL codeSourceURL = null;
        private int contentLength = -1;
        private final UcpResource.Metadata metadata;
        private final byte[] data;


        public UrlProxy(String name, URL url) {
            this(name, url, null);
        }
        public UrlProxy(String name, URL url, URL codeSourceURL) {
            this(name, url, codeSourceURL, URL::openStream);
        }
        public UrlProxy(String name, URL url, URL codeSourceURL, UrlFunction<InputStream> getInputStream) {
            this(name, url, codeSourceURL, getInputStream, null);
        }

        public UrlProxy(String name, URL url, URL codeSourceURL, UrlFunction<InputStream> getInputStream, UrlFunction<Integer> getContentLength) {
            this(name, url, codeSourceURL, getInputStream, null, null);
        }
        public UrlProxy(String name, URL url, URL codeSourceURL, UrlSupplier<InputStream> getInputStream, UrlSupplier<Integer> getContentLength, UcpResource.Metadata metadata) {
            this(name, url, codeSourceURL, x -> getInputStream.get(), x -> getContentLength.get(), metadata);
        }
        public UrlProxy(String name, URL url, URL codeSourceURL, UrlFunction<InputStream> getInputStream, UrlFunction<Integer> getContentLength, UcpResource.Metadata metadata) {
            this.name = name;
            this.url = url;
            this.codeSourceURL = codeSourceURL;
            this.getInputStream = getInputStream != null ? getInputStream : URL::openStream;
            this.getContentLength = getContentLength != null ? getContentLength : u -> u.openConnection().getContentLength();
            this.metadata = metadata != null ? metadata : Metadata.EMPTY;
            this.data = null;
        }
        public UrlProxy(String name, URL url, URL codeSourceURL, byte[] data) {
            this(name, url, codeSourceURL, data, null);
        }
        public UrlProxy(String name, URL url, URL codeSourceURL, byte[] data, UcpResource.Metadata metadata) {
            this.name = name;
            this.url = url;
            this.codeSourceURL = codeSourceURL;
            this.getInputStream = null;
            this.getContentLength = null;
            this.metadata = metadata != null ? metadata : Metadata.EMPTY;
            this.data = data;
        }

        @Override
        public String getName() {
            return name;
        }
        @Override
        public URL getURL() {
            return url;
        }
        @Override
        public URL getCodeSourceURL() {
            if (codeSourceURL == null) {

            }
            return codeSourceURL;
        }
        @Override
        public InputStream getInputStream() throws IOException {
            if (data != null) {
                return new ByteArrayInputStream(data);
            }
            return getInputStream.apply(url);
        }
        @Override
        public int getContentLength() throws IOException {
            if (data != null) {
                return data.length;
            }
            if (contentLength == -1) {
                contentLength = getContentLength.apply(url);
            }
            return contentLength;
        }
        @Override
        public ByteBuffer getByteBuffer() throws IOException {
            return ByteBuffer.wrap(getBytes());
        }
        @Override
        public byte[] getBytes() throws IOException {
            if (data != null) {
                return data.clone();
            }
            return getInputStream().readAllBytes();
        }
        @Override
        public Certificate[] getCertificates() {
            return metadata.getCertificates();
        }
        @Override
        public CodeSigner[] getCodeSigners() {
            return metadata.getCodeSigners();
        }
        @Override
        public Manifest getManifest() throws IOException {
            return metadata.getManifest();
        }
    }

    public static UcpResource of(String name, URL url) {
        return new UrlProxy(name, url);
    }
    public static UcpResource of(String name, URL url, URL codeSourceURL) {
        return new UrlProxy(name, url, codeSourceURL);
    }
    public static UcpResource of(String name, URL url, URL codeSourceURL, UrlFunction<InputStream> getInputStream) {
        return new UrlProxy(name, url, codeSourceURL, getInputStream);
    }
    public static UcpResource of(String name, URL url, URL codeSourceURL, UrlFunction<InputStream> getInputStream, UrlFunction<Integer> getContentLength) {
        return new UrlProxy(name, url, codeSourceURL, getInputStream, getContentLength);
    }
    public static UcpResource of(String name, URL url, URL codeSourceURL, UrlFunction<InputStream> getInputStream, UrlFunction<Integer> getContentLength, UcpResource.Metadata metadata) {
        return new UrlProxy(name, url, codeSourceURL, getInputStream, getContentLength, metadata);
    }
    public static UcpResource of(String name, URL url, URL codeSourceURL, byte[] data) {
        return new UrlProxy(name, url, codeSourceURL, data);
    }
    public static UcpResource of(String name, URL url, URL codeSourceURL, byte[] data, UcpResource.Metadata metadata) {
        return new UrlProxy(name, url, codeSourceURL, data, metadata);
    }
    public static UcpResource of(String name, URL url, CodeSource codeSource, byte[] data) {
        return new UrlProxy(name, url, urlFor(codeSource), data, Metadata.of(codeSource));
    }
    public static UcpResource of(String name, URL url, CodeSource codeSource, byte[] data, UcpResource.Metadata metadata) {
        return new UrlProxy(name, url, urlFor(codeSource), data, metadata);
    }
    public static UcpResource of(String name, CodeSource codeSource, byte[] data) throws MalformedURLException {
        return new UrlProxy(name, urlFor(codeSource, name), urlFor(codeSource), data, Metadata.of(codeSource));
    }
    public static UcpResource of(String name, CodeSource codeSource, byte[] data, UcpResource.Metadata metadata) throws MalformedURLException {
        return new UrlProxy(name, urlFor(codeSource, name), urlFor(codeSource), data, metadata);
    }
    public static UcpResource of(String name, URL url, UcpResource origResource) {
        return new UrlProxy(name, url, origResource.getCodeSourceURL(), origResource::getInputStream, origResource::getContentLength, Metadata.of(origResource));
    }
    static UcpResource ofResource(String name, URL url, Resource origResource) {
        return new UrlProxy(name, url, origResource.getCodeSourceURL());
    }

    private static URL urlFor(CodeSource codeSource) {
        try {
            return urlFor(codeSource, "");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
    private static URL urlFor(CodeSource codeSource, String name) throws MalformedURLException {
        var location = codeSource.getLocation();
        if (location.toString().endsWith(".jar")) {
            return new URL("jar", "", -1, location + "!/" + name);
        }
        
        return new URL(location, name);
    }

    public static Enumeration<UcpResource> enumerationOf(String name, Enumeration<URL> urls) {
        return new EnumerationConverter<>(urls, u -> of(name, u));
    }
    public static Enumeration<UcpResource> enumerationOfResources(String name, Enumeration<URL> urls, Enumeration<Resource> origResources) {
        return new EnumerationConverter<>(urls, u -> origResources.hasMoreElements() ? ofResource(name, u, origResources.nextElement()) : of(name, u));
    }
    public static Enumeration<UcpResource> enumerationOfResources(Enumeration<Resource> resources) {
        return new EnumerationConverter<>(resources, UcpResource.Proxy::new);
    }

    static Enumeration<Resource> asResourceEnumeration(Enumeration<UcpResource> ucpResources) {
        return new EnumerationConverter<UcpResource, Resource>(ucpResources, UcpResource.ReverseProxy::new);
    }

    static class EnumerationConverter<T, U> implements Enumeration<U> {
        private final Enumeration<T> orig;
        private final Function<? super T, ? extends U> convert;
        public EnumerationConverter(Enumeration<T> orig, Function<? super T, ? extends U> convert) {
            this.orig = orig;
            this.convert = convert;
        }
        @Override
        public boolean hasMoreElements() {
            return orig.hasMoreElements();
        }
        @Override
        public U nextElement() {
            return convert.apply(orig.nextElement());
        }
    }

}
