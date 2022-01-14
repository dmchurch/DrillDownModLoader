package de.dakror.modding.agent.boot;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.function.Function;

import jdk.internal.loader.Resource;

public abstract class UcpResource extends Resource {
    static class Proxy extends UcpResource {
        private final Resource target;
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
    }

    static class UrlProxy extends UcpResource {
        private final String name;
        private final URL url;
        private URL codeSourceURL = null;
        private int contentLength = -1;

        public UrlProxy(String name, URL url) {
            this(name, url, null);
        }

        public UrlProxy(String name, URL url, URL codeSourceURL) {
            this.name = name;
            this.url = url;
            this.codeSourceURL = codeSourceURL;
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
            return url.openStream();
        }
        @Override
        public int getContentLength() throws IOException {
            if (contentLength == -1) {}
            return contentLength;
        }
    }

    public static UcpResource of(String name, URL url) {
        return new UrlProxy(name, url);
    }
    public static UcpResource of(String name, URL url, URL codeSourceURL) {
        return new UrlProxy(name, url, codeSourceURL);
    }
    public static UcpResource of(String name, URL url, Resource origResource) {
        return new UrlProxy(name, url, origResource.getCodeSourceURL());
    }

    public static Enumeration<UcpResource> enumerationOf(String name, Enumeration<URL> urls) {
        return new EnumerationConverter<>(urls, u -> of(name, u));
    }
    public static Enumeration<UcpResource> enumerationOf(String name, Enumeration<URL> urls, Enumeration<Resource> origResources) {
        return new EnumerationConverter<>(urls, u -> origResources.hasMoreElements() ? of(name, u, origResources.nextElement()) : of(name, u));
    }
    public static Enumeration<UcpResource> enumerationOf(Enumeration<Resource> resources) {
        return new EnumerationConverter<>(resources, UcpResource.Proxy::new);
    }

    static Enumeration<Resource> asResourceEnumeration(Enumeration<UcpResource> ucpResources) {
        return new EnumerationConverter<>(ucpResources, Function.identity());
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
